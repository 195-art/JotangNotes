# RabbitMQ 笔记消息顺序性与幂等性说明

本文对应入门篇 Part 3 的顺序性加分项，包含当前实现、使用流程、升级步骤和测试记录。

## 1. 顺序性的含义与范围

同一篇笔记的修改消息先进入队列，删除消息后进入队列，就应该先完成修改，再执行删除。仅有 FIFO 队列不够：多个消费者可能同时领取消息，先领取的修改耗时更长，后领取的删除却先提交。

RabbitMQ 的顺序以队列实际入队顺序为基础。同一发布 channel 的消息保留发布顺序，不同 channel 的并发消息没有共同的发送先后定义。本项目保证正常消费与受控重试中的队列处理顺序，不把多个并发 HTTP 请求的点击时间当作全局顺序。[RabbitMQ 队列顺序说明](https://www.rabbitmq.com/docs/queues)

## 2. 当前实现

| 环节 | 核心代码 | 作用 |
|---|---|---|
| 消息投递 | `NoteController` 使用 `RabbitConfig.NOTE_ROUTING_KEY` | 所有笔记写操作进入同一保序队列 |
| 队列声明 | `RabbitConfig.noteQueue()` | 持久化 classic 队列 `note.ordered.queue`，声明 `x-single-active-consumer=true` |
| 消费配置 | `RabbitConfig.orderedNoteListenerFactory()` | 固定 concurrency=1、maxConcurrency=1、prefetch=1、batchSize=1、AUTO 确认 |
| 监听入口 | `NoteConsumer.handleMessage()` 的 `@RabbitListener` | 显式使用专门的保序消费工厂，同步处理消息 |
| 本地失败重试 | Spring Boot 的 retry 配置 | 默认同一消息重试 3 次，间隔递增 |
| 重试耗尽 | `RabbitConfig.noteMessageRecoverer()` | 抛出 ImmediateRequeueAmqpException，保留原消息继续重试，不跳过到后面的操作 |
| 其他消费错误 | `OrderedNoteErrorHandler` | 转换错误等也重入队，等待一秒避免忙循环 |
| 重复消息 | `processed_note_messages` 唯一主键 | 消息标识与笔记操作同事务提交，重复投递不重复修改数据库 |

Single Active Consumer 由 broker 选出唯一活跃消费者，其他应用实例的消费者等待接管；它不同于每个实例各自只启动一个消费者。[RabbitMQ 单活跃消费者说明](https://www.rabbitmq.com/docs/consumers#single-active-consumer)

prefetch=1 使一个活跃消费者最多持有一条未确认消息。同步监听方法成功返回后才确认这条消息，失败会重新处理。不能改成手动提前确认，也不能在监听方法内另起异步任务后立即返回。[Spring AMQP 消费与 prefetch 说明](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-consumer.html)

本项目选择全队列串行，其他笔记也会排队，适合当前小规模练习。未来提高并发时可以按笔记 ID 分片，每个分片保持单活跃消费者；不能直接增加本队列活跃消费者数量。

## 3. 顺序性为什么要与幂等性一起使用

修改已经提交，但 Redis 失效失败时，消息会重试。数据库中的消息标记让重试跳过已提交的修改，仍然执行缓存失效；缓存失效成功后才能确认消息、继续处理删除。

如果进程在事务提交前失败，笔记操作和消息标记一起回滚。重投可以继续执行。如果在提交后、确认前失败，重投只跳过数据库写入，不重复新增或推进修改版本。

## 4. 使用流程

1. 启动 MySQL、Redis、RabbitMQ 和应用，确认新队列 `note.ordered.queue` 已声明。
2. 注册并登录，新增笔记；接口返回“请求已受理”，等待笔记出现，取得实际笔记 ID 后再操作它。
3. 对同一笔记提交修改 A、修改 B，再删除。顺序指实际入队先后；需要固定调用顺序时，应等待前一个请求完成投递后再发下一个。
4. 正常处理时两次修改依次完成，最终笔记被删除；重复旧消息不会重新创建笔记。
5. 启动第二个应用实例时，broker 只给其中一个消费者发送消息。活跃消费者停止后，备用消费者可以接管。

## 5. 失败处理与边界

失败消息自动移入死信队列会让后续操作越过它，所以新队列不采用这一策略。当前消息持续失败时，整个笔记写队列会阻塞。需要查看日志，修复数据库、Redis 或消息兼容性问题，恢复后继续重试。

格式错误或永久无效的消息同样会阻塞。应先暂停消费、备份消息并查明原因；人工跳过消息是显式放弃该操作，需要同时检查其后续操作。不能一边继续消费后续消息，一边把失败消息放到队尾重放并声称保持了原顺序。

不要给保序队列配置消息 TTL、优先级、丢弃式队列长度限制，或手工将失败消息挪到队尾；这些会改变消息集合或顺序。新增队列声明失败时应用应暴露错误，不忽略参数冲突。

网络断开后的消费者切换不能视为数据库操作的全局互斥锁：原消费者可能尚有正在执行的事务。本方案没有实现跨实例数据库序号栅栏、业务操作序号、事务 outbox 或端到端恰好一次。对于必须覆盖网络分区及多发布者因果顺序的生产场景，应再引入笔记级操作序号、数据库条件写入和持久化补偿机制。当前实现与测试覆盖正常队列消费、失败重试及正常停机接管。

## 6. 旧版本升级

`x-single-active-consumer` 不能给已经存在的队列随意追加。因此使用新队列 `note.ordered.queue` 和新 routing key `note.ordered.operation`，不删除旧 `note.queue`。

升级前暂停写请求，让旧版本处理完 `note.queue` 的 Ready 与 Unacked 消息，并检查 `note.queue.dlq`。遗留失败操作需要确认是否已被后续操作覆盖，不能盲目重放。确认旧消息已处理或完成业务核对后，停止所有旧实例，再启动新版本。不能混跑新旧版本，否则两套队列同时处理会失去统一顺序。若无法排空旧队列，应保留旧版本修复并完成处理，而不是直接切换后遗忘积压。

已升级过数据库的项目不需要为了本次顺序性变更再修改表结构。首次部署直接使用 `schema.sql`。

## 7. 测试过程与结果（2026-10-07）

执行后端回归：

```powershell
.\mvnw.cmd test -B -ntp
```

结果：22 项，20 项通过，2 项可选集成测试跳过，失败 0、错误 0。`NoteOrderingTests` 覆盖保序工厂覆盖不安全的并发/预取/确认配置、重试耗尽继续重入队、格式错误不丢弃。`NoteConsumerTests` 使用 H2 和模拟 Redis 验证两次修改后删除，以及重复消息不重新创建已删除笔记。

执行真实 RabbitMQ 测试：

```powershell
.\mvnw.cmd test -B -ntp "-Dtest=RabbitBrokerTests" "-Drabbit.integration=true"
```

测试连接本机 RabbitMQ，只创建随机前缀 `jotangnote.test.ordered.*` 的队列并在结束后清理，不消费业务队列。

| 验证场景 | 测试步骤 | 实际结果 |
|---|---|---|
| 双消费者与失败重试 | 注册两个消费者，按同一 channel 发布 UPDATE、DELETE；让 UPDATE 连续失败 | UPDATE 重投时，DELETE 尚未被领取或完成 |
| 恢复后顺序执行 | 允许 UPDATE 成功，等待两条消息处理结束 | 完成顺序为 UPDATE、DELETE |
| 正常停机接管 | 停止先注册的活跃消费者，再发送 AFTER_FAILOVER | 备用消费者处理成功，最终顺序为 UPDATE、DELETE、AFTER_FAILOVER |

真实 broker 测试结果：1 项通过，失败 0、错误 0。该测试使用实际保序工厂和错误处理器，监听函数模拟修改失败，没有连接真实 MySQL/Redis 执行业务；数据库事务和缓存行为由上述回归测试分别覆盖。

## 8. 提交截图说明

本次已记录真实测试过程与结果，尚未采集界面截图。按招新统一要求，后续功能使用文档仍需附真实操作截图：RabbitMQ 管理页面中的队列 SAC 参数与消费者状态、修改/删除的应用界面、测试通过的终端输出。截图必须来自实际运行，不能把方案图当作操作证据。

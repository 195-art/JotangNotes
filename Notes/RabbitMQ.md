# 我的RabbitMQ学习笔记

参考教程：[RabbitMQ 官方教程](https://www.rabbitmq.com/tutorials/tutorial-one-java)和[RabbitMQ 入门教程](https://javabetter.cn/mq/rabbitmq-rumen.html)。

练习代码：[rabbit-hello](https://github.com/195-art/rabbit-hello.git)。

## 什么是RabbitMQ
RabbitMQ 是一款开源的消息队列系统，让不同程序通过消息通信，常用于异步处理、系统解耦和流量削峰。

- 异步处理：把发邮件、生成报表等任务交给后台执行，减少等待
- 系统解耦：生产者只负责发消息，不需要直接调用每个消费者
- 流量削峰：先把大量请求放进队列，由消费者按处理能力逐步消费4

## RabbitMQ 安装
Windows 本地安装时，先安装与 RabbitMQ 版本兼容的 Erlang，再安装 RabbitMQ。

- Erlang：[下载地址](https://erlang.org/download/otp_versions_tree.html)
- RabbitMQ：[安装说明](https://www.rabbitmq.com/docs/install-windows)
- 客户端默认连接端口：`5672`
- 启用管理插件后，管理页面默认端口：`15672`

## RabbitMQ 核心概念
- 生产者（Producer）：发送消息的程序
- 消费者（Consumer）：接收并处理消息的程序
- Broker：接收、路由和保存消息的 RabbitMQ 服务
- 交换机（Exchange）：根据路由规则，把消息转发给队列
- 队列（Queue）：保存等待消费的消息
- 绑定（Binding）：交换机和队列之间的关联及匹配规则
- 路由键（Routing Key）：发消息时附带的标识，用于匹配目标队列
- 虚拟主机（Virtual Host）：隔离不同业务的交换机、队列和权限

## 在 Java 中使用 RabbitMQ
### 添加依赖
原练习项目使用下面的客户端版本：

```xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>5.16.0</version>
</dependency>
```

### 连接基础配置
```java
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("localhost");
factory.setPort(5672);
factory.setUsername("guest");
factory.setPassword("guest");
factory.setVirtualHost("/");
Connection connection = factory.newConnection();
Channel channel = connection.createChannel();
```

- Connection 是 TCP 连接，一个连接可以创建多个 Channel，减少连接开销
- Channel 用于声明队列、发送消息和接收消息
- 消费者运行期间需要保持连接，不能注册监听后就关闭连接

### 常用方法
- 声明队列：`channel.queueDeclare(queue, true, false, false, null)`
- 声明交换机：`channel.exchangeDeclare(exchange, "direct", true)`
- 绑定队列：`channel.queueBind(queue, exchange, bindingKey)`
- 发送消息：`channel.basicPublish(exchange, routingKey, properties, body)`
- 监听消息：`channel.basicConsume(queue, false, callback, cancelCallback)`

`queueDeclare` 中的三个布尔参数分别是持久化、排他、自动删除；`basicConsume` 中的 `false` 表示关闭自动确认。

## RabbitMQ 消息模式
### 基础消息模式（Hello World）
一个生产者把消息发到队列，一个消费者接收，适合简单的点对点通信。

- 使用默认交换机，交换机名称是空字符串 `""`
- 路由键填写队列名，默认交换机会把消息路由到该队列

### 工作队列模式（Work Queues）
多个消费者共同处理一个队列中的任务，适合耗时任务的分摊处理。

- 正常情况下，一次投递只交给其中一个消费者，不是每人都收到一份
- 手动确认配合 `channel.basicQos(1)`，限制每个消费者最多保留一条未确认消息

### 发布订阅模式（Publish/Subscribe）
使用 fanout 交换机，把消息发给所有绑定的队列，适合广播和通知。

- 每个订阅方需要自己的队列，才能各自收到消息
- fanout 忽略路由键，只看队列是否绑定

### 路由模式（Routing）
使用 direct 交换机，路由键与绑定键完全一致时，消息才会投递到对应队列。

- 例如 `error` 日志发到错误日志队列，`info` 日志发到普通日志队列
- 一个队列可以绑定多个键，多个队列也可以绑定同一个键

### 主题模式（Topics）
使用 topic 交换机，通过通配符匹配路由键，适合按业务分类订阅。

- 路由键按点号分段，例如 `order.created`
- `*`：匹配恰好一个单词，例如 `order.*` 匹配 `order.created`
- `#`：匹配零个或多个单词，例如 `order.#` 匹配 `order`、`order.created`、`order.pay.success`

### RPC 模式（远程过程调用）
客户端发出请求，服务端处理后把结果发回，实现请求和响应。

- `replyTo`：指定回复队列
- `correlationId`：标识请求，用来匹配对应的响应
- 等待结果需要设置超时，避免服务端异常后一直等待

## 消息怎么流转
生产者先把消息发到交换机，交换机根据绑定规则转发给队列，再由消费者处理。

> 生产者 → 交换机 → 队列 → 消费者

- 交换机负责路由，队列负责保存等待消费的消息
- 即使发送时交换机名称为空，也是在使用默认交换机

## 消息确认原理
发送方和消费方分别确认自己负责的环节，两种确认不能互相替代。

- Publisher Confirm：让生产者知道 Broker 对本次发布的确认结果，不代表消费者处理成功
- Consumer Ack：消费者在业务处理成功后确认，队列才可以删除这次消息
- 手动确认下，连接断开时还没有确认的消息通常会重新入队，因此可能重复消费

## 持久化原理
队列持久化保存队列信息，消息持久化用于让消息在重启后恢复，两者需要配合。

- 队列声明时设置 `durable=true`
- 发送时可以用 `MessageProperties.PERSISTENT_TEXT_PLAIN` 标记消息持久化
- 还需要生产者确认来判断发布结果，不能只靠持久化标记就认为绝不会丢消息

## 预取数量怎么起作用
预取数量限制消费者还没确认的消息数，达到上限后暂停继续投递，确认后再补充。

- `basicQos(1)` 适合体验任务分摊，但不一定能获得最高吞吐量
- 预取太大可能让消费者积压任务，太小可能增加等待，需要结合处理速度调整

## 如何减少消息丢失
从发送、保存和消费三个环节分别处理。

- 发送端：开启 Confirm；还要结合 `mandatory=true` 和 Return 回调发现无法路由到队列的消息
- 保存端：持久化队列和消息；需要副本容错时，可以考虑仲裁队列（Quorum Queue）
- 消费端：业务成功后再手动确认，避免失败的任务被提前删除

## 如何处理重复消费
网络异常或确认丢失时，同一条消息可能再次投递，业务处理需要支持幂等。

- 幂等就是同一条业务重复执行，结果仍和执行一次一致
- 可以通过业务唯一编号、数据库唯一约束或状态判断，避免重复扣款、重复创建订单
- 去重记录和业务修改尽量放在同一个数据库事务中，避免只完成其中一步

## 消费失败与死信队列
消息处理失败后，要区分暂时失败和无法处理，不能一直立即重新入队。

- 暂时失败可以延迟重试，并限制次数
- 拒绝消息且不重新入队时，配置了死信交换机才会转交它继续路由；未配置则会丢弃
- 超过重试次数后，可以交给异常队列或人工处理，并记录失败原因

## 延迟处理
可以用消息过期时间（TTL）配合死信交换机，把到期消息转到处理队列，适合订单超时检查。

- 到期后仍要检查订单当前状态，已支付的订单不能关闭
- TTL 加死信不保证精确定时，同一队列混放不同过期时间的消息可能出现等待

## 消息积压
消息进入队列的速度长期高于消费速度，就会形成积压。

- 先检查消费者是否正常、处理是否变慢、数据库是否成为瓶颈
- 再按实际处理能力增加消费者、调整预取数量，必要时限制生产速度
- `Ready` 是等待投递的消息，`Unacked` 是已经投递但尚未确认的消息

## 消息顺序
队列中的先后顺序，不代表多个消费者完成业务的顺序一定相同。

- 同一订单的消息需要有序时，可以路由到同一个队列，由一个消费者串行处理
- 生产端也要按业务顺序发送，并对重试和重新投递造成的乱序做处理

## 数据库与消息一致性
数据库更新成功，不代表消息一定发送成功；消息发出去了，也不代表数据库一定提交成功。

- 可以在同一个数据库事务里保存业务数据和待发送消息，再由后台任务发送、重试
- 这种方式常叫本地消息表，消费端仍然需要处理重复消息

## 常见注意事项
- 重复声明同名队列或交换机时，属性需要一致，否则可能报错并关闭通道
- 只有业务成功后才 Ack，不要放在无论成功失败都会执行的 `finally` 中
- Ack 使用当前消息的 delivery tag，并在接收这条消息的同一个 Channel 上执行
- 临时广播队列可以用 `channel.queueDeclare().getQueue()` 创建，它会生成非持久、排他、自动删除的队列
- RabbitMQ 4.3.0 起，非持久、非排他的经典队列默认不允许声明；不代表所有队列都必须持久化

## 启蒙篇问题回答
### 1. 除了耗时任务，还有什么情况会引入MQ
- 系统解耦：笔记发布后发送事件，通知、搜索索引等模块分别处理，不必直接互相调用
- 流量削峰：请求先进入队列，消费者按实际能力逐步处理突发任务
- 事件广播：同一事件交给不同队列，例如同时更新搜索索引和统计信息
- 失败重试：下游暂时不可用时，保留任务并按策略重试，避免只调用一次就结束

MQ 会增加系统复杂度，也会带来处理延迟，需要考虑丢失、重复、积压和顺序；消费者分摊同一个队列不是广播。

在 JotangNote 中，任务成功提交后可以返回 `202 Accepted` 和任务编号，前端显示“处理中”，确认业务完成后再显示“修改成功”。

### 2. 怎么通过Java发送和接收消息
使用 `com.rabbitmq:amqp-client`。先建立连接和 Channel，再声明队列；生产者发布，消费者注册回调接收。

共用的连接和队列配置：

```java
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("localhost");
Connection connection = factory.newConnection();
Channel channel = connection.createChannel();
channel.queueDeclare("note_tasks", true, false, false, null);
```

生产者发送消息：

```java
channel.confirmSelect();
String message = "生成笔记1001的摘要";
channel.basicPublish("", "note_tasks",
    MessageProperties.PERSISTENT_TEXT_PLAIN,
    message.getBytes(StandardCharsets.UTF_8));
channel.waitForConfirmsOrDie(5000);
```

消费者接收消息：

```java
channel.basicQos(1);
DeliverCallback callback = (consumerTag, delivery) -> {
    String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
    System.out.println(message); // 示例只打印；项目中执行实际任务
    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
};
channel.basicConsume("note_tasks", false, callback, consumerTag -> {});
```

- 片段需要导入 `com.rabbitmq.client.*` 和 `java.nio.charset.StandardCharsets`，并处理连接、发送相关异常
- 消费者运行期间保持连接；业务成功后再 Ack，失败时按重试或死信策略处理
- 发布确认不等于消费者已处理，无法路由的消息还要用 `mandatory` 和 Return 回调检查
- 为每个实际任务设置唯一编号，避免重复消息导致重复修改

参考资料：[Java Hello World](https://www.rabbitmq.com/tutorials/tutorial-one-java)、[确认机制](https://www.rabbitmq.com/docs/confirms)。

## 学习过程记录
- 原笔记记录了六种消息模式，并保留了跟教程编写的 [rabbit-hello代码仓库](https://github.com/195-art/rabbit-hello.git)
- 本次补充了项目引入 MQ 的原因，以及发布确认、手动消费确认和异步任务状态
- 待实践：发送消息，再验证确认与重新投递；本次没有启动 RabbitMQ 或运行代码

## 补充参考
- 确认与可靠投递：[Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- 队列属性与顺序：[Queues](https://www.rabbitmq.com/docs/queues)
- 失败消息转发：[Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx)

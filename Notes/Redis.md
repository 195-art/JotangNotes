# 我的Redis学习笔记

参考教程：[Redis 教程 | 菜鸟教程](https://www.runoob.com/redis/redis-tutorial.html)。

## 什么是Redis
Redis 是一个主要基于内存的键值数据库，支持多种数据结构，常用于缓存、计数、排行榜和消息处理。

- 读写速度快，支持把数据持久化到磁盘
- 支持主从复制、故障切换和集群分片
- key 是字符串，value 可以是字符串、哈希、列表、集合等类型

## Redis 为什么快
数据主要存在内存中，配合合适的数据结构，减少查找和读写开销。

- 常见命令主要串行执行，减少锁竞争，但耗时命令也可能阻塞其他请求
- 整个 Redis 进程并不是只有一个线程，后台任务和部分 I/O 可以由其他线程处理
- 实际性能取决于数据量、命令、机器和网络，没有固定的每秒读写次数

## Redis 启动与连接
下面以 Linux 环境为例，配置文件路径按实际安装位置调整。

- 启动服务：`redis-server /etc/redis.conf`
- 本地连接：`redis-cli`
- 远程连接：`redis-cli -h host -p port`
- 测试连接：`PING`，返回 `PONG` 表示正常
- 密码认证：`AUTH password`；使用 ACL 用户时是 `AUTH username password`
- 切换数据库：`SELECT index`，Redis Cluster 只支持数据库 0
- 关闭服务：`SHUTDOWN`

默认端口是 `6379`；旧 Windows 移植版的启动命令不适用于所有安装方式。

## Redis 键（key）
键命令用于检查、删除、改名和设置过期时间。

- 检查存在：`EXISTS key`
- 删除键：`DEL key`
- 修改名称：`RENAME key newkey`
- 查看类型：`TYPE key`
- 设置过期时间：`EXPIRE key seconds`
- 查看剩余时间：`TTL key`，`-1` 表示没有过期时间，`-2` 表示键不存在
- 分批查找：`SCAN cursor MATCH pattern COUNT count`，从游标 0 开始，把返回的游标用于下一次调用，直到返回 0

`KEYS pattern` 会遍历整个键空间，大量数据时容易阻塞；`SCAN` 可能返回重复键，`COUNT` 也不是严格的返回数量。

## 常用数据类型
### String 字符串
可以存文本、数字和二进制数据，适合普通缓存和计数器。

- 设置与获取：`SET key value`、`GET key`
- 自增：`INCR key`
- 设置并指定过期时间：`SET key value EX seconds`

### Hash 哈希
保存字段和值的映射，适合用户信息这类对象，可以单独修改某个字段。

- 设置字段：`HSET key field value`
- 查询字段：`HGET key field`；查询全部：`HGETALL key`
- 删除字段：`HDEL key field`

### List 列表
元素有序、可重复，支持两端插入和弹出，适合简单队列或栈。

- 插入：`LPUSH key value`、`RPUSH key value`
- 弹出：`LPOP key`、`RPOP key`
- 范围查询：`LRANGE key start stop`

右侧插入、左侧弹出是先进先出；简单弹出会删除元素，可靠任务处理还需要确认和重试机制。

### Set 集合
元素不重复，适合去重、共同关注和标签。

- 添加与删除：`SADD key member`、`SREM key member`
- 查询全部：`SMEMBERS key`；判断成员：`SISMEMBER key member`
- 交集、并集、差集：`SINTER key1 key2`、`SUNION key1 key2`、`SDIFF key1 key2`

### ZSet 有序集合
成员不重复，每个成员带一个分数，适合排行榜。

- 添加成员：`ZADD key score member`
- 升序查询：`ZRANGE key start stop WITHSCORES`
- 降序查询：`ZREVRANGE key start stop WITHSCORES`
- 查看与增加分数：`ZSCORE key member`、`ZINCRBY key increment member`
- 删除成员：`ZREM key member`

### HyperLogLog
用于估算去重后的数量，适合访问人数这类允许少量误差的统计，内存开销小。

- 添加元素：`PFADD key element`
- 估算数量：`PFCOUNT key`
- 合并统计：`PFMERGE destkey sourcekey1 sourcekey2`
- 不能返回原始元素，也不能代替精确去重

## 原子性、事务与脚本
### 原子性
单条命令执行时，不会被其他客户端的命令插入，多条命令分开发送则没有整体原子性。

- 自增优先用 `INCR`，先 `GET` 再 `SET` 可能覆盖其他客户端的修改
- 原子执行不代表出错后会自动撤销之前的修改

### 事务
`MULTI` 开启事务后，命令先进入队列，收到 `EXEC` 后按顺序执行，中间不插入其他客户端的命令。

- `DISCARD`：取消尚未执行的事务
- `WATCH key`：监视键，提交前键发生变化时，事务不执行，需要自行决定是否重试
- 执行阶段某条命令报错，其他命令仍会继续，已经执行的修改不会回滚

### Lua 脚本
把判断和修改等多个操作放进一个脚本，执行期间不插入其他客户端的命令，也能减少网络往返。

- 执行脚本：`EVAL script numkeys key [key ...] arg [arg ...]`
- 脚本应尽量简短，执行时间太长会影响其他请求，出错也不会自动回滚

## Redis 管道技术
Pipeline 可以连续发送多条命令，再读取回复，减少一条命令等待一次结果的网络开销。

- 适合批量读写，但不保证整批命令的原子性
- 批量太大会占用较多内存，需要合理分批

## Redis 持久化
### RDB
把某个时间点的数据保存为快照，恢复时加载快照，适合备份和恢复。

- 两次快照之间发生故障，可能丢失期间的修改
- `SAVE` 会阻塞服务；`BGSAVE` 在后台生成快照，但仍有内存和创建子进程的开销

### AOF
记录写操作，恢复时重放日志，数据安全程度取决于刷盘策略。

- 常见策略包括每次写入刷盘、每秒刷盘和交给操作系统决定
- 写入成功的回复不一定代表数据已安全落盘
- `BGREWRITEAOF` 根据当前数据重写日志，减少冗余记录

RDB 和 AOF 可以配合使用，但持久化不能代替独立备份。

## 过期与内存淘汰
过期解决数据到时间后失效，淘汰解决内存达到上限后如何腾出空间。

- 过期清理：访问键时检查，同时定期检查部分键，不一定到点就马上释放内存
- `maxmemory`：设置数据内存上限；`maxmemory-policy`：选择淘汰策略
- LRU 优先淘汰最近少用的数据，LFU 优先淘汰使用频率低的数据
- `noeviction` 不主动淘汰，内存不足时相关写入会报错

## 主从复制
主节点把数据变化发送给从节点，用于数据副本和分担读请求。

- 通常是异步复制，从节点可能读到旧数据，故障切换时也可能丢失尚未复制的写入
- 首次同步可能需要全量数据，后续尽量同步增量变化
- 配置从节点：`REPLICAOF host port`；取消复制：`REPLICAOF NO ONE`
- 查看状态：`INFO REPLICATION`

## 哨兵模式
哨兵持续监控主从节点，主节点故障时协作选择新的主节点，完成故障切换。

- 通常部署多个哨兵，客户端需要支持发现新的主节点
- 哨兵负责高可用，不负责把业务数据拆分到多个主节点

## 分区与集群
分区就是把数据分散到多个实例；Redis Cluster 通过哈希槽实现分片，同时支持故障切换。

- 一共有 16384 个槽，键映射到槽，再由负责该槽的节点保存
- 支持的多键操作通常要求相关键位于同一个槽
- `{order:1}:info` 和 `{order:1}:items` 可以用相同的哈希标签放进同一个槽
- 查看集群：`CLUSTER INFO`、`CLUSTER NODES`

## 发布订阅与 Stream
### 发布订阅
生产者向频道发消息，在线订阅者接收，适合实时广播。

- 订阅与取消：`SUBSCRIBE channel`、`UNSUBSCRIBE channel`
- 发布消息：`PUBLISH channel message`
- 不保存供离线订阅者补读的历史消息

### Stream
保存消息流，支持消费者组和确认，适合需要记录消费进度的场景。

- 添加消息：`XADD key * field value`
- 范围读取：`XRANGE key - + COUNT count`
- 创建消费者组：`XGROUP CREATE key group 0 MKSTREAM`
- 组内读取新消息：`XREADGROUP GROUP group consumer COUNT count STREAMS key >`
- 确认完成：`XACK key group id`

同组消费者分摊新消息；未确认的消息需要恢复或转交处理，因此可能重复消费。`XACK` 清除待确认记录，不等于删除流中的消息；重启后的恢复仍取决于持久化。

## Redis 缓存使用
先查 Redis，命中就返回；没命中就查数据库，再把结果写入 Redis，减少数据库压力。

- 缓存设置合适的过期时间，避免旧数据长期保留
- 键名可以按业务组织，例如 `user:1001`、`order:1001`

## 缓存穿透
请求的数据在缓存和数据库中都不存在，每次都会访问数据库。

- 可以缓存空结果，并设置较短的过期时间
- 也可以用布隆过滤器拦截确定不存在的数据，注意及时维护过滤器

## 缓存击穿
某个热点数据过期，大量请求同时访问数据库。

- 可以用互斥控制，让一个请求负责重建缓存
- 其他请求等待，或在业务允许时使用保留的旧数据

## 缓存雪崩
大量缓存同时过期，或 Redis 不可用，导致请求集中访问数据库。

- 给过期时间加随机值，避免集中失效
- 配合高可用、限流和降级，减少故障影响

## 大 Key 与热点 Key
大 Key 是单个键的数据太多，热点 Key 是单个键的访问太频繁。

- 大 Key：拆分数据、分批读取，避免一次获取整个大集合；删除时可考虑 `UNLINK` 异步释放内存
- 热点 Key：使用本地缓存等方式分担请求，同时考虑本地缓存的更新
- 增加集群节点不一定能解决单个热点键的压力

## 缓存与数据库一致性
缓存和数据库是两份数据，更新时可能短暂不一致，数据库通常作为最终数据来源。

- 常见做法是先更新数据库，再删除缓存，让后续查询重新加载
- 删除失败需要考虑重试，过期时间可以限制旧数据的保留时间
- 并发查询仍可能把旧数据写回，要求更高时需要结合版本判断或其他同步方案

## 分布式锁
可以用 `SET lock:key token NX PX milliseconds` 尝试加锁，键不存在时才写入，并同时设置过期时间。

- token 使用本次加锁的唯一标识，避免误删其他请求持有的锁
- 解锁时要原子地比较标识并删除，可以用 Lua，不能分开 `GET` 和 `DEL`
- 锁过期不代表业务执行结束，长任务和故障切换仍需要额外考虑

## 状态查看与性能排查
- 服务状态：`INFO`；内存：`INFO memory`；命中和淘汰等统计：`INFO stats`
- 键数量：`DBSIZE`；连接情况：`CLIENT LIST`
- 慢命令记录：`SLOWLOG GET 10`，其中的执行耗时不包含网络传输
- 压力测试：`redis-benchmark -h host -p port -c connections -n requests`，结果取决于测试条件

响应变慢时，结合慢命令、大 Key、内存、连接和网络情况排查，不要只看每秒请求数。

## 常见注意事项
- 不直接把 Redis 暴露在公网，结合网络访问控制、认证和 ACL 限制权限
- `CONFIG SET` 修改运行配置，重启后是否保留取决于是否写入配置文件等持久配置方式
- 大集合避免直接 `HGETALL`、`SMEMBERS` 或全量范围查询，可以使用分批遍历
- `FLUSHDB` 清空当前库，`FLUSHALL` 清空当前实例的所有库，使用前确认目标

## 启蒙篇问题回答
### 1. Redis为什么快
数据主要存在内存中，配合合适的数据结构，减少磁盘访问和查找开销；常见命令主要串行执行，也减少了锁竞争。

实际速度仍受命令、数据量、网络和机器影响，不能保证每次都比 MySQL 快固定倍数。MySQL 本身也有内存缓存和索引。

### 2. MySQL的一行数据怎么作为缓存保存
可以把一行数据转成 JSON 字符串，或者把各字段保存为 Hash。键名使用业务和编号，例如 `note:1001`。

JSON 字符串适合一次读取整个对象：

```redis
SET note:1001 '{"id":1001,"title":"Redis笔记","content":"这里是正文"}' EX 300
GET note:1001
```

Hash 适合读取或修改单独字段：

```redis
HSET note:1002 id 1002 title "MySQL笔记" content "这里是正文"
EXPIRE note:1002 300
HGETALL note:1002
```

- 未命中时查询 MySQL，再把结果写入 Redis
- 更新时常见做法是先更新数据库，再删除缓存，但仍可能短暂不一致
- 缓存命中不代替权限检查，私有笔记仍要验证访问者身份

### 3. 怎么通过Java代码操作Redis
可以使用 Jedis 或 Lettuce 客户端。下面以添加 Jedis 依赖后的 `JedisPooled` 为例：

```java
import redis.clients.jedis.JedisPooled;

try (JedisPooled redis = new JedisPooled("localhost", 6379)) {
    String json = "{\"id\":1001,\"title\":\"Redis笔记\"}";
    redis.setex("note:1001", 300, json);
    String cached = redis.get("note:1001");
    System.out.println(cached);
}
```

实际项目要按服务配置提供认证信息；读到 JSON 字符串后，可以用 Jackson 转成 Java 对象。

参考资料：[Jedis Java客户端](https://redis.io/docs/latest/develop/clients/jedis/)。

## 学习过程记录
- 原笔记已整理常用类型、命令、持久化、复制和缓存问题
- 本次把一行数据分别对应到 String 和 Hash，并补充 Java 读写示例
- 待实践：验证缓存命中、过期后回源和数据更新；本次没有连接 Redis 或运行代码

## 补充参考
- 事务与回滚：[Transactions](https://redis.io/docs/latest/develop/using-commands/transactions/)
- 持久化：[Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)
- 内存淘汰：[Key eviction](https://redis.io/docs/latest/develop/reference/eviction/)
- 主从复制：[Redis replication](https://redis.io/docs/latest/operate/oss_and_stack/management/replication/)
- 消费者组：[XREADGROUP](https://redis.io/docs/latest/commands/xreadgroup/)

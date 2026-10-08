# JotangNote 笔记托管平台

Spring Boot 4 + MySQL + Redis + RabbitMQ 笔记应用，包含 JWT 登录、私人笔记管理和 AI 工具调用。前端在后端静态资源中，启动后访问应用地址即可。

## 环境要求

- JDK 17+、Maven 3.9+（也可使用仓库内的 Maven Wrapper）
- MySQL 8+、Redis、RabbitMQ
- Node.js 18+ 仅用于前端脚本回归测试，运行应用不需要 Node.js

## 首次运行

1. 在 MySQL 中执行仓库根目录的 `schema.sql`，创建 `jotangnote` 库、用户表、笔记表和消息处理记录表。
2. 启动 MySQL、Redis 和 RabbitMQ，按下表配置环境变量。默认配置适用于本地开发；部署到其他机器时填写实际服务地址和账号。
3. Windows 执行 `mvnw.cmd spring-boot:run`，Linux/macOS 执行 `./mvnw spring-boot:run`。
4. 浏览器访问 `http://localhost:8080`；其他机器访问 `http://应用服务器地址:端口`。

MySQL 命令行客户端中可以执行 `source schema.sql`。若数据库名称不是 `jotangnote`，相应修改脚本中的建库和 `USE` 语句。

## Docker 一键启动

安装并启动 Docker Desktop（使用 Linux 容器），在项目根目录执行：

```powershell
docker compose up -d --build --wait --wait-timeout 240
```

启动后访问 `http://localhost:8080`。不需要在宿主机安装 Java、Maven、MySQL、Redis 或 RabbitMQ；第一次构建需要联网下载镜像和 Maven 依赖。Dockerfile 在构建阶段执行 Java 测试并打包，运行阶段使用 Java 17 JRE 和普通用户。

镜像已经构建、代码没有修改时，可直接使用本机镜像启动，跳过重新构建和下载：

```powershell
docker compose up -d --no-build --pull never --wait --wait-timeout 240
```

此命令要求应用及三个依赖镜像已在本机。如果构建时镜像下载站报 `EOF`，但这些镜像已存在，也可使用此命令启动。修改应用代码后仍需执行带 `--build` 的命令重新构建。若提示找不到 `dockerDesktopLinuxEngine`，先启动 Docker Desktop 并等待引擎就绪。

Compose 同时启动应用、MySQL 8.4、Redis 7.4 和 RabbitMQ 4.2。首次启动新数据库数据卷时自动执行 `schema.sql`，应用等待依赖服务就绪后再启动。只映射应用端口，数据库、Redis 和 RabbitMQ 通过容器内部网络连接，不占用宿主机的 3306、6379、5672 端口。就绪检查配置遵循 [Docker Compose 启动顺序说明](https://docs.docker.com/compose/how-tos/startup-order/)。

可选配置：首次启动前复制 `.env.example` 为 `.env`（已有 `.env` 时直接编辑，保留现有配置），修改其中的端口、密码和 JWT 密钥（至少 32 字节）。默认配置用于本地开发；需要其他机器访问时将 `APP_BIND_HOST` 改为 `0.0.0.0`，并设置自己的密码和密钥。Compose 使用固定库名和服务账号 `jotangnote`；AI Key 继续使用项目现有配置。

常用操作：

```powershell
docker compose ps
docker compose logs -f app
docker compose down
docker compose up -d --no-build --pull never --wait --wait-timeout 240
```

`docker compose down` 保留 MySQL、Redis 和 RabbitMQ 数据卷，再次启动会继续使用已有数据。已有数据卷中的账号密码不会随着 `.env` 修改自动更新，需先在对应服务中修改账号密码。初始化脚本仅在空数据库数据目录执行；升级已有数据库仍需执行下节的升级流程。此配置创建独立的容器数据库，不会自动导入宿主机现有数据。

仅构建应用镜像（连接已有服务时使用）：

```powershell
docker build -t jotangnote:latest .
```

运行独立镜像时，通过下文的环境变量指定可连接的 MySQL、Redis 和 RabbitMQ 地址。Docker Desktop 中访问宿主机服务可使用 `host.docker.internal`；RabbitMQ 需使用允许远程连接的账号。

2026-10-07 Docker 验证：成功构建 `jotangnote:latest`，构建阶段 Java 测试共 22 项，20 项通过、2 项可选集成测试跳过，无失败或错误。四个容器健康检查均通过，并在容器内部完成注册、登录、经 RabbitMQ 创建/修改/删除笔记、MySQL 查询、Redis 缓存回填及失效验证，测试账号和笔记已清理。验证使用临时配置取消宿主机端口映射，正式配置保持 8080；启动前需保证宿主机 8080 未被其他程序占用。验证完成后停止验证容器，保留镜像和数据卷。

## 已有项目升级（2026-09-30）

新代码需要 `notes.cache_version` 字段和 `processed_note_messages` 表。只替换 jar、不升级数据库，会导致笔记操作失败。

1. 备份数据库，暂停写入，等待旧应用将 `note.queue` 的 Ready/Unacked 消息处理完，再停止所有旧应用实例。旧版 Redis 幂等记录无法可靠换算成数据库提交记录，不能混跑新旧消费者。
2. 在 MySQL 客户端执行 `source db/upgrade-20260930.sql`。脚本可重复执行，保留原有笔记；自定义库名时修改脚本中的 `USE`。
3. 检查旧 `note.queue.dlq` 的遗留失败操作，完成业务核对后再部署新版本。新版本使用 `note.ordered.queue` 和 `note.ordered.operation`；**保留原有 `note.queue`，不要删除队列或清空积压消息。**
4. 验证新增、修改、删除；检查新队列的单活跃消费者状态。不能混跑新旧版本。具体迁移步骤见[消息顺序性说明](docs/message-ordering.md)。

RabbitMQ 新队列启用 Single Active Consumer，消费并发和 prefetch 固定为 1。消息失败会重试、重入原队列，避免后续删除越过失败的修改；持续失败会阻塞队列，需要排查修复。旧失败队列保留供升级核对，新操作不自动转入该队列。

Redis 旧缓存无需手工清空，新代码只读取 `note:v3:笔记ID`，旧条目按原 TTL 自然过期。升级时停止旧应用后再启动新版本，避免旧消费者写入数据库却不删除新缓存。

## 配置

AI API Key 按项目约定明文保留在 `src/main/resources/application.properties`；其他服务参数支持环境变量。

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `PORT` | `8080` | 应用端口 |
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | MySQL 地址 |
| `DB_NAME` | `jotangnote` | 数据库名 |
| `DB_USER` / `DB_PASSWORD` | `root` / `123456` | 数据库账号 |
| `JWT_SECRET` | 开发占位值 | JWT 签名密钥，部署时设置自己的随机值，至少 32 字节 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 地址 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ 地址 |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` | RabbitMQ 账号 |
| `RABBITMQ_VHOST` | `/` | RabbitMQ virtual host |

连接远程 RabbitMQ 时创建可远程登录的账号并授予目标 virtual host 的权限，`guest` 通常仅能本地登录。可使用 Spring Boot 标准的 `SPRING_DATASOURCE_URL` 覆盖整个 JDBC URL，以配置 TLS 等连接参数；Redis 和 RabbitMQ 同样支持 Spring Boot 标准属性覆盖。

## 修复后的数据处理

- `/notes/my` 列出本人的笔记，详情、修改、删除和 AI 查询也校验归属。
- 新增、修改和删除返回“请求已受理”，前端轮询等待数据库结果。
- 消息 ID 在 MySQL 中有唯一约束，幂等记录和笔记操作在同一个 InnoDB 事务里提交。进程失败会一起回滚，重投可继续处理；已提交的消息重投不重复执行。
- 笔记写操作按保序队列入队顺序串行消费，多实例由 broker 选出一个活跃消费者；并发 HTTP 请求之间没有按点击时间定义的全局顺序。实现范围和失败恢复见[消息顺序性说明](docs/message-ordering.md)。
- 笔记详情先查询 Redis，命中直接返回，不访问 MySQL；未命中才查询 MySQL，查到后缓存 30 分钟，查不到直接返回不存在（暂未缓存空值）。
- 缓存未命中时使用有期限的 Redis 锁减少并发回源，拿到锁后再次检查缓存；等待重试耗尽或 Redis 不可用时降级查库。回填失败仍返回已查询到的数据库结果。
- 修改、删除在数据库事务提交后，用同一个 Redis DEL 命令删除正文缓存和重建锁。回填通过 Lua 原子检查锁令牌，已失去锁的旧请求不能重新写入旧缓存；释放锁也通过 Lua 校验令牌。
- 数据库提交与缓存失效之间存在短暂窗口，不保证强一致性。失效失败会触发 MQ 重试，重复消息跳过数据库写入但仍重试删缓存；当前消息持续失败时队列暂停推进，修复后自动继续，旧缓存也会按 TTL 过期。
- 数据库幂等记录不自动过期，避免延迟重放导致重复执行。若未来清理，应先明确消息最大重放期限。
- 登录过期或缺少令牌返回 HTTP 401 / `code=401`，前端统一退出并清空私人内容；退出时终止旧请求，迟到响应不会进入新账号界面。

`cache_version` 字段保留兼容，详情查询不再读取它。直接执行 SQL 或新增其他写入入口时，需要在提交后调用相同的缓存失效逻辑；只递增版本号不会使当前缓存失效。

## 验证与打包

```text
mvnw.cmd test -B -ntp
node --test src/test/frontend/account.test.cjs
mvnw.cmd package -B -ntp
java -jar target/JotangNote-0.0.1-SNAPSHOT.jar
```

Linux/macOS 将 `mvnw.cmd` 换成 `./mvnw`。Java 回归测试使用 H2 内存数据库，不依赖接手者本机的 MySQL、Redis 或 RabbitMQ，也不会消费业务队列。覆盖失败事务回滚、并发重复消费、笔记归属、缓存晚回填和鉴权响应；前端测试覆盖账号切换、迟到响应和过期登录。

2026-10-07 缓存流程调整验证：执行 `mvnw.cmd test -B -ntp`，共 18 项，16 项通过、2 项可选集成测试跳过，无失败或错误。`NoteCacheTests` 使用模拟 Redis 验证命中不查库、Redis 先于数据库读取、未命中回填、不存在的笔记、旧请求晚回填、删除失效、Redis 故障降级及抢锁后再次检查缓存；`NoteConsumerTests` 使用 H2 和模拟 Redis 验证提交后失效、失效失败重试及数据库写入幂等。本轮未连接真实 Redis、MySQL 或 RabbitMQ 做联调。

2026-10-07 顺序性调整验证：后端回归 22 项，20 项通过、2 项可选集成测试跳过，无失败或错误；单独开启 `RabbitBrokerTests` 后真实 RabbitMQ 测试 1 项通过，验证双消费者、失败重投不越序和正常停机接管。详细过程见[消息顺序性说明](docs/message-ordering.md)。

## 加分项学习与说明

- [长文本与图片替代存储方案](docs/storage-alternatives.md)：MySQL 拆表、对象存储、本地文件比较，以及上传权限、版本、一致性和迁移设计。本项是学习记录，尚未实现对象存储上传功能。
- [RabbitMQ 消息顺序性与幂等性](docs/message-ordering.md)：实际代码、使用流程、失败处理、升级步骤和已执行的测试结果。

可选 RabbitMQ 实测（需要可连接的测试 broker）：

```text
mvnw.cmd test -B -ntp "-Drabbit.integration=true"
```

实测通过 `RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USER`、`RABBITMQ_PASSWORD`、`RABBITMQ_VHOST` 配置连接，只创建并清理带随机前缀的测试队列，不读取业务队列中的消息。

可选 MySQL 升级脚本实测：`mvnw.cmd test -B -ntp "-Dmysql.integration=true"`。使用上表的数据库连接参数，测试账号需要创建/删除测试数据库的权限；只操作随机名称的临时测试库，验证升级脚本重复执行和旧数据保留。

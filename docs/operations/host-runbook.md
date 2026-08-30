# CC4C 宿主机运行手册

## 适用范围与边界

宿主机模式只管理本脚本启动的 CC4C 后端 JAR、前端 npm/Vite 或可选 Nginx，以及 Prometheus/Grafana。MySQL、Redis、RabbitMQ、Mailpit/SMTP 必须由管理员预先安装或由外部环境提供；宿主机脚本不会自动启动、停止、重启或接管这些依赖。

宿主机与 Compose 不得同时连接同一个数据库、Redis Session/缓存 namespace、RabbitMQ vhost 或上传目录。默认 Compose 项目是 `cc4c`，历史 `cc4c-v3` 仅用于迁移和回滚；当前 `cc4c-a7verify2` 等测试项目不在宿主机脚本管理范围内。

禁止读取、复制、打包或提交 `application.yml`、任意 `.env.*.local`、`deploy/secrets/local`、数据库备份、Docker 卷、上传文件、Cookie、Token、SMTP 凭据、Pepper 或消息密钥。脚本只在用户明确运行时读取本机 env，并且不输出变量值。

## 版本和依赖预检

按根目录 `versions.yml` 检查 Java 21、Maven 3.9.16、Node 24.18.0、npm 11.16.0、MySQL 8.4.11、Redis 7.4.10、RabbitMQ 4.3.5、Mailpit 1.31.0、Prometheus 3.13.2、Grafana 13.1.0 和可选 Nginx 1.28.3。后端 JAR 必须由 canonical `backend/target/cc4c-4.0.0-SNAPSHOT.jar` 提供，前端 Dev 模式必须已有 `frontend/node_modules`，脚本不会执行 `npm install`。

环境文件按 canonical 优先、旧路径回退：

```text
backend/.env.runtime.local
back-end/CC4C/.env.runtime.local
frontend/.env.local
front-end/CC4C/.env.local
infrastructure/observability/.env.observability.local
observability/.env.observability.local
```

宿主机后端环境必须提供现有 `CC4C_*` 变量，并补充 `CC4C_MAIL_HOST`、`CC4C_MAIL_PORT`、`CC4C_MAIL_AUTH`、`CC4C_MAIL_SSL_ENABLED` 和 `CC4C_MAIL_STARTTLS_ENABLED`。`CC4C_DB_URL` 中的数据库名必须与命令行 `-ConfirmDatabase` 完全相同。

## 外部依赖预检

从仓库根目录执行：

```powershell
.\\scripts\\development\\host-preflight.ps1 -Component All -ConfirmDatabase <exact-database-name>
```

预检只检查 TCP 和必要的健康/版本条件：

1. MySQL/Flyway：确认精确 JDBC 数据库名和端口；Flyway 仍由应用启动时按 V1–V7 执行，不执行独立降级、`clean`、`repair`、`DROP DATABASE` 或基线猜测。
2. 安全 Redis：确认会话、验证码和限流 Redis 地址可达；不执行 `FLUSHDB`、`FLUSHALL` 或 namespace 清理。
3. 业务缓存 Redis：确认独立地址或明确不同 database/namespace 可达；业务缓存 namespace 必须和 Session namespace 分离。
4. RabbitMQ：确认 AMQP URL 使用预配置的 `cc4c` vhost，并保留现有 `cc4c.v3.*` namespace。管理员必须提前配置应用/监控用户、权限和 `rabbitmq_prometheus`；脚本不删除队列、不 purge、不删除 vhost。
5. Mailpit/SMTP：确认 `CC4C_MAIL_HOST:CC4C_MAIL_PORT` 可达；默认 Mailpit 使用回环 1025 端口，外部 SMTP 的认证/TLS 只由用户提供的 env 和 secret 配置。

## 启动顺序

启动脚本只管理自身创建的 PID，固定顺序为：

```text
MySQL/Flyway → 安全 Redis → 业务缓存 Redis → RabbitMQ → Mailpit/SMTP
→ 后端 JAR → 前端 npm/Vite 或 Nginx → 可选 Prometheus/Grafana
```

外部依赖通过预检确认后，启动后端和前端：

```powershell
.\\scripts\\development\\start-host-stack.ps1 `
  -ConfirmDatabase <exact-database-name> `
  -FrontendMode Dev
```

脚本设置进程级 `SPRING_CONFIG_NAME=application-example` 和 `SPRING_APPLICATION_NAME=CC4C`，退出或启动完成后恢复调用进程原环境。后端监听 `127.0.0.1:4080`，管理端监听 `127.0.0.1:4081`；前端 Vite 监听 `127.0.0.1:5173`。

静态模式要求用户提供已验证的 Nginx 绝对路径和现有 `frontend/dist`：

```powershell
.\\scripts\\development\\start-host-stack.ps1 `
  -ConfirmDatabase <exact-database-name> `
  -FrontendMode Static `
  -NginxPath <absolute-nginx-exe>
```

临时 Nginx 配置只写入被忽略的 `temp/cc4c-host-frontend`，使用 `alias` 提供 `frontend/public/blogImg` 和 `frontend/public/avatar`；不修改 tracked 配置。

## 健康、日志和停止

```powershell
.\\scripts\\development\\health-host-stack.ps1
.\\scripts\\development\\health-host-stack.ps1 -IncludeObservability
```

健康检查只读 PID、可执行文件、监听端口和 HTTP 状态。后端日志写入 `temp/cc4c-host-backend`，前端日志写入 `temp/cc4c-host-frontend`，观测日志写入 `temp/cc4c-observability`；日志不得包含密码、Cookie、Token、请求体、连接凭据或消息载荷。

停止只停止状态文件中记录的精确进程，按前端、后端、观测的逆序执行；外部依赖保持运行：

```powershell
.\\scripts\\development\\stop-host-stack.ps1
```

若单项 PID 的可执行文件、启动标记或命令行摘要不匹配，脚本拒绝停止并保留记录，禁止改用进程名批量终止。启动失败时，编排脚本只逆序停止本次已经成功记录的组件。

## RabbitMQ、Outbox 和排空

切换或维护前，管理员必须冻结新写入，精确停用 Dispatcher/Consumer，并记录 Outbox 未处理项、RabbitMQ ready/unacked、重试和 DLQ 摘要。确认 Outbox 已排空、RabbitMQ 无未确认消息且消费者没有未完成外部处理后才允许切换；失败时恢复旧配置并重新启用此前精确停用的消费者。

不得通过宿主机脚本 purge 队列、删除 vhost、删除 exchange、清空 Redis 或绕过 ACK/NACK。消息仍保持至少一次投递、Inbox 幂等、Publisher Confirm、重试和 DLQ 语义，三个已发布 `*.v1` 事件不变。

## 应用契约和安全验收

宿主机与 Compose 使用同一套 HTTP URL、DTO、Cookie、CSRF、上传访问路径和观测协议。后端保存博客图片和头像的位置由 `CC4C_SAVE_IMG_PATH`、`CC4C_SAVE_AVATAR_PATH` 提供，浏览器访问路径保持 `/blogImg/`、`/avatar/`；Cookie Secure、CORS 和 CSRF 由运行环境显式配置。

验收至少覆盖后端 readiness、前端 `/`、`/login`、`/register`、课程、博客、收藏、评论、管理消息和上传资源；管理端口与 Prometheus/Grafana 只绑定回环地址，观测 Basic 身份不得复用业务用户身份。OpenAPI 快照必须保持不变，数据库迁移仍为 Flyway V1–V7。

故障恢复只允许停止新模式精确 PID、恢复旧配置/namespace、重新启动所需外部依赖并重新验证健康和消息积压。不要使用 `docker compose down -v`、卷删除、Flyway `clean/repair`、Redis flush、Rabbit purge 或广泛进程终止。

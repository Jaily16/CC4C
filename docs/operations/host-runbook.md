# CC4C 本机运行手册

## 适用范围与安全边界

本机入口只管理当前工作区的后端 JAR 和业务前端 Node/Vite，启动顺序为后端、前端，停止顺序相反。MySQL、一个 Redis、RabbitMQ、SMTP 和 Prometheus 均由用户预先提供；项目不启动、停止、重启或重载这些外部服务。

数据库必须已存在，通过 -ConfirmDatabase 精确确认。脚本不建库、不清库、不删除数据。业务 HTTP API、DTO、Cookie、CSRF、上传 URL、Flyway V1–V7 和三个已发布事件协议保持不变。不提供静态 Web 服务器或 Grafana 运行入口。

不得读取、复制、暂存或上传本机私有配置、秘密目录、数据库内容或备份、上传数据、Cookie、Token、SMTP 授权码、Pepper 和消息密钥。环境文件仅在用户明确运行项目入口时由严格加载器读取，值不回显、不展开变量、不执行表达式。已跟踪的 backend/src/main/resources/application.yml 是受控脱敏配置，不得用旧本机配置覆盖。

## 工具和构建

版本以 [versions.yml](../../versions.yml) 为准：PowerShell 7.6.5、Java 21、Maven 3.9.16、Node 24.18.0、npm 11.16.0、MySQL 8.4.11、原生 Redis 8.2.9、RabbitMQ 4.3.5、Prometheus 3.13.2。SMTP 使用用户控制的邮箱服务。

所有 PowerShell 命令在 PowerShell 7 中执行。临时选择 Java 时，保存并在结束后恢复调用会话的 JAVA_HOME 和 PATH。生产构建不运行自动化测试：

~~~powershell
Set-Location -LiteralPath 'D:\codex\CC4C_v5\backend'
mvn --no-transfer-progress clean package -DskipTests
mvn --no-transfer-progress spotless:check
Set-Location -LiteralPath 'D:\codex\CC4C_v5\frontend'
npm run lint
npm run format:check
npm run build
~~~

后端产物为 backend/target/cc4c-5.0.0-SNAPSHOT.jar 和 backend/target/cc4c-5.0.0-SNAPSHOT-admin-bootstrap.jar。资源打包只过滤受控 application.yml 中的 Maven 版本标记，Spring 环境占位符保持原样，Flyway SQL 不过滤、不改写。前端产物为 frontend/dist；运行入口依赖现有 frontend/node_modules，不自动安装依赖。

## 三端配置入口

只使用以下三份模板及对应本机文件：

| 应用 | 受控模板 | 用户手工准备、Git 忽略的文件 |
| --- | --- | --- |
| 后端 | backend/.env.example | backend/.env.local |
| 业务前端 | frontend/.env.example | frontend/.env.local |
| 观测前端 | observability/.env.example | observability/.env.local |

观测前端目前只有模板，没有页面、独立登录或构建入口，不声称其 lint 或生产构建通过。两个前端模板都只有公开的 VITE_API_BASE_URL，默认 http://localhost:4080，不得放入 Prometheus 地址、管理凭据或其他秘密。

用户根据模板手工整理 .env.local，保留原仓库外文件作为私有资料。加载器不接受环境文件路径参数或旧目录回退。非注释行必须是字面的 NAME=value，不使用 export、引号展开或命令替换；重复、未知、缺失或非法字段都会失败。文件及全部父目录必须是普通本机路径，不允许链接或 reparse point。

后端配置规则：

- 数据库 URL、账号、密码、SMTP 主机和端口必须显式提供，不回退到数据库管理员账号或默认邮件服务。
- Session 和缓存共用 CC4C_REDIS_URL，保留独立连接；两个 namespace 均必填且不同。用户应移除旧缓存专用 Redis URL 项。
- JDBC 数据库名必须与 -ConfirmDatabase 逐字符一致；连接和验证等待均有界。
- SMTP 认证、隐式 SSL 或 STARTTLS 应与邮箱服务一致，不能同时启用两种 TLS 方式；TCP 预检不代替实际收件确认。
- CORS 只接受精确来源，不接受通配符、路径或凭据。Cookie Secure 与访问协议一致。
- 管理端固定绑定 127.0.0.1，默认端口 4081，管理身份不交给业务浏览器。
- 上传模板使用以 backend 为工作目录解析的 ../temp/uploads/blogImg/ 和 ../temp/uploads/avatar/。已有隔离环境保留当前绝对路径，不迁移、不清空文件。

Vite 启动器只注入公开 API 地址及两个非 VITE_ 变量 CC4C_HOST_BLOG_IMG_ROOT、CC4C_HOST_AVATAR_ROOT，不传入后端凭据。两根路径必须是不同普通目录；浏览器仍使用 /blogImg/、/avatar/，不回退到旧 public 上传目录。前端不要另建 .env、.env.development、.env.production 或对应模式的 .local 文件；不要用额外环境变量或参数覆盖私有配置来源。

只检查忽略规则，不输出内容：

~~~powershell
Set-Location -LiteralPath 'D:\codex\CC4C_v5'
git check-ignore -- backend/.env.local frontend/.env.local observability/.env.local
~~~

## 预检、启动与健康

~~~powershell
.\infrastructure\host\host-preflight.ps1 -Component All -ConfirmDatabase <精确数据库名>
.\infrastructure\host\start-host-stack.ps1 -ConfirmDatabase <精确数据库名>
.\infrastructure\host\health-host-stack.ps1
~~~

预检可选 MySQL、Redis、RabbitMQ、SMTP、Backend、Frontend 或 Prometheus：

1. MySQL：只解析 JDBC 地址、精确确认数据库名并检查 TCP，不执行 SQL。Flyway 校验和迁移由后端正常启动执行。
2. Redis：只验证同一地址可达，不查询键、不清空 namespace。
3. RabbitMQ：使用预配置 cc4c vhost 和现有 cc4c.v3.* namespace，不创建、删除或 purge 消息资源。
4. SMTP：不发送预检邮件，不打印账号或授权码。
5. 默认业务端口 4080、管理端口 4081、前端端口 5173 互不相同且必须空闲；不杀占用者、不自动换端口。
6. 外部 Prometheus：查询就绪和版本，启动前不要求尚未运行的后端已被成功抓取。

后端以 backend 为工作目录，使用 SPRING_CONFIG_NAME=application 读取受控打包配置。前端以 frontend 为工作目录，Vite 绑定 127.0.0.1:5173 并启用严格端口检查。整栈入口在后端健康后启动前端，所有临时环境变量在 finally 中恢复。

独立入口：

~~~powershell
.\backend\scripts\start-backend.ps1 -ConfirmDatabase <精确数据库名>
.\frontend\scripts\start-frontend.ps1
.\frontend\scripts\stop-frontend.ps1
.\backend\scripts\stop-backend.ps1
~~~

## 外部 Prometheus

公开模板及规则位于 infrastructure/prometheus/。reference/grafana/ 中的三个 Dashboard 和两个 provisioning 文件仅供后续转换参考，不作为可启动组件。模板的 RabbitMQ 抓取定义保持原样，外部实例实际启用哪些抓取任务由用户管理。

若外部私有配置引用旧规则路径，由用户自行调整、检查和重载。项目不读取该私有配置，不启动、停止或重载实例。检查入口只校验仓库公开模板和规则，再查询外部实例：

~~~powershell
.\infrastructure\prometheus\check-prometheus.ps1 -PromtoolPath 'D:\tool\prometheus-3.13.2.windows-amd64\promtool.exe' -RequireBackendScrape
~~~

-PrometheusUrl 默认 http://127.0.0.1:9090。实例必须就绪且版本为 3.13.2；指定 -RequireBackendScrape 时，up{job="cc4c-backend"} 至少有一项且所有返回值为 1。不执行规则测试、不查看 TSDB 内容、不声称外部私有配置正文已经验证。

## 状态、日志与停止

状态仍位于 temp/cc4c-host-stack/{backend,frontend,stack}.json。两端记录包含 PID、绝对可执行文件、完整应用标记和操作系统真实进程创建时间，栈记录保存本次身份快照。已停止旧记录可由新启动正常更新；旧运行记录缺字段或身份不匹配时停止人工排查，不猜测补全。

日志仍位于 temp/cc4c-host-backend/ 和 temp/cc4c-host-frontend/，每次使用时间戳加唯一 ID 的新文件名，不覆盖旧日志。仅在授权后本地查看，不上传凭据、请求正文或完整配置。

~~~powershell
.\infrastructure\host\health-host-stack.ps1 -IncludePrometheus
.\infrastructure\host\stop-host-stack.ps1
~~~

健康检查核对进程真实身份、各端口精确所有者、前端 HTTP 200 及后端 health/liveness/readiness 的 UP 状态。停止前确认 Prometheus 后端抓取为 1。

停止只针对本次快照中身份完全匹配的前端、后端，复核创建时间防止 PID 复用；不按名称、端口或进程树终止。启动失败只逆序停止本次已记录组件。身份不明、部分停止或状态写入失败时保留现场、日志和数据，不扩大恢复范围。

## 管理员引导和密码迁移

已有管理员不重复引导。新环境完成 Flyway 后，仅当用户明确要求时执行：

~~~powershell
.\backend\scripts\bootstrap-admin.ps1 -AdminId <七位管理员ID> -ConfirmDatabase <精确数据库名> -PasswordFile <仓库外绝对密码文件路径>
~~~

密码文件必须由用户预先创建，自身和父路径均为普通路径。引导器使用 backend 固定配置，不生成密码、不创建数据库、不输出密码，最后恢复全部临时变量。

离线密码迁移保留备份路径、SHA-256 和精确数据库确认参数，详见 [数据库说明](../../infrastructure/database/README.md)。它不是启动步骤，不在已有隔离 smoke 环境重复执行。

## 数据和人工验收

复用既有数据库、账号、Redis/RabbitMQ namespace、密钥和上传路径。人工验收包括登录/会话恢复、注册页面、课程/博客浏览、收藏与取消、头像/博客图片上传、唯一标识博客和评论、管理员仅审核该博客、只读消息页、审核邮件、审核后可见性及本人删除流程。

收藏只撤销本轮新增项；删除前确认精确目标，不删除旧博客、评论或上传文件。既有账号不重复注册，过去注册成功证据不能表述为本轮重跑。验证码和管理员密码由用户输入；浏览器管理会话，不读取或导出 Cookie/Token。

保留至少一次投递、Inbox 幂等、Publisher Confirm、ACK/NACK、重试和 DLQ 语义。排空或停用消费必须另行确认，不对未知消息执行 retry/ignore，不 purge。数据库备份、恢复和消息维护均不是本机启停脚本的隐式操作。

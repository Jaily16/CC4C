# CC4C 容器运行手册

## 适用范围

本手册对应 V3 方面七的本机 Docker Compose 交付。默认环境用于开发、演示和验收，不直接等同生产部署。它使用 Mailpit 捕获邮件、HTTP Cookie Secure=false、显式开启 OpenAPI，并把宿主入口限制在 `127.0.0.1`。

禁止读取、复制或打包 `back-end/CC4C/src/main/resources/application.yml`。Compose 只使用脱敏 `application-example.yml` 和挂载到 `/run/secrets` 的本机 secret 文件。

## 前置条件

- Docker Desktop，Docker Engine 24+；本次验收为 28.0.4。
- Docker Compose v2；本次验收为 2.34.0。
- PowerShell 7 或 Windows PowerShell 5.1。
- 首次构建需要访问 Maven、npm 和基础镜像仓库。

Java、Maven、Node、MySQL、Redis 与 RabbitMQ 不需要安装到宿主机；只有单独运行源码时才需要相应工具链。

## 首次启动

在仓库根目录执行：

```powershell
.\deploy\scripts\prepare-local.ps1
docker compose -p cc4c-v3 up --build -d --wait
```

`prepare-local.ps1` 只创建缺失文件，已有 secret 不会被覆盖。生成目录 `deploy/secrets/local/` 已被 Git 忽略；不要复制、截图、提交或发送其中内容。

长期服务及入口：

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 前端 | http://127.0.0.1:5173 | Vue SPA，支持深层路由刷新 |
| 后端 | http://127.0.0.1:4080 | 业务 API |
| Actuator | http://127.0.0.1:4081 | readiness 匿名；指标等需独立 Basic 身份 |
| Grafana | http://127.0.0.1:3000 | 用户名 `cc4c_grafana_admin`，密码来自本机 secret |
| Prometheus | http://127.0.0.1:9090 | 应用与 Rabbit 指标 |
| RabbitMQ 管理页 | http://127.0.0.1:15672 | 仅回环地址 |
| Mailpit | http://127.0.0.1:8025 | 本地邮件捕获 |

MySQL、两个 Redis、AMQP 和 RabbitMQ 指标端口不向宿主机发布。前端、后端、RabbitMQ、Mailpit、Prometheus 与 Grafana各自使用独占宿主访问网络，均关闭 bridge masquerade；应用和观测内部网络设置为 internal。

## 健康与状态

```powershell
docker compose -p cc4c-v3 ps
```

正常情况下九个长期服务均为 `healthy`。初始化服务 `rabbit-init` 与 `storage-init` 完成后退出属于正常状态。

常用只读检查：

```powershell
Invoke-WebRequest http://127.0.0.1:4081/actuator/health/readiness -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:8025/readyz -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:9090/-/ready -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:3000/api/health -UseBasicParsing
```

日志可能包含内部事件 ID 或错误枚举，但不得包含密码、Cookie、验证码、邮箱原文、Redis/Rabbit URL 或消息载荷。排障时只使用精确服务名：

```powershell
docker compose -p cc4c-v3 logs --tail 200 backend
```

## 一次性管理员引导

管理员 ID 必须是七位数字。密码由 `prepare-local.ps1` 生成，不通过命令行传递：

```powershell
.\deploy\scripts\bootstrap-admin.ps1 -AdminId <七位管理员ID>
```

引导器仅在不存在有效管理员时创建账号。相同 ID 且密码匹配时幂等成功；ID 冲突、已有其他管理员或密码不一致时失败。命令不输出管理员 ID、明文或哈希。

## Grafana 登录

- 用户名固定为 `cc4c_grafana_admin`。
- 密码保存在 `deploy/secrets/local/grafana_admin_password`，不要发送给他人。
- Secret 只在空 Grafana 数据卷首次初始化时设置管理员。若保留卷后更换了 secret，需使用 Grafana CLI 对用户 ID 1 显式重置；不要删除 Grafana 卷来绕过认证。
- Grafana 默认五分钟内连续失败五次会暂时阻止登录；保留该保护，不要为排障关闭。

## 默认 Mailpit 与外部 SMTP

默认后端连接 `mailpit:1025`，无认证、无 TLS。注册、找回密码、博客提交与审核通知可在 Mailpit UI 中验收，不会投递到公网。

外部 SMTP 只通过显式覆盖启用：

```powershell
# 先在 deploy/secrets/local/ 中准备 smtp_username 与 smtp_password
$env:CC4C_MAIL_HOST = '<smtp-host>'
$env:CC4C_MAIL_PORT = '587'
$env:CC4C_MAIL_AUTH = 'true'
$env:CC4C_MAIL_STARTTLS_ENABLED = 'true'
docker compose -p cc4c-v3 -f compose.yml -f compose.smtp.yml up -d --wait backend
```

覆盖文件只给后端增加 egress 网络。不得把 SMTP 用户名或密码写进 Compose 文件、Shell 历史、README 或日志。

## 停止、重启与数据持久化

普通停止：

```powershell
docker compose -p cc4c-v3 down
```

重新启动：

```powershell
docker compose -p cc4c-v3 up -d --wait
```

禁止给普通停止添加 `-v`。MySQL、两个 Redis、RabbitMQ、Prometheus、Grafana、博客图片与头像共八个项目级命名卷会在普通 `down`/`up` 后保留。

只有明确需要完全重置本地环境时，才允许运行：

```powershell
.\deploy\scripts\reset-local.ps1 -ConfirmProjectName cc4c-v3
```

脚本还要求再次输入 `DELETE-cc4c-v3`。该操作会删除本项目卷且不可恢复；执行前必须备份。

## 备份与回滚

镜像升级前：

1. 使用 `mysqldump --single-transaction --skip-lock-tables` 导出 MySQL，并保存 SHA-256。
2. 备份 `blog_uploads` 与 `avatar_uploads` 卷。
3. 记录当前后端、前端镜像的 registry digest；本地 image ID 不能替代发布 digest。
4. 确认 Outbox 没有未知积压，Rabbit 持久队列不得 purge。

回滚只切换到已记录旧 digest。Flyway V1–V7 不提供破坏性 down migration，禁止 `clean/repair`；数据库恢复应写入新库并完成结构、数据和哈希比对后再切换。

## Testcontainers 与质量门禁

```powershell
cd back-end/CC4C
.\run-tests.ps1 clean verify
```

脚本只校验 Java 21 与 Docker。MySQL、两个 Redis 和 RabbitMQ 由 Testcontainers 启动，标准测试不读取本机 `.env.test.local` 或 Compose 服务。

前端：

```powershell
cd front-end/CC4C
npm ci
npm run test:security
npm audit --audit-level=high
npm audit --omit=dev --audit-level=high
npm run build
```

容器性能 profile：

```powershell
.\deploy\scripts\run-container-performance.ps1 -StandardRounds 3
```

性能工具只操作 Compose 内精确的 `cc4c_perf_test` 和保留 ID 区间，原始结果写入已忽略 `temp/`。

## 发布边界

质量工作流响应 PR、main push 和手工触发；发布工作流只响应严格 `vX.Y.Z` 标签，重新运行质量和容器性能门禁后才构建多架构 GHCR 镜像并生成 SBOM、provenance 与 attestation。

本手册不授权 `git add`、提交、推送、Git 标签、GHCR 登录或镜像发布。上述每项仍需用户单独明确授权。

# ADR-0001：以 Compose、Testcontainers 和 tag-only 工作流交付 CC4C

- 状态：已接受
- 日期：2026-08-29
- 基线：`f0f6fa1`

## 背景

V3 方面一至六已经形成 Java 21 模块化单体、安全会话、Redis Cache-Aside、Transactional Outbox/RabbitMQ 与可观测性证据，但本机测试仍依赖外部服务，启动步骤分散，前端依赖存在已知高危漏洞，且没有可审查的供应链发布流程。

目标是让开发、测试和交付可复现，同时保持现有 URL、DTO、Cookie、CSRF、Flyway V1–V7、六模块边界和异步可靠性语义不变。

## 决策

1. 使用一个 `compose.yml` 编排前端、后端、MySQL、两个 Redis、RabbitMQ、Mailpit、Prometheus 和 Grafana；外部 SMTP 仅通过独立覆盖文件启用。
2. 使用项目级命名卷保存数据库、Redis、RabbitMQ、观测数据和上传文件。普通 `down` 保留卷，破坏性重置由带精确项目名与二次确认的脚本执行。
3. 使用 Compose secrets 挂载本机秘密。后端入口只在进程启动时读取 secret 文件；不把秘密写进镜像、Compose 环境、日志或命令行。
4. 前后端使用多阶段镜像、非 root、只读根文件系统、tmpfs、`no-new-privileges` 和最小 capability。宿主入口只绑定回环地址，数据服务保持内部隔离。
5. 后端集成测试使用 Testcontainers 1.21.4 管理 MySQL、两个 Redis 和 RabbitMQ；标准测试不读取 `.env.test.local` 或本机服务，reuse 禁用并由 Ryuk 清理。
6. 默认邮件使用 Mailpit，避免本地和 CI 发送真实邮件。真实 SMTP 必须显式提供 egress 与 secret。
7. 前端升级至 Node 24、Vue 3.5、Vite 8 和已修复安全版本，并对所有 Markdown 输出执行统一净化。
8. GitHub Actions 将质量与发布分离：PR/main 只执行质量门禁；严格 SemVer 标签重跑质量与容器性能后，才定义 GHCR 多架构发布、SBOM、provenance 和 attestation。
9. 项目不自动部署服务器。提交、推送、打标签和发布镜像分别需要人工授权。

## 结果

### 正面

- 新开发者只需 Docker 与一条 Compose 启动命令即可获得完整环境。
- 测试服务完全隔离，避免误用本机数据库、Redis 或 RabbitMQ。
- 数据卷、上传目录和 Rabbit 队列具备明确持久化与安全重置语义。
- Mailpit 提供确定、无公网副作用的验证码和通知验收。
- 供应链门禁、OpenAPI 漂移、漏洞扫描和多架构发布均可审查。

### 代价

- 完整 Testcontainers 与 Compose smoke 需要 Docker，首次拉取镜像和依赖耗时较长。
- 本地 Compose 是开发/验收配置，不自动满足生产 TLS、外部秘密管理、备份、反向代理或部署编排要求。
- GitHub Actions 尚需首次远程运行才能证明 Runner 行为；本地等价门禁不能替代远程证据。
- 本地镜像只有 image ID，没有 registry digest；只有实际 GHCR 发布后才能记录可移植 digest。

## 被否决的方案

- 继续依赖本机共享 MySQL/Redis/RabbitMQ：隔离和可重复性不足，存在误清理真实数据风险。
- 在 CI 使用固定外部测试服务：凭据与并发隔离复杂，故障会污染多个工作流。
- 在镜像或 Compose 环境中硬编码秘密：会进入镜像历史、配置展开或日志。
- 默认投递真实 SMTP：会让测试产生外部副作用并依赖第三方可用性。
- main push 自动发布或自动部署：扩大权限与变更半径，不符合当前人工授权边界。
- 使用 `latest` 基础镜像或浮动 Action tag：不可复现，也难以审查供应链变化。

## 后续约束

- 基础镜像升级必须审阅 digest、重新运行 Testcontainers、Trivy、Compose smoke 和浏览器门禁。
- V1–V7 迁移不得因容器化而执行 `clean/repair` 或破坏性 down migration。
- 发布记录必须使用 Buildx/registry 返回的 digest，不能把本地 image ID 伪装成发布 digest。
- 若未来引入 Kubernetes、云秘密管理或服务器自动部署，应创建新的 ADR，而不是悄悄扩大本决策范围。

# CC4C V3 技术栈升级总结

> 收口日期：2026-08-29
>
> 规划基线：`54262da`
>
> 方面七实现提交：`a22a329`
>
> 当前状态：七个方面均已实现、自动验证并完成浏览器验收；成果已推送到 `origin/main`。提交 `8f29872` 对应的 GitHub Actions 质量工作流已成功完成；尚未创建 SemVer 标签或发布 GHCR 镜像。

## 1. 本次迭代做了什么

V3 没有把 CC4C 拆成一组难以维护的微服务，而是在保留课程、博客、评论、收藏、用户和管理端完整业务的前提下，把原来的“功能型分层单体”升级为“可验证的模块化单体”。升级覆盖运行时、依赖治理、模块边界、API 与数据库、安全认证、缓存、异步可靠性、可观测性、测试隔离和交付链路。

核心变化可以概括为四点：

1. **运行基础现代化**：Java 17/Spring Boot 2.6 进入 Java 21/Spring Boot 3.5/Jakarta 体系，去掉重复或无实际用途的依赖。
2. **正确性可验证**：DTO、校验、HTTP 状态、Flyway、模块边界测试、OpenAPI 和 Testcontainers 把隐含约定变成自动门禁。
3. **性能与可靠性可证明**：热点缓存、批量查询、复合索引、Transactional Outbox、RabbitMQ、Inbox 幂等和故障演练均有真实对照证据。
4. **运行状态可观察、交付可复现**：请求关联、结构化日志、指标、健康检查、告警、Grafana、Gatling、Docker Compose 和 GitHub Actions 形成完整工程闭环。

## 2. 技术栈升级全景

| 领域 | V3 基线 | 当前实现 | 主要解决的问题 |
| --- | --- | --- | --- |
| Java / Web | Java 17、Spring Boot 2.6.11、`javax.servlet` | Java 21、Spring Boot 3.5.16、Jakarta Servlet | 旧框架生命周期、Jakarta 兼容、现代 JVM 与 Boot 能力无法使用 |
| 数据访问 | MyBatis-Plus 3.5.2，同时存在 MyBatis Starter、MPJ、Druid、Fastjson | MyBatis-Plus 3.5.17 Boot 3 Starter、HikariCP、Jackson | 重复自动配置、依赖冲突、无使用价值的库、数据源维护成本 |
| 架构 | 按 Controller/Service/Mapper 分层的单体 | Spring Modulith 1.4.12 验证的六模块单体 | 跨领域随意引用、内部实现泄露、重构影响面不清晰 |
| API | 实体直接收发、业务失败常用 HTTP 200、列表契约不统一 | DTO、Bean Validation、统一异常、正确 HTTP 状态、分页、OpenAPI 2.8.17 | 越权字段覆盖、校验分散、前后端契约模糊、接口难验证 |
| 数据库版本 | 手工 SQL 初始化和变更 | Flyway V1–V7、索引与约束迁移、空库/已有库门禁 | 环境结构漂移、迁移不可追踪、初始化不可复现 |
| 身份安全 | 自定义业务 Cookie、明文密码比较 | Spring Security、Spring Session Redis、BCrypt、CSRF、角色与所有权校验 | Cookie 可伪造、密码泄露风险、越权、会话撤销和并发会话不可控 |
| 缓存 | 无业务缓存 | 独立 Redis Cache-Aside、负缓存、TTL 抖动、单飞、短锁、事务后失效 | 热点重复查询、缓存击穿/穿透、写后脏数据和 Redis 故障放大 |
| 异步 | HTTP 线程同步发送邮件 | MySQL Outbox、RabbitMQ quorum queue、Publisher Confirm、Inbox、重试/DLQ | SMTP 拖慢主链路、Broker 故障丢消息、重复消费和失败不可恢复 |
| 可观测 | 分散文本日志 | Actuator、Micrometer、Prometheus 3.13.2、Grafana 13.1.0、ECS JSON、`X-Request-ID` | 故障只能靠猜、性能优化无证据、消息积压和缓存效果不可见 |
| 测试 | 依赖本机 MySQL/Redis/RabbitMQ | JUnit 5、Testcontainers 1.21.4、Flyway/Modulith/契约/故障测试 | 测试污染开发数据、环境差异、无法在 CI 可靠复现 |
| 前端 | Vue 3.2、Vite 3、Axios 0.18 位于开发依赖、旧编辑器残留 | Vue 3.5.42、Vite 8.2.2、Axios 1.19.0、Element Plus 2.14.5、sanitize-html 2.17.7 | 依赖漏洞、客户端分散、CSRF/Session 处理重复、Markdown XSS 风险 |
| 交付 | 手工启动与本机配置 | Docker Compose、非 root 多阶段镜像、Compose secrets、Mailpit、GitHub Actions、Trivy | “只在某台电脑能运行”、秘密进入配置、人工门禁遗漏、发布不可追踪 |

## 3. 七个方面分别解决了什么

### 3.1 基础版本与依赖现代化

**采用技术：** Java 21、Spring Boot 3.5.16、Jakarta Servlet、MyBatis-Plus 3.5.17、HikariCP、Jackson、Axios 1.19.0。

**解决的问题：**

- Spring Boot 2.6 与旧 `javax` API 阻塞后续 Security、Actuator、Testcontainers 等现代组件升级。
- MyBatis Starter 与 MyBatis-Plus Starter 同时存在，容易产生重复自动配置和版本不一致。
- MPJ、Druid、Fastjson 在业务中没有不可替代的使用点，却扩大依赖树和漏洞面。
- 上传路径采用平台相关拼接，Windows 路径无法可靠迁移到 Linux/容器。
- 前端页面各自导入 Axios、硬编码后端地址并重复设置凭据选项。

**开发收益：** 依赖由 Spring Boot BOM 和专用 Starter 统一管理；数据源收敛为 HikariCP；JSON 收敛为 Jackson；文件路径跨平台；前端只有一个 API 客户端和一个公开 Base URL 配置点。后续六方面不再需要兼容旧 Servlet 与重复依赖。

### 3.2 模块化、API 与数据治理

**采用技术：** Spring Modulith 1.4.12、DTO、Bean Validation、统一异常处理、MyBatis-Plus 分页、springdoc OpenAPI 2.8.17、Flyway V1–V3。

**解决的问题：**

- 传统按技术层分包使用户、课程、博客、互动和审核互相访问内部实体或 Mapper。
- 请求体直接绑定数据库实体，客户端可能覆盖服务端 ID、时间、状态等字段。
- 列表一次性查全量或在内存截取，数据增大后延迟和内存占用不可控。
- 手工 SQL 无法证明不同环境使用相同表、约束和索引。
- 接口错误只靠文档文字描述，前端难以稳定处理。

**开发与性能收益：** 六个领域模块的允许依赖由测试自动验证；写接口只接受明确 DTO；状态码和分页契约统一；数据库分页、批量评论装配和聚合查询消除多处 N+1；OpenAPI 快照可以检测接口漂移；Flyway 使空库和已有库升级具有同一来源。

### 3.3 安全认证体系

**采用技术：** Spring Security、Spring Session Data Redis、BCrypt、Cookie CSRF、Redis Lua/HMAC 限流与验证码、服务层所有权校验。

**解决的问题：**

- 旧 `user_email` 和 `admin` Cookie 直接承载身份，客户端值不能作为可信认证依据。
- 历史密码使用明文比较，数据库泄露会直接暴露凭据。
- 控制器信任路径或请求体中的用户 ID，存在水平越权风险。
- 改密、退出、跨浏览器登录和管理员单会话缺少统一会话撤销能力。
- 登录、验证码和内容写入缺少原子限流。

**开发与运行收益：** 身份统一由服务端 Session 和不可读的 `CC4C_SESSION` 表示；密码离线迁移为 `{bcrypt}`；所有写请求受 CSRF 保护；权限采用默认拒绝，并在服务层再次验证资源所有权；用户最多三会话、管理员单会话，改密后可撤销全部会话。安全 Redis 不可用时系统明确失败，不会静默退化到内存认证。

### 3.4 缓存、SQL 与性能优化

**采用技术：** 独立 Redis Cache-Aside、JSON 信封、负缓存、TTL 抖动、JVM 单飞、Redis `SET NX PX` 短锁、generation 失效、MyBatis 批量查询、Flyway V5 复合索引。

**解决的问题：**

- 课程首页、模块树、推荐和公开博客列表反复执行相同 SQL。
- 模块和推荐按条查询，查询次数随模块数量增长。
- 缓存实现若只加注解，无法处理跨实例击穿、负缓存、事务回滚和 Redis 故障。
- 收藏列表缺少符合过滤与排序顺序的复合索引。

**性能证据：** 在固定种子 `20260827`、2,000 用户、1,000 课程、20,000 博客和 200,000 条互动关系的本机受控数据上，热缓存命中率为 100%，目标 SELECT 从 10,995 降为 0，三轮中位数 p95 从 181.599 ms 降为 5.486 ms；冷路径 p95 约退化 2.06%。这是同机对照证据，不代表生产容量。

**运行收益：** 写事务仅在提交后增加 generation，回滚不会误失效；业务 Redis 故障时公开读取回源 MySQL；权限、个人内容、评论和审核列表明确不缓存，避免跨用户数据泄露。

### 3.5 异步事件与可靠性

**采用技术：** MySQL Transactional Outbox/Inbox、AES-256-GCM、RabbitMQ 4.3.5 quorum queue、mandatory publish、Publisher Confirm、手动 ACK、有限退避、retry queue、DLQ、管理员恢复。

**解决的问题：**

- 同步 SMTP 让验证码和审核流程受网络延迟与邮件服务故障影响。
- 仅在事务提交后发布事件仍存在“数据库已提交、进程发布前崩溃”的丢消息窗口。
- RabbitMQ 的 Broker Confirm 不代表消费者处理成功，重复投递也不能靠假设避免。
- 死信若只能进入 Rabbit 管理页，业务管理员无法安全恢复。

**开发与可靠性收益：** 业务数据和 Outbox 在同一 MySQL 事务提交；Dispatcher 使用租约和 `SKIP LOCKED` 多实例领取；Confirm、Return、NACK、超时分别记录受控状态；Inbox 以 event ID/generation 幂等；邮件失败经过三段重试后进入 DEAD，并可由 ADMIN 页面明确重试或忽略。RabbitMQ 停止期间业务仍可受理，恢复后自动补发。

### 3.6 可观测性与性能证据

**采用技术：** Actuator、Micrometer、Prometheus、Grafana、RabbitMQ Prometheus 插件、ECS JSON、MDC/`X-Request-ID`、Gatling Java DSL、Prometheus 告警规则。

**解决的问题：**

- HTTP、MyBatis、Hikari、缓存、安全和消息链路没有统一指标，故障定位依赖人工翻日志。
- 动态 URI、用户 ID 或异常正文若直接作为指标标签，会造成高基数和敏感信息泄露。
- 缓存优化、观测开销和系统容量没有同数据、同硬件的对照方式。
- readiness 若把所有外部依赖都视为强依赖，会在 RabbitMQ 或业务缓存降级时错误摘除仍可工作的实例。

**开发与运维收益：** 请求 ID 从 HTTP 贯穿 Outbox、RabbitMQ 和消费者；日志只记录路由模板与受控枚举；指标标签受白名单和 URI 上限约束；liveness、readiness、dependencies 分别表达进程、强依赖和可降级依赖；20 条告警规则和三个固定 Dashboard 可直接观察 API/JVM、数据库/缓存/安全和消息链路。

**开销证据：** `PublicReadStandard` 三轮中位数在观测关闭/开启时 p95 均为 5 ms，p99 为 7→8 ms，吞吐为 869.98→868.91 req/s，下降约 0.12%，通过既定门禁。详细条件见[方面六报告](../reports/v3/aspect6/README.md)。

### 3.7 容器化、自动化测试与持续交付

**采用技术：** Docker Compose、Testcontainers 1.21.4、MySQL 8.4.11、Redis 7.4.10、RabbitMQ 4.3.5、Mailpit 1.31.0、多阶段 Dockerfile、Compose secrets、GitHub Actions、Trivy、Dependabot、SBOM/provenance/attestation 定义。

**解决的问题：**

- 集成测试依赖开发者本机数据库、Redis 和 RabbitMQ，容易污染数据且难以复现。
- 开发者需要手工拼接多个服务、端口和秘密，环境差异导致“本机可用、他人不可用”。
- 前端旧依赖存在 High/Critical 漏洞，Markdown 输出缺少统一净化边界。
- 构建、审计、OpenAPI、Prometheus、镜像和冒烟验证依赖人工记忆。

**开发与交付收益：** 一条 Compose 命令可启动完整本地环境；Mailpit 默认截获邮件；数据库、两个 Redis、RabbitMQ 和上传文件使用持久卷；秘密只以只读文件挂载；前后端镜像采用非 root、只读根和最小 capability。后端 154 项测试改由单 JVM Testcontainers 提供真实隔离依赖；前端升级后 High/Critical 为 0。质量与严格 SemVer tag-only 发布工作流已定义，第三方 Action 和外部基础镜像均固定不可变引用。

## 4. 对开发效率的直接改善

| 原问题 | 当前做法 | 开发影响 |
| --- | --- | --- |
| 新成员需手工安装并配置多个中间件 | `prepare-local.ps1` + Docker Compose | 统一入口、默认 Mailpit、秘密与数据卷自动隔离 |
| 测试可能误连开发库 | Testcontainers + 动态连接属性 | 测试每轮使用临时资源，失败不会污染开发数据 |
| 改一个模块不知道影响哪些包 | Spring Modulith 依赖验证 | 越界引用在测试阶段直接失败 |
| API 变更只能靠联调发现 | DTO、OpenAPI 快照、契约测试 | 请求/响应、错误状态和漂移自动验证 |
| 数据库变更靠口头同步 | Flyway V1–V7 | 结构、约束、索引与升级顺序进入版本控制 |
| 环境变量散落且容易泄露 | 类型化配置、示例文件、Compose secrets | 缺失配置快速失败，真实值不进入仓库和镜像 |
| 故障只能看零散日志 | Request ID、ECS JSON、Prometheus/Grafana | 从请求到异步消费者可关联，运行状态可查询 |
| 发布前检查依赖人工执行 | GitHub Actions、Trivy、audit、OpenAPI/Compose 门禁 | 质量步骤可重复，发布只允许严格 SemVer 标签触发 |

## 5. 测试与证据增长

| 阶段 | 后端完整测试数 | 主要新增门禁 |
| --- | ---: | --- |
| V2 基线 | 17 | 核心功能流程 |
| 方面一 | 23 | Java/Boot/Jakarta、V2 契约、配置排除 |
| 方面二 | 40 | Modulith、DTO、分页、OpenAPI、Flyway |
| 方面三 | 63 | Security、Session、CSRF、验证码、限流 |
| 方面四 | 80 | 缓存并发/故障、查询数量、V5 索引 |
| 方面五 | 125 | Outbox、Confirm、Inbox、重试/DLQ、恢复 |
| 方面六 | 150 | 请求关联、日志脱敏、指标、健康、管理权限 |
| 方面七 | 154 | Testcontainers、管理员引导与容器交付回归 |

方面七最终还通过了四项前端 Markdown 安全测试、完整和生产依赖 audit、Vite 生产构建、Trivy 源码/镜像扫描、Compose/OpenAPI/Prometheus/Grafana 门禁、容器故障演练和用户浏览器验收。完整证据见[方面七报告](../reports/v3/aspect7/README.md)。

## 6. 兼容性与有意保留的边界

- 用户、课程、博客、评论、收藏、草稿和管理员审核流程保持可用；必要的 HTTP 方法、分页和认证契约变化已同步前端并由测试固定。
- Long ID 继续以字符串传输；业务响应继续使用 `code/data/msg`；博客编辑器上传兼容既有字段。
- V3 选择模块化单体而不是微服务，保留本地事务、较低部署复杂度和清晰模块边界。
- 没有引入 Spring Cloud、Nacos、Seata、Elasticsearch、Kubernetes 或 AI/RAG；当前业务没有足够证据证明这些复杂度是必要的。
- 性能数字只适用于记录的本机硬件、数据和负载，不能用作生产容量承诺。

## 7. 当前发布状态与下一步边界

七个方面的实现提交已经位于本地 `main`：

| 方面 | 提交 |
| --- | --- |
| 一 | `b1b9c1b` `chore: modernize CC4C foundation` |
| 二 | `57d769b` `feat: modularize CC4C and govern API data` |
| 三 | `ca628e1` `feat: secure CC4C authentication` |
| 四 | `bc7dcf8` `feat: add Redis caching and performance gates` |
| 五 | `5daf68c` `feat: add reliable asynchronous messaging` |
| 六 | `f0f6fa1` `feat: add observability and performance evidence` |
| 七 | `a22a329` `chore: containerize and automate CC4C delivery` |

当前远程状态与仍未发生的事项必须继续明确区分：

- 本地 `main` 与 `origin/main` 已同步到 `8f2987267a942655c1059243aaa60cf4bd29748b`。
- GitHub-hosted Actions 质量工作流 [33251873844](https://github.com/Jaily16/CC4C/actions/runs/33251873844) 已通过后端 Testcontainers、前端审计构建、交付配置、Trivy 源码/镜像扫描和 Compose smoke。
- 尚未创建 SemVer 标签，也未发布 GHCR 多架构镜像、SBOM、provenance 或 attestation。
- Compose 是安全的本地开发/验收环境，不等同生产部署；生产仍需 HTTPS、Secure Cookie、关闭公开 API 文档、正式秘密管理、备份和经过审阅的网络策略。
- V4 已进入总体规划阶段，详细范围与执行顺序见 [第四次迭代开发规划](../../development/v4-iteration-plan.md)。

## 8. 证据与运维入口

- [第三次迭代开发规划](iteration-plan.md)
- [项目迭代修改记录](../project-iteration-record.md)
- [方面六观测与性能报告](../reports/v3/aspect6/README.md)
- [方面七交付证据](../reports/v3/aspect7/README.md)
- [容器运行手册](../../operations/container-runbook.md)
- [容器交付架构](../../architecture/container-delivery.md)
- [数据库迁移说明](../../../infrastructure/database/README.md)
- [规范化 OpenAPI 快照](../../reference/openapi.json)

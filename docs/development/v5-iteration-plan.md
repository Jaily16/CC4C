# CC4C 第五次迭代开发规划

> 当前状态：规划已确认，迭代尚未开始。本文只定义后续七个方面的执行顺序、边界和验收要求。

## 1. 当前基线

| 项目 | 固定值 |
| --- | --- |
| GitHub `main` | `d243f6a577120d3dd11206815bea802a1c1a6b42` |
| V4 标签 | `v4.0.0` |
| V4 标签提交 | `ed3c7bb62b4402bd1a4e7aa616955f938cf2aaaf` |
| V4 归档分支 | `archive/v4-final` |
| V5 开发分支 | `v5/restructure` |
| V5 开发版本 | `5.0.0-SNAPSHOT` |
| 当前工作区 | `D:\codex\CC4C_v2` |
| V5 工作区 | 用户已创建且当前为空的 `D:\codex\CC4C_v5` |

`v4.0.0` 标签之后还有一项 V4 文档提交，因此 V4 归档必须以当前 `origin/main` 为准，不能使用标签提交代替。第五次迭代保留现有 Git 历史，最终通过正常合并更新 `main`，禁止使用 orphan 分支或 force push 重建历史。

## 2. 总体目标

第五次迭代不新增学习平台业务功能，目标是：

1. 完整冻结 V4 最终状态并建立独立 V5 开发空间。
2. 在删除性能工具前，统一保留 V3 至 V4 的性能测试方法、过程和结果。
3. 删除 Docker 镜像发布、容器运行、压力测试和自动化测试资产，只迁移生产功能实现。
4. 简化仓库、脚本和配置入口，使项目以本机环境直接运行。
5. 建设独立的中文观测后台，替代现有 Grafana 用户界面并覆盖当前可观测能力。
6. 为后端、业务前端和观测前端的功能函数补充准确中文注释。
7. 精简 GitHub `main` 和 README，使仓库面向希望复现项目的用户，而不是展示迭代过程。

## 3. 全局约束

- 不得在 `D:\codex` 下再创建项目目录；只使用已经存在的 `D:\codex\CC4C_v5`。
- 不使用或操作本机 Docker、Compose、Docker 卷和容器服务。
- 运行环境只依赖本机或外部提供的 MySQL、一个 Redis、RabbitMQ、SMTP 和 Prometheus。
- Session 与业务缓存可以共用一个 Redis 实例，但必须使用不同 namespace。
- 外部中间件只做连通性和权限预检，项目脚本不得自动启动、停止、清空、重置或卸载它们。
- 最终删除单元测试、集成测试、功能测试、Testcontainers、Gatling 和测试工作流，但保留编译、生产构建、lint、格式检查和运行 smoke。
- 项目版本统一为 `5.0.0-SNAPSHOT`；未完成最终验收前不创建 `v5.0.0` 标签。
- 不读取、复制、提交或打印任何本机 `application.yml`、`.env.local`、`.env.*.local`、`deploy/secrets/local`、数据库内容、上传文件、Cookie、Token、Docker 数据或备份内容。
- 默认保持 HTTP API、DTO、Flyway V1–V7、RabbitMQ `*.v1` 事件、Cookie、CSRF、上传 URL 和数据库语义不变；如确需修改，必须在对应方面单独列出兼容方案并获得确认。
- 当前未跟踪的本地清理计划 `docs/superpowers/plans/2026-09-02-cc4c-local-artifact-cleanup.md` 不属于 GitHub `main`，不得迁入 V5、暂存或删除。

## 4. 七个方面及执行顺序

### 方面一：冻结 V4 并建立 V5 分支及工作区

目标是先保存不可变的 V4 状态，再建立完全隔离的 V5 开发入口。

- 重新确认 `origin/main` 仍为固定基线，暂存区为空，并核对本地未跟踪文件。
- 从固定基线创建并推送 `archive/v4-final`，作为不可变 V4 完整归档。
- 从同一提交创建并推送 `v5/restructure`，作为第五次迭代开发分支。
- 分支不存在时才创建；若已存在且指向固定基线则复用，若指向其他提交则立即停止，禁止覆盖或强推。
- 不在 `CC4C_v2` 中切换开发分支；在已存在且为空的 `D:\codex\CC4C_v5` 中初始化 Git、配置 `origin`、获取并跟踪 `v5/restructure`。
- 比较原 `main`、归档分支和 V5 初始分支的提交 ID、tree hash、tracked 文件数和标签状态。
- 三者一致后，将本文安全迁入 V5 工作区，作为 V5 分支的首个独立文档提交。
- 本方面不得修改源码、版本、README、配置、Docker、测试或性能文件。

### 方面二：汇总 V3 至 V4 的性能测试证据

创建 `docs/reports/v4/performance-testing.md`，在删除性能实现前保存完整、可追溯的技术记录。

文档必须覆盖：

- 缓存基准：数据生成、冷路径、热路径、缓存命中率、MyBatis SELECT 数量和 p95/p99。
- Gatling 场景：`PublicReadSmoke`、`PublicReadStandard`、`AuthenticatedMixed` 和 `StepCapacity`。
- 观测开销：关闭和开启 Micrometer、结构化日志、Prometheus 抓取时的吞吐和延迟对照。
- 容器性能：V3 容器结果、V4 本地三轮结果及 GitHub Actions release 三轮结果。
- 操作系统、CPU、内存、Java、Maven、Gatling、数据库、Redis、RabbitMQ 和固定数据种子。
- 用户、课程、博客、评论、收藏和回复的数据规模，以及预热、测量、并发模型和门禁阈值。
- 精确历史命令、逐轮数据、中位数、失败原因、修复过程和最终结论。
- 明确标记哪些数据是 V3 原始实验、哪些在 V4 重跑、哪些只是历史引用。
- 说明结果仅代表受控环境，不作为生产容量承诺。

只允许引用 Git 跟踪文档、源码定义和公开 GitHub Actions 结果；不得读取或提交 `temp` 原始数据、本机性能环境文件、凭据或数据库内容。

### 方面三：迁移纯功能代码并移除 Docker、性能和测试资产

在 `D:\codex\CC4C_v5` 的 `v5/restructure` 分支原地精简。V5 工作区本身就是迁移目标，不再建立嵌套项目目录。

删除范围：

- Docker 与镜像发布：Compose 文件、Dockerfile、容器入口、GHCR release、镜像标签、镜像扫描和 Docker Dependabot 配置。
- 性能与压力测试：Gatling 源码、性能 Maven profile、性能依赖、性能 Netty 覆盖、性能脚本和性能环境模板。
- 自动化测试：后端 `src/test`、前端 `tests`、Testcontainers、测试资源、测试脚本、测试控制器和测试专用开关。
- 仅为 Docker、测试、性能或历史兼容服务的重复目录和入口。

保留范围：

- 后端生产源码、前端生产源码、静态资源和生产依赖。
- Flyway V1–V7、OpenAPI 文档和三个已发布 `*.v1` 消息事件。
- MySQL、Redis、RabbitMQ、SMTP 所需业务实现。
- Actuator、Micrometer、健康检查、请求关联和业务指标实现。
- Maven 编译打包、三端生产构建、ESLint、Prettier、Spotless 和 Enforcer。
- 方面二形成的性能文档和 V4 功能验证记录。

迁移完成后仅使用本机环境执行一次构建和业务 smoke：注册、登录、课程、博客、收藏、评论、审核、消息管理、邮件和上传。不得启动 Docker，不得自动管理外部中间件。

### 方面四：简化仓库结构和三端配置入口

目标仓库结构：

```text
CC4C_v5/
├─ backend/
├─ frontend/
├─ observability/
├─ infrastructure/
│  ├─ database/
│  ├─ rabbitmq/
│  └─ prometheus/
├─ docs/
│  ├─ architecture/
│  ├─ operations/
│  ├─ reference/
│  └─ reports/v4/
├─ .github/workflows/build.yml
├─ .editorconfig
├─ .gitignore
├─ versions.yml
└─ README.md
```

结构规则：

- 删除根级 `scripts`、`deploy`、`database`、旧 `observability` 和其他兼容目录。
- 必须保留的本机运行脚本移动到对应应用或 `infrastructure`，并只管理自身启动的精确 PID。
- 历史 SQL 移入 `infrastructure/database/legacy`。
- `versions.yml` 删除 Docker、测试和性能字段，只保留三端版本、工具链及必要中间件版本。
- 后端、业务前端和观测前端各只保留一个用户可编辑的脱敏环境模板：
  - `backend/.env.example`
  - `frontend/.env.example`
  - `observability/.env.example`
- 三端实际本机配置统一使用各自目录中的 `.env.local` 并保持 Git 忽略。
- Maven、package、Vite、Spring `application.yml` 和 Prometheus 配置属于内部构建或框架配置，不计入环境模板数量。
- 后端跟踪的 `application.yml` 只能由受控脱敏模板生成，包含环境变量占位符和安全默认值，禁止复制旧本机配置。
- 删除第二个物理 Redis 的运行要求，Session 和业务缓存改为同一 Redis 地址、不同 namespace。

### 方面五：建设独立中文观测后台

建立独立的 `observability` Vue 3 应用：

- 使用 Vue 3、Vite、Element Plus、Axios、Pinia 和 Apache ECharts。
- 全部用户界面使用中文，并复用业务管理端的颜色、字体、圆角、阴影、导航和响应式布局。
- 使用单文件组件、懒加载路由、按领域拆分的 store 和 composable。
- 禁止使用 `v-html` 渲染不可信内容。
- Prometheus继续作为指标、时序数据和告警规则后端；ECharts 替代 Grafana 负责图表展示。
- 删除 Grafana 和 Mailpit；邮件改由用户提供的本地或外部 SMTP 服务处理。

观测页面必须覆盖现有三个 Grafana Dashboard 的 20 个面板和 Prometheus 的 20 条告警：

1. 系统总览。
2. API、HTTP 延迟、状态码、JVM、GC、CPU、线程和 Tomcat。
3. MyBatis、Hikari、Redis、缓存命中、缓存旁路、安全认证、授权拒绝和限流。
4. Outbox、Inbox、RabbitMQ、发布、消费、重试、死信、重复、过期和采样状态。
5. 告警列表、规则状态、依赖健康、liveness、readiness 和请求关联信息。

新增后端接口：

```text
GET  /observability/auth/csrf
POST /observability/auth/login
GET  /observability/auth/session
POST /observability/auth/logout

GET  /observability/api/overview
GET  /observability/api/dashboard/{dashboardId}
GET  /observability/api/alerts
GET  /observability/api/dependencies
```

接口和安全规则：

- `dashboardId` 只允许固定枚举，不开放任意 PromQL 代理。
- 时间范围、步长和查询数量必须设置固定上限。
- Prometheus 地址和凭据只存在后端配置，不发送给浏览器。
- 使用独立 `OBSERVABILITY` 身份，不复用 USER 或 ADMIN。
- Cookie 固定为 `CC4C_OBSERVABILITY_SESSION`，使用 HttpOnly、SameSite 和可配置 Secure。
- 随机会话令牌只以哈希形式存入 Redis 独立 namespace。
- 登录凭据使用用户名和 bcrypt 密码哈希，不保存或记录明文密码。
- 登录限流、CSRF、Origin 校验、退出失效和会话过期必须完整处理。
- Prometheus不可用、数据为空、会话过期和依赖降级必须显示中文状态，不得泄露内部凭据或原始异常正文。

### 方面六：补齐三端中文功能注释

注释范围：

- 后端全部生产类、接口、record、构造器和功能方法。
- 业务前端与观测前端全部组件、API wrapper、composable、store action 和功能函数。
- 本机运行脚本的前置条件、外部依赖、失败恢复和退出码。

注释要求：

- Java 使用中文 Javadoc，说明用途、参数、返回值、异常、事务、安全、幂等和副作用。
- JavaScript/Vue 使用中文 JSDoc 或紧邻函数的中文功能说明。
- 每个功能函数必须有注释，生命周期绑定也要说明业务目的。
- 注释解释设计原因和边界，不能逐行翻译代码。
- 不格式化或改写 Flyway SQL、OpenAPI JSON、锁文件、第三方生成文件和 V4 原始报告。
- 完成后执行 Maven 编译、三端 lint、格式检查和生产构建。

### 方面七：收口 GitHub main 与精简 README

GitHub Actions 只保留不使用 Docker、也不执行测试的构建工作流：

- 后端 Maven 编译和生产打包。
- 业务前端 `npm ci`、lint、format check 和 build。
- 观测前端 `npm ci`、lint、format check 和 build。
- Dependabot 只保留 Maven 和两个 npm 应用。
- 不发布镜像，不执行 Testcontainers、Gatling、Trivy、Compose 或性能任务。

README 只保留：

- 项目用途和核心功能。
- 前端、后端和观测端架构及技术栈。
- Java、Maven、Node、npm、MySQL、Redis、RabbitMQ、SMTP 和 Prometheus 要求。
- 三份 `.env.example` 的复制和填写方式。
- 数据库初始化、构建、启动、停止、健康检查和观测后台访问。
- 安全说明和常见启动问题。

README 不得出现 V1–V5 迭代过程、方面编号、开发日志、历史性能数字、Docker、Compose、GHCR 或镜像发布说明。

最终门禁通过后，`v5/restructure` 通过正常合并更新 `main`；不得改写历史。`archive/v4-final` 永久保持指向 V4 最终基线。推送 `main`、创建 `v5.0.0` 标签或发布正式版本仍需单独授权。

## 5. 验证门禁

### 分支与工作区

- `archive/v4-final`、V5 初始分支和原 `main` 的 tree hash 完全一致。
- `D:\codex\CC4C_v2` 的本机忽略文件、秘密和清理计划不进入 V5。
- 不在 `D:\codex` 创建第三个项目目录。

### 删除与结构

- 最终仓库不包含 Dockerfile、Compose、GHCR、Gatling、Testcontainers、测试源码和测试工作流引用。
- 根目录只保留三端应用、基础设施、文档和必要元文件。
- 三端各只有一个环境模板，实际本机配置全部保持忽略。

### 构建和业务运行

- 后端 Maven 编译打包通过。
- 业务前端和观测前端 lint、格式检查和生产构建通过。
- 本机 MySQL、Redis、RabbitMQ、SMTP 和 Prometheus 预检通过。
- 注册、登录、课程、博客、收藏、评论、审核、异步消息、邮件和上传 smoke 通过。

### 观测后台

- 原有 20 个面板能力和 20 条告警均有中文入口。
- 未登录、错误密码、登录限流、CSRF、会话过期和退出流程符合预期。
- Prometheus故障、无数据和依赖降级具有明确中文提示。
- 前端不得获得 Prometheus 凭据、任意 PromQL 能力或内部异常正文。

### 兼容性

- Flyway V1–V7、OpenAPI、三个已发布事件名及业务 URL 保持不变。
- Cookie、CSRF、上传路径、Redis namespace 和 RabbitMQ namespace 的变化均必须显式记录并验证迁移。
- 旧 V4 状态始终可从 `archive/v4-final` 获取。

## 6. 主要风险

- 过早删除测试资产会降低后续重构安全网，因此每方面必须执行编译、lint、构建和本机功能 smoke。
- V4 标签落后当前 `main` 一个文档提交，归档错误会遗漏最终验证记录。
- 将两个 Redis 合并为一个实例时若 namespace 配置错误，会造成 Session 与业务缓存冲突。
- 删除 Docker 后，环境一致性依赖本机中间件版本和用户配置，必须加强版本、端口和权限预检。
- 自定义观测门户替代 Grafana 时容易遗漏面板、告警或查询语义，必须建立逐项 20 对 20 映射。
- 独立观测会话若与业务 Session 混用，可能破坏权限隔离，必须使用独立 Cookie 和 Redis namespace。
- 全量函数注释可能产生无意义翻译式注释，审查必须关注约束、安全原因和副作用。
- 精简 README 时不能误删复现项目所需的数据库、消息、邮件和 Prometheus 配置说明。

## 7. 安全规则

- 禁止 `git push --force`、orphan main、`git reset --hard`、`git clean` 和未经核对的分支删除。
- 禁止读取或复制旧工作区的本机配置、秘密、上传数据、数据库、Docker 卷和备份。
- 禁止使用 `docker compose down -v`、Docker volume prune、Redis FLUSH、RabbitMQ purge 和 Flyway clean/repair。
- 禁止按进程名批量停止 Java、Node、Prometheus、MySQL、Redis 或 RabbitMQ。
- 本机运行脚本只能停止自己记录且已验证身份的精确 PID。
- 删除文件前必须以 Git tracked 清单和引用扫描生成精确 allowlist，禁止使用广泛通配符清理。
- 每个方面必须独立提交；未通过本方面门禁时不得开始下一方面。
- 每次推送、合并 `main`、创建标签或发布版本都需要用户单独授权。

## 8. 新对话提示词

### 8.1 提示词一：接手 V5 并只读理解

```text
你正在接手 CC4C 项目的第五次迭代。请先只读并完整理解：

1. D:\codex\CC4C_v2\docs\development\v5-iteration-plan.md
2. README.md
3. docs/development/v4-iteration-plan.md
4. docs/development/v4-validation-report.md
5. docs/history/reports/v3/aspect6/performance.md
6. docs/history/reports/v3/aspect7/performance.md
7. 当前 backend、frontend、infrastructure、observability、scripts、.github、versions.yml、pom.xml 和 package.json/package-lock.json
8. 当前 Git 分支、标签、跟踪、忽略和未跟踪状态
9. 已存在且为空的 D:\codex\CC4C_v5 目录状态

V5 唯一规划基线为 GitHub origin/main：

d243f6a577120d3dd11206815bea802a1c1a6b42

当前 v4.0.0 标签指向：

ed3c7bb62b4402bd1a4e7aa616955f938cf2aaaf

标签之后还有一项 V4 文档提交，因此归档必须以当前 origin/main 为准，不能用 v4.0.0 标签提交替代。

V5 固定决策：

- V4 归档分支：archive/v4-final
- V5 开发分支：v5/restructure
- 使用用户已经创建的 D:\codex\CC4C_v5，不得在 D:\codex 新建其他目录
- 保留 Git 历史，最终正常合并 main，禁止 force push 和 orphan main
- 开发版本：5.0.0-SNAPSHOT
- 删除 Docker、Compose、GHCR、性能压力测试和全部自动化测试资产
- 保留 Maven 编译、三端生产构建、lint、格式检查和运行 smoke
- 运行环境只使用本机 MySQL、一个 Redis、RabbitMQ、SMTP 和 Prometheus
- 不使用或操作本地 Docker 服务
- 独立中文观测后台采用 Vue 3、Element Plus、Pinia、ECharts 和 Prometheus
- 观测身份使用独立 HttpOnly Session，不复用 USER 或 ADMIN
- 前端、后端、观测端各只保留一份 .env.example
- README 最终只保留项目介绍、技术架构和复现必需内容，不披露迭代过程

七个方面固定顺序：

1. 冻结 V4 并建立 V5 分支及工作区
2. 汇总 V3–V4 全部性能测试方法、过程和结果
3. 迁移纯功能代码并移除 Docker、性能和测试资产
4. 简化仓库结构和三端配置入口
5. 建设独立中文观测后台
6. 为三端所有功能函数补充详细中文注释
7. 精简 GitHub main、构建工作流和 README

本条消息只允许只读检查。请回复：

- 当前工程和 Git 状态
- 七方面的依赖顺序
- 第一方面的精确范围
- 必须保留的业务、数据和协议资产
- 可删除资产分类
- 主要风险
- 无 Docker 验证门禁
- 分支、秘密和本地数据安全规则

不要修改或删除文件，不要创建、切换或推送分支，不要初始化 CC4C_v5，不要暂存、提交或创建标签，不要运行测试、构建、安装或服务。

不得读取本机 application.yml、任何 .env.local/.env.*.local、deploy/secrets/local、数据库内容、上传文件、Docker 卷、Cookie、Token 或历史备份内容。等待我的下一条指令。
```

### 8.2 提示词二：规划第一方面

```text
现在进入计划模式，为 docs/development/v5-iteration-plan.md 中第一个方面“冻结 V4 并建立 V5 分支及工作区”制定可直接执行的详细实施计划。

请先只读检查：

- D:\codex\CC4C_v2 当前 HEAD、origin/main、Git 状态和暂存区
- origin/main 是否仍为 d243f6a577120d3dd11206815bea802a1c1a6b42
- v4.0.0 标签及其指向
- archive/v4-final 和 v5/restructure 在本地与远端是否已经存在
- origin URL 和只读网络连通性
- D:\codex\CC4C_v5 是否仍已存在、为空、不是 reparse point 且尚未初始化 Git
- 当前本地未跟踪规划文件：
  - docs/development/v5-iteration-plan.md
  - docs/superpowers/plans/2026-09-02-cc4c-local-artifact-cleanup.md

计划必须锁定以下结果：

1. archive/v4-final 精确指向当前 origin/main，作为不可变 V4 完整归档。
2. v5/restructure 从同一提交创建，作为第五次迭代开发分支。
3. 两个远端分支不存在时才创建；若存在且提交一致则复用，提交不一致则停止。
4. 禁止 force push、禁止删除或覆盖现有远端分支、禁止改写 main 历史。
5. 使用现有空目录 D:\codex\CC4C_v5 初始化 Git并跟踪 origin/v5/restructure，不得在 D:\codex 创建其他目录。
6. 不复制 D:\codex\CC4C_v2 的 .git、本机配置、秘密、构建产物、上传文件或忽略目录。
7. 本地清理计划不得暂存、提交、删除或迁入 V5。
8. V5 规划文档在三个分支 tree hash 验证完成后，才允许迁入 D:\codex\CC4C_v5\docs\development\v5-iteration-plan.md，作为 V5 分支的首个独立文档提交。
9. 完成后比较 main、archive/v4-final 和 V5 初始提交的提交 ID、tree hash、tracked 文件数和标签状态。
10. 方面一不得提前删除 Docker、测试或性能资产，不得修改业务源码、版本、README 或配置。

计划需要给出：

- 每条 Git 命令及执行目录
- 远端分支创建顺序
- 已存在分支的碰撞处理
- 网络中断和部分推送成功时的停止规则
- D:\codex\CC4C_v5 初始化和跟踪分支步骤
- V5 规划文档的安全迁移与提交步骤
- 精确验证命令和验收输出
- 不覆盖用户文件的失败恢复方式
- 方面一允许与禁止修改的清单
- 用户最终验收信息

当前仍为计划模式：不要修改文件，不要初始化 Git，不要创建、删除或切换分支，不要暂存、提交、推送或创建标签，不要运行测试、构建、安装或服务。

不得读取本机 application.yml、任何 .env.local/.env.*.local、deploy/secrets/local、数据库、上传目录、Docker 卷或历史备份内容。计划完成后等待我确认。
```

## 9. 启动条件

只有在新对话中先使用提示词一完成只读接手，再使用提示词二形成并确认方面一实施计划后，才允许开始第五次迭代。当前规划文档落盘不代表 V5 已启动。

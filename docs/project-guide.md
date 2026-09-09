# CC4C 项目指南

本指南说明当前 `6.0.0-SNAPSHOT` 的实现和维护方式。项目是一个 Spring Boot 后端、一个业务 Vue 应用和一个独立观测 Vue 应用；各端独立构建，共用环境辅助，不拆成微服务。当前版本以 [versions.yml](../versions.yml) 为准，历史结果与剩余工作见[迭代总结](iteration-summary.md)，历史实验见[性能证据](performance-history.md)。

- [项目与目录](#项目与目录)
- [业务契约与安全](#业务契约与安全)
- [配置与本机运行](#配置与本机运行)
- [数据库维护](#数据库维护)
- [异步消息维护](#异步消息维护)
- [观测架构](#观测架构)
- [代码质量](#代码质量)
- [资料与来源](#资料与来源)

## 项目与目录

```text
CC4C/
├─ backend/             Java 生产源码、资源、辅助程序及 OpenAPI 快照
├─ frontend/            业务 Vue 应用、Vuex；screenshots 仅存文档截图
├─ observability/       独立观测 Vue 应用、Pinia、ECharts
├─ infrastructure/      database、host、prometheus、quality、rabbitmq
├─ .github/             构建工作流与 Dependabot
├─ docs/                项目指南、迭代总结、历史性能三份文档
├─ README.md
├─ versions.yml
├─ .editorconfig
└─ .gitignore
```

该树表示受控职责，不等于磁盘只允许这些目录。`.git`、依赖、构建产物、运行资料和秘密目录原地保留并按忽略规则管理；没有 tracked 文件不能证明目录为空。旧顶层目录候选的安全边界见迭代总结。

后端按技术层命名，业务含义由类名表达；五个 Mapper 直接承担 DAO，两个 JDBC Repository 保持同层。只在 support 下保留 cache、messaging、monitoring 三个技术子包，不增加机械的 ServiceImpl、Dao 或重复 Mapper 包装。

### 后端

#### 当前目录

下列目录位于 `backend/src/main/java`：

```text
com/cc4c/
├── CC4CApplication.java
├── controller/              # HTTP 接口
├── service/                 # 业务服务、接口和转换辅助类
├── mapper/                  # MyBatis-Plus 数据访问，即 DAO
├── repository/              # 已有 JDBC 数据访问
├── entity/                  # 持久化实体
├── dto/                     # 请求、响应、快照、查询结果
├── config/                  # 配置类和配置属性
├── common/                  # 公共异常、校验、常量和工具
├── security/                # 认证、会话、安全过滤器与处理器
└── support/                 # 外部服务和技术支撑
    ├── cache/               # 缓存存取、键规则和编解码
    ├── messaging/           # 消息、事件、消费者和发布器
    ├── monitoring/          # 指标、健康检查和 Prometheus
    ├── FileStorage.java
    └── OutboundMailSender.java
```

只有缓存、消息和监控保留技术子包，其余包中的类直接平铺。例如：`controller/CatalogController.java`、`mapper/UserMapper.java`、`dto/CatalogDtos.java`。

辅助程序继续位于独立 `com.cc4ctools` 根包；后端 `scripts`、POM、环境模板和原有必要资源保持用途。资源仍保留 `application.yml`、`db/migration`、`observability/catalog.json`；只有实际使用 XML SQL 时才建立 `resources/mapper`。

#### 十个包的职责

| 包 | 放什么 | 边界 |
| --- | --- | --- |
| `controller` | HTTP 参数接收、校验、服务调用和响应 | 不直接访问 Mapper、Repository 或编写 SQL |
| `service` | 业务规则、事务、登录与查询用例、已有服务接口、DTO 转换辅助类 | 转换仍由独立辅助类负责，不混入数据库操作 |
| `mapper` | 现有 MyBatis-Plus 接口与 SQL 声明 | 直接承担 DAO 职责，不手写 MapperImpl |
| `repository` | 现有 InboxRepository、OutboxRepository 等 JDBC 实现 | 与 Mapper 同层，不转发包装 Mapper |
| `entity` | 对应数据库持久化结构的实体或记录 | 不直接作为公开 HTTP 响应 |
| `dto` | 请求、响应、快照、查询 Row、ApiResponse 和分页结构 | 用类名及已有嵌套类型区分用途，不合并不同协议模型 |
| `config` | Spring 装配、安全链配置及所有 Properties | 配置类与属性类放在一起，真实配置值继续外置 |
| `common` | 公共异常、GlobalExceptionHandler、错误枚举、通用注解、校验器和无状态工具 | 不接收数据库访问、缓存、邮件、文件操作或业务服务 |
| `security` | 身份对象、认证器、Session/CSRF 安全实现、限流、安全 Filter 和 Handler | 登录用例在 service；业务身份与观测身份继续隔离 |
| `support` | 缓存、消息、监控和现有文件、邮件适配器 | 具体业务规则仍在 service，支撑能力不依赖 Controller |

公共错误码如 `BusinessCode` 放入 `common`；角色枚举如 `AccountRole` 放入 `security`；消息状态如 `OutboxStatus` 放入 `support/messaging`。不为少量枚举分别建立 `constants/enums` 子目录。

#### 目录合并表

下表保留方面二已落实的合并决策；左列属于历史设计，右列是当前位置：

| 上一版目录或内容 | 精简后位置 |
| --- | --- |
| `config/properties` | `config` |
| `dto/request`、`dto/response`、`dto/query` | `dto`，保留原模型及字段 |
| `converter` | `service` 中独立的转换辅助类 |
| `common/response` | `dto` |
| 公共异常、全局异常处理器、通用注解、校验器、常量和工具 | `common` |
| 安全 Filter、CSRF Handler、身份对象及安全原语 | `security` |
| 请求关联 Filter、MyBatis 指标拦截器 | `support/monitoring` |
| `cache` | `support/cache` |
| `event`、`messaging` 及消费者、发布器、模型子目录 | `support/messaging`，直接平铺 |
| `monitoring/metrics`、`monitoring/health`、`monitoring/prometheus` | `support/monitoring`，直接平铺 |
| `storage`、`mail` | 现有单文件适配器直接放入 `support` |

`support` 按技术依赖归类：外部 Prometheus 查询和健康指标属于 monitoring，缓存存取属于 cache，可靠消息和事件载荷属于 messaging。各类仍保留明确的名称和独立职责。

#### 调用关系和安全边界

```text
Controller → Service → Mapper（DAO）→ 数据库
                   └→ Repository   → 数据库
```

MyBatis 创建 Mapper 的代理实现；`UserMapper extends BaseMapper<UserEntity>` 已是 DAO 层。已有 JDBC 代码继续使用 Repository，不改写访问方式，也不额外增加 `Dao → Mapper` 转发层。

Service 按用例需要调用缓存、邮件、文件和消息设施；消息消费者可以调用 Service 执行业务。已有 Lookup、UseCase 接口保留，不为每个具体服务机械增加接口与 Impl。

目录合并不改变框架机制：认证 Filter 仍在 Spring Security 过滤链中；MVC Interceptor、MyBatis Interceptor 和 AOP Aspect 仍是不同机制，不互相替换。当前没有的切面不预建目录。

业务与观测的认证、Cookie、CSRF、Session 和 namespace 继续分别维护；USER、ADMIN、OBSERVABILITY 的权限语义保持一致。管理端口抓取认证仍独立于观测门户，辅助程序不纳入主应用组件扫描。

### 业务前端

当前保留 10 个常用基础目录：

```text
frontend/src/
├── App.vue
├── main.js
├── api/                     # 7 个请求模块直接平铺
├── assets/                  # 沿用已有静态资源分类
├── components/              # 共享组件及其配套子组件
├── composables/             # 3 个 composable 直接平铺
├── layout/
│   ├── index.vue
│   ├── Header.vue
│   └── Sidebar.vue
├── router/
│   └── index.js
├── store/
│   ├── index.js
│   ├── getters.js
│   └── modules/
│       └── user.js
├── styles/
├── utils/
└── views/
    ├── HomeView.vue
    ├── UserInfoView.vue
    ├── FavoriteView.vue
    ├── login/
    ├── course/
    ├── blog/
    └── admin/
        └── components/
            ├── CourseBasicsForm.vue
            └── CourseModuleEditor.vue
```

页面分类与后端业务模块无关。多页面的 login、course、blog、admin 保留目录；单独的首页、个人资料和收藏页面直接平铺。

具体放置规则：

- `api` 保留 client、auth、catalog、community、interactions、messaging、profile 七个文件；HTTP 配置和凭据处理集中在 client。
- `composables` 保留 useCommentThread、useCurrentUser、useVerificationCode；撤销预设的 auth、community、verification 子目录。
- `components` 直接放置 ContentActionBar、MarkdownPreview、PageFeedback、UserInfo、CommentThread、ProfileEditDialog、PasswordChangeDialog。
- UserInfo、CommentThread 被多个页面复用；ProfileEditDialog、PasswordChangeDialog 是共享 UserInfo 的配套组件，继续一起保留在 components。
- CourseBasicsForm、CourseModuleEditor 仅由 AddCourseView 使用，位于 `views/admin/components`。
- 布局组件平铺到 `layout`；路由仍集中在 `router/index.js`，不预设 routes/public/user/admin 文件。
- Vuex 保留熟悉的 `store/modules` 约定；管理页面直接放在 admin，不再预分 content、users、messaging 子目录。

路由使用懒加载。页面状态尽量留在组件或 composable，确有跨页面共享需求才进入 Store；页面和组件不重复创建 Axios 实例。

### 观测前端

现有应用仅有 5 个公共组件、6 个页面，直接平铺更容易定位：

```text
observability/src/
├── App.vue
├── main.js
├── api/
│   ├── client.js
│   ├── auth.js
│   └── observability.js
├── components/
│   ├── ChartPanel.vue
│   ├── DashboardPage.vue
│   ├── MetricCard.vue
│   ├── StatusBadge.vue
│   └── TimeRangeToolbar.vue
├── composables/
│   └── usePolling.js
├── layout/
│   └── ObservabilityLayout.vue
├── router/
│   └── index.js
├── stores/
│   ├── auth.js
│   ├── overview.js
│   ├── dashboard.js
│   └── operations.js
├── styles/
└── views/
    ├── LoginView.vue
    ├── OverviewView.vue
    ├── ApiJvmView.vue
    ├── DataCacheSecurityView.vue
    ├── MessagingView.vue
    └── OperationsView.vue
```

观测端原有平铺结构符合设计，方面二没有进行机械迁移。三个 API 文件继续保留，数据接口放在 observability.js，不提前拆成 overview/dashboard/operations 请求文件。

继续使用 4 个 Pinia Store 和 usePolling。ChartPanel 保持图表生命周期、ResizeObserver、释放资源和文本替代职责；当前不要求抽出新的图表 composable，也不预建 assets 或 utils。

浏览器只访问后端固定观测接口，不直接访问 Prometheus，不保存 Session Token 或拼接 PromQL。轮询、隐藏暂停、中止过期请求和安全退出行为保持原有约定。

## 业务契约与安全

### 请求、响应与数据流

Controller 接收和校验 HTTP 参数，Service 协调业务规则、事务与副作用，Mapper/Repository 执行数据库访问。普通响应保留 `code/data/msg`，Long ID 以字符串传输；请求与响应使用独立 DTO，服务端生成的身份、状态和时间不能由请求覆盖。分页保留 `items/page/size/total/totalPages/hasNext/hasPrevious`。

课程与博客列表使用数据库分页；评论批量装配用户和两级回复。博客提交在同一事务中清除当前用户草稿，验证或维护时不能覆盖历史草稿。正常博客详情读取可能增加阅读量，不能将页面读取一概视为无数据副作用。

前端统一 API Client 负责凭据、CSRF 和错误处理；页面、composable 和 Store 不各自创建客户端。Vuex/Pinia 保存展示状态，服务端 Session 才是身份权威。路由守卫帮助导航，不能替代服务层授权。Markdown 输出使用既有净化逻辑。

### 三种身份与受限兼容

业务 USER 与 ADMIN 共用业务身份体系及 `CC4C_SESSION`，同一浏览器正常退出和重新登录完成角色切换，不把两个标签页当作独立身份。业务写请求使用 `XSRF-TOKEN` 与 `X-XSRF-TOKEN`，角色与资源所有权均在服务端验证；密码使用 BCrypt，改密及重置密码撤销对应账号会话。安全 Redis 故障不降级为内存认证。

观测门户独立使用 OBSERVABILITY 账户、Cookie、CSRF 与 Redis namespace；Prometheus 管理抓取另用 Basic 身份和另一份密码。业务身份、门户身份、管理抓取权限不可互换，详见[观测架构](#观测架构)。

`SessionJsonRedisSerializer` 保留 Bean 名 `springSessionDefaultRedisSerializer`。只将以下两个旧完整类名映射到新 security 包同名类型：

- `com.cc4c.identity.api.Cc4cPrincipal`
- `com.cc4c.identity.internal.Cc4cSessionAuthenticationToken`

读取根对象与嵌套对象都经过受限映射器，检查基类型兼容性及类型许可；普通字符串不替换，不保留旧包壳类，不放行整个应用包。保留既有 JDK/Spring 类型范围，应用身份采用精确许可。新写入使用新完整类名，兼容方向仅为 **V6 读取 V5**，不承诺 V5 读取新会话，不自动清理 Redis 或回退。

方面二通过 64 项合成 Session/Mapper 校验；方面三验证了 V6 Maven→JAR 的真实会话恢复。真实 V5 会话未验证，不能由这两项证据替代。

### 缓存、消息与指标

公开课程和已审核博客使用 Cache-Aside；私有内容、权限结果、草稿、审核队列、评论与回复不缓存。缓存采用显式 JavaType 和 JSON 信封，namespace、region generation 与参数摘要参与键规则，保留负缓存、TTL 抖动、单飞、短锁与故障旁路。写事务提交后才使对应 generation 失效；回滚不触发失效。公开缓存故障可回源数据库，身份依赖仍安全失败。

邮件与审核通知由业务事务写入 Outbox，发布与消费者处理见[异步消息维护](#异步消息维护)。既有三个 `*.v1` 事件、Flyway V1–V7、HTTP/DTO、上传 URL 不因目录整理而更改。

MyBatis 从 statement ID 提取完整 Mapper 名，保持既有 module 标签：

| Mapper 完整名称 | module |
| --- | --- |
| com.cc4c.mapper.UserMapper | identity |
| com.cc4c.mapper.AdministratorMapper | identity |
| com.cc4c.mapper.CatalogMapper | catalog |
| com.cc4c.mapper.BlogMapper | community |
| com.cc4c.mapper.InteractionMapper | interaction |
| 其他名称 | shared |

拦截器只计时最外层 Executor；不把 SQL、参数、statement ID 或动态用户数据变成指标标签。应用包重组不改变指标业务含义。

## 配置与本机运行

### 首次依赖准备与生产构建

以下是新环境的操作说明，本次文档整理没有执行这些命令。先准备 [versions.yml](../versions.yml) 中的工具链，确认当前终端使用 PowerShell 7 和 Java 21；原有环境直接复用，不重复安装或引导账户。

首次缺少依赖时，维护者在允许访问依赖仓库的环境按锁文件安装。下列每段在标明的应用目录执行，任一步非零即停止：

```powershell
# backend 目录：首次构建可下载固定依赖。
mvn -B -ntp clean package -DskipTests

# frontend 目录。
npm ci --ignore-scripts --no-audit --no-fund
npm run lint
npm run format:check
npm run build

# observability 目录。
npm ci --ignore-scripts --no-audit --no-fund
npm run lint
npm run format:check
npm run build
```

已有完整 Maven 缓存的隔离验证使用 `mvn -o -B -ntp clean package -DskipTests`；离线缺失依赖即停止，不能把去掉 `-o` 当作自动修复。本指南中的安装示例不构成当前协作任务的安装授权。

后端生成 `cc4c-6.0.0-SNAPSHOT.jar`、`cc4c-6.0.0-SNAPSHOT-admin-bootstrap.jar`、`cc4c-6.0.0-SNAPSHOT-observability-password.jar`。两端产物分别为 `frontend/dist`、`observability/dist`。构建不连接业务数据库；后端正常启动会执行 Flyway 校验及必要迁移。

Maven 只过滤受控配置的项目版本标记，Spring 环境占位符、Flyway 和 catalog 不重写。后端受控配置进入 JAR，不得与被禁止读取的本机私有配置混淆。

### 初次准备三份配置

只在对应 `.env.local` 不存在时，由维护者根据同目录 `.env.example` 创建并手工填写。已有文件原地保留，禁止覆盖。下例从仓库根目录执行，复制不会覆盖现有文件：

```powershell
foreach ($app in @('backend', 'frontend', 'observability')) {
    $template = Join-Path (Get-Location).Path "$app/.env.example"
    $local = Join-Path (Get-Location).Path "$app/.env.local"
    if (Test-Path -LiteralPath $local) { throw "Configuration already exists: $app" }
    [System.IO.File]::Copy($template, $local, $false)
}
git check-ignore -- backend/.env.local frontend/.env.local observability/.env.local
```

先由数据库管理员准备专用数据库及最小权限账户，另行准备原有 Redis、RabbitMQ、SMTP 和外部 Prometheus。密码文件、哈希、消息密钥及私有 Prometheus 配置只保留在仓库外；两个前端只接受公开 API 地址。新环境管理员引导与密码工具见下文，均不是已有环境的重复启动步骤。

### 适用范围与安全边界

本机入口只管理当前工作区的后端 JAR、业务前端和独立观测前端，启动顺序为后端、业务前端、观测前端，停止顺序相反。MySQL、一个 Redis、RabbitMQ、SMTP 和 Prometheus 均由用户预先提供；项目不启动、停止、重启或重载这些外部服务。

数据库必须已存在，通过 -ConfirmDatabase 精确确认。脚本不建库、不清库、不删除数据。业务 HTTP API、DTO、Cookie、CSRF、上传 URL、Flyway V1–V7 和三个已发布事件协议保持不变。不提供静态 Web 服务器或 Grafana 运行入口。

不得读取、复制、暂存或上传本机私有配置、秘密目录、数据库内容或备份、上传数据、Cookie、Token、SMTP 授权码、Pepper 和消息密钥。环境文件仅在用户明确运行项目入口时由严格加载器读取，值不回显、不展开变量、不执行表达式。已跟踪的 backend/src/main/resources/application.yml 是受控脱敏配置，不得用旧本机配置覆盖。

### V6 前台命令入口

V6 使用 infrastructure/host/with-app-environment.ps1 包裹一个标准 Maven、Java 或 npm 前台命令；退出时恢复环境，不创建日志或 PID 状态。2026-09-09 的方面三已验证以下入口、最小业务闭环及 Maven 到 JAR 的会话恢复，具体证据和限制见 [V6 规划](iteration-summary.md#v6可读性迭代与交接)。下文原有整栈脚本仍是可选入口，其状态文件不适用于这些前台命令。

准备三个独立 PowerShell 7 终端。Windows PowerShell 5.1 不能执行这些脚本；先运行 pwsh -NoProfile，出现新提示符后用 $PSVersionTable.PSVersion.ToString() 确认版本。本机本次使用的 PowerShell 7.6.5 绝对路径如下；其他机器应使用自身已安装路径，不自动安装或修改 PATH：

~~~powershell
& 'C:\Users\31880\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile
~~~

先由用户准备好原有 MySQL、Redis、RabbitMQ、SMTP 和 Prometheus。复用隔离数据库 cc4cv5a3smoke，不重复引导账户。启动会执行应用已有的数据库迁移校验、Session 和消息处理，不能视为无数据副作用。三个应用启动前确认 4080、4081、5173、5174 空闲；有占用时停止，不自动换端口或结束占用者。

终端一启动后端，仅在本次调用期间选择 Java 21，Maven 保持离线：

~~~powershell
Set-Location -LiteralPath 'D:\codex\CC4C_v5\backend'
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$previousMavenArgs = $env:MAVEN_ARGS
try {
    $env:JAVA_HOME = 'D:\tool\Java\jdk-21'
    $env:PATH = "$env:JAVA_HOME\bin;$previousPath"
    $env:MAVEN_ARGS = '-o'
    & ..\infrastructure\host\with-app-environment.ps1 `
        -Application Backend -ConfirmDatabase cc4cv5a3smoke `
        -Command { mvn -o spring-boot:run }
}
finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    $env:MAVEN_ARGS = $previousMavenArgs
}
~~~

确认后端 health、liveness、readiness 全部正常后，终端二和终端三分别执行：

~~~powershell
# 终端二：业务前端。
Set-Location -LiteralPath 'D:\codex\CC4C_v5\frontend'
& ..\infrastructure\host\with-app-environment.ps1 `
    -Application Frontend `
    -Command { npm run dev -- --host localhost --port 5173 --strictPort }

# 终端三：观测前端。
Set-Location -LiteralPath 'D:\codex\CC4C_v5\observability'
& ..\infrastructure\host\with-app-environment.ps1 `
    -Application Observability `
    -Command { npm run dev -- --host localhost --port 5174 --strictPort }
~~~

浏览器分别访问 http://localhost:5173 和 http://localhost:5174。方面三验收时 localhost 对应的 Vite 监听为 ::1；两个非 VITE_ 上传根变量仍由辅助注入业务前端，已有头像与新博客图片映射均已验证。

使用方面二已构建的 JAR 时，先停止 Maven 后端并确认 4080、4081 释放；在上述同一个 Java 21 环境包裹块中，仅替换命令为：

~~~powershell
& ..\infrastructure\host\with-app-environment.ps1 `
    -Application Backend -ConfirmDatabase cc4cv5a3smoke `
    -Command { java -jar target/cc4c-6.0.0-SNAPSHOT.jar }
~~~

不同时运行 Maven 和 JAR 两个后端，不为切换入口重新安装依赖或构建。方面三已确认两端刷新后恢复原有 V6 会话；真实 V5 会话恢复没有执行，方面二的合成旧格式校验是独立证据。

只读健康检查无需配置或凭据。PowerShell 对 Actuator 媒体类型可能返回字节数组，应先按 UTF-8 解码；方面三默认前端请求曾返回 502，而明确直连返回 200，因此检查本机 URL 使用 -NoProxy，不修改系统代理：

~~~powershell
foreach ($endpoint in @('health', 'health/liveness', 'health/readiness')) {
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:4081/actuator/$endpoint" `
        -NoProxy -TimeoutSec 10 -MaximumRedirection 0
    $json = if ($response.Content -is [byte[]]) {
        [Text.Encoding]::UTF8.GetString($response.Content)
    } else { [string]$response.Content }
    if ($response.StatusCode -ne 200 -or ($json | ConvertFrom-Json).status -ne 'UP') {
        throw "Health check failed: $endpoint"
    }
}
~~~

失败时停止后续操作，不自动重复登录、重试请求或读取私有日志。方面三遇到过 Redis 不监听导致后端启动失败或 readiness 为 DOWN，以及 Prometheus 未启动导致观测不可用；应恢复既有外部依赖，再分别检查后端健康和抓取。抓取 up=1 不代表所有依赖或登录正常。

停止由用户在各自前台终端依次按 Ctrl+C：观测端、业务前端、后端。核对本次记录的 PID 已退出及四个端口释放，不调用旧状态文件停止器，不按进程名称、端口或进程树批量结束。前端可执行文件路径若无法由操作系统取得，应记录该限制；不能据此执行自动强制停止。外部中间件继续保留。

### 三端配置入口

只使用以下三份模板及对应本机文件：

| 应用 | 受控模板 | 用户手工准备、Git 忽略的文件 |
| --- | --- | --- |
| 后端 | backend/.env.example | backend/.env.local |
| 业务前端 | frontend/.env.example | frontend/.env.local |
| 观测前端 | observability/.env.example | observability/.env.local |

两个前端模板都只有公开的 VITE_API_BASE_URL，默认 http://localhost:4080，不得放入 Prometheus 地址、管理凭据或其他秘密。观测端使用独立 OBSERVABILITY Session，不复用业务用户或管理员身份。

用户根据模板手工整理 .env.local，保留原仓库外文件作为私有资料。加载器不接受环境文件路径参数或旧目录回退。非注释行必须是字面的 NAME=value，不使用 export、引号展开或命令替换；重复、未知、缺失或非法字段都会失败。文件及全部父目录必须是普通本机路径，不允许链接或 reparse point。

后端配置规则：

- 数据库 URL、账号、密码、SMTP 主机和端口必须显式提供，不回退到数据库管理员账号或默认邮件服务。
- Session、缓存和观测身份共用 CC4C_REDIS_URL；三个 namespace 均必填且互不相同。用户应移除旧缓存专用 Redis URL 项。
- JDBC 数据库名必须与 -ConfirmDatabase 逐字符一致；连接和验证等待均有界。
- SMTP 认证、隐式 SSL 或 STARTTLS 应与邮箱服务一致，不能同时启用两种 TLS 方式；TCP 预检不代替实际收件确认。
- CORS 只接受精确来源，不接受通配符、路径或凭据。Cookie Secure 与访问协议一致。
- 管理端固定绑定 127.0.0.1，默认端口 4081，只保存 BCrypt cost-12 哈希；其明文仅保留在外部 Prometheus 私有配置中。
- 观测门户账户使用另一份 BCrypt cost-12 哈希，Cookie Secure 与访问协议一致，CORS 固定为 http://localhost:5174。
- Prometheus URL 和可选成对 Basic 凭据只进入后端配置，不进入浏览器产物。
- 上传模板使用以 backend 为工作目录解析的 ../temp/uploads/blogImg/ 和 ../temp/uploads/avatar/。已有隔离环境保留当前绝对路径，不迁移、不清空文件。

Vite 启动器只注入公开 API 地址及两个非 VITE_ 变量 CC4C_HOST_BLOG_IMG_ROOT、CC4C_HOST_AVATAR_ROOT，不传入后端凭据。两根路径必须是不同普通目录；浏览器仍使用 /blogImg/、/avatar/，不回退到旧 public 上传目录。前端不要另建 .env、.env.development、.env.production 或对应模式的 .local 文件；不要用额外环境变量或参数覆盖私有配置来源。

只检查忽略规则，不输出内容：

~~~powershell
Set-Location -LiteralPath 'D:\codex\CC4C_v5'
git check-ignore -- backend/.env.local frontend/.env.local observability/.env.local
~~~

### 可选旧入口：预检、启动与健康

~~~powershell
.\infrastructure\host\host-preflight.ps1 -Component All -ConfirmDatabase <精确数据库名>
.\infrastructure\host\start-host-stack.ps1 -ConfirmDatabase <精确数据库名>
.\infrastructure\host\health-host-stack.ps1
~~~

预检可选 MySQL、Redis、RabbitMQ、SMTP、Backend、Frontend、Observability 或 Prometheus：

1. MySQL：只解析 JDBC 地址、精确确认数据库名并检查 TCP，不执行 SQL。Flyway 校验和迁移由后端正常启动执行。
2. Redis：只验证同一地址可达，不查询键、不清空 namespace。
3. RabbitMQ：使用预配置 cc4c vhost 和现有 cc4c.v3.* namespace，不创建、删除或 purge 消息资源。
4. SMTP：不发送预检邮件，不打印账号或授权码。
5. 默认业务端口 4080、管理端口 4081、业务前端 5173、观测前端 5174 互不相同且必须空闲；不杀占用者、不自动换端口。
6. 外部 Prometheus：查询就绪和版本，启动前不要求尚未运行的后端已被成功抓取。

后端以 backend 为工作目录，使用 SPRING_CONFIG_NAME=application 读取受控打包配置。两端 Vite 分别绑定 127.0.0.1:5173 和 127.0.0.1:5174，并启用严格端口检查。整栈入口逐端确认健康，所有临时环境变量在 finally 中恢复。

独立入口：

~~~powershell
.\backend\scripts\start-backend.ps1 -ConfirmDatabase <精确数据库名>
.\frontend\scripts\start-frontend.ps1
.\observability\scripts\start-observability.ps1
.\observability\scripts\stop-observability.ps1
.\frontend\scripts\stop-frontend.ps1
.\backend\scripts\stop-backend.ps1
~~~

### 外部 Prometheus

公开模板及规则位于 infrastructure/prometheus/。旧 Grafana 面板已完整转换为后端固定 catalog 和 ECharts 页面，不再保留 Grafana 运行或 provisioning 资产。模板的 RabbitMQ 抓取定义保持原样，外部实例实际启用哪些抓取任务由用户管理。

若外部私有配置引用旧规则路径，由用户自行调整、检查和重载。项目不读取该私有配置，不启动、停止或重载实例。检查入口只校验仓库公开模板和规则，再查询外部实例：

~~~powershell
.\infrastructure\prometheus\check-prometheus.ps1 -PromtoolPath 'D:\tool\prometheus-3.13.2.windows-amd64\promtool.exe' -RequireBackendScrape
~~~

-PrometheusUrl 默认 http://127.0.0.1:9090。实例必须就绪且版本为 3.13.2；指定 -RequireBackendScrape 时，up{job="cc4c-backend"} 至少有一项且所有返回值为 1。不执行规则测试、不查看 TSDB 内容、不声称外部私有配置正文已经验证。

### 可选旧入口：状态、日志与停止

状态仍位于 temp/cc4c-host-stack/，包含 backend、frontend、observability 和 stack 四份记录。三端记录包含 PID、绝对可执行文件、完整应用标记和操作系统真实进程创建时间，schema v3 栈记录保存本次身份快照。已停止旧记录可由新启动正常更新；旧运行记录缺字段或身份不匹配时停止人工排查，不猜测补全。

日志位于 temp/cc4c-host-backend/、temp/cc4c-host-frontend/ 和 temp/cc4c-host-observability/，每次使用时间戳加唯一 ID 的新文件名，不覆盖旧日志。仅在授权后本地查看，不上传凭据、请求正文或完整配置。

~~~powershell
.\infrastructure\host\health-host-stack.ps1 -IncludePrometheus
.\infrastructure\host\stop-host-stack.ps1
~~~

健康检查核对进程真实身份、各端口精确所有者、前端 HTTP 200 及后端 health/liveness/readiness 的 UP 状态。停止前确认 Prometheus 后端抓取为 1。

停止只针对本次快照中身份完全匹配的观测端、业务前端和后端，复核创建时间防止 PID 复用；不按名称、端口或进程树终止。启动失败只逆序停止本次已记录组件。身份不明、部分停止或状态写入失败时保留现场、日志和数据，不扩大恢复范围。

### 管理员引导和密码迁移

首次迁移观测身份时，用户准备两个不同的仓库外单行密码文件，并分别运行以下入口；命令只输出哈希：

~~~powershell
.\backend\scripts\hash-observability-password.ps1 -PasswordFile <仓库外绝对密码文件路径>
~~~

一个哈希配置为 CC4C_MANAGEMENT_PASSWORD_HASH，另一个配置为 CC4C_OBSERVABILITY_PASSWORD_HASH。密码要求 12–64 个字符且 UTF-8 不超过 72 字节；密码文件、明文和生成哈希都不得提交。

已有管理员不重复引导。新环境完成 Flyway 后，仅当用户明确要求时执行：

~~~powershell
.\backend\scripts\bootstrap-admin.ps1 -AdminId <七位管理员ID> -ConfirmDatabase <精确数据库名> -PasswordFile <仓库外绝对密码文件路径>
~~~

密码文件必须由用户预先创建，自身和父路径均为普通路径。引导器使用 backend 固定配置，不生成密码、不创建数据库、不输出密码，最后恢复全部临时变量。

离线密码迁移保留备份路径、SHA-256 和精确数据库确认参数，详见 [数据库说明](project-guide.md#数据库维护)。它不是启动步骤，不在已有隔离 smoke 环境重复执行。

### 数据和人工验收

复用既有数据库、账号、Redis/RabbitMQ namespace、密钥和上传路径。人工验收包括登录/会话恢复、注册页面、课程/博客浏览、收藏与取消、头像/博客图片上传、唯一标识博客和评论、管理员仅审核该博客、只读消息页、审核邮件、审核后可见性及本人删除流程。

收藏只撤销本轮新增项；删除前确认精确目标，不删除旧博客、评论或上传文件。既有账号不重复注册，过去注册成功证据不能表述为本轮重跑。验证码和管理员密码由用户输入；浏览器管理会话，不读取或导出 Cookie/Token。

保留至少一次投递、Inbox 幂等、Publisher Confirm、ACK/NACK、重试和 DLQ 语义。排空或停用消费必须另行确认，不对未知消息执行 retry/ignore，不 purge。数据库备份、恢复和消息维护均不是本机启停脚本的隐式操作。

## 数据库维护

### 结构来源

`backend/src/main/resources/db/migration` 中的 Flyway 迁移是数据库结构和公开目录基线的唯一来源：

| 迁移 | 内容 |
| --- | --- |
| `V1__create_cc4c_schema.sql` | 创建现有 16 张表，不含 `DROP`、锁表语句或默认账号 |
| `V2__seed_catalog_reference_data.sql` | 幂等写入 4 种语言、61 门课程、9 个课程模块和 61 条模块关系 |
| `V3__harden_relations_and_add_query_indexes.sql` | 统一文本字符集，强化评论归属与父回复完整性，增加博客及回复查询索引 |
| `V4__expand_password_columns.sql` | 将用户与管理员密码列扩展到 255 字符，为 `{bcrypt}` 格式保留空间；不读取或转换明文 |
| `V5__add_interaction_query_indexes.sql` | 为课程收藏和博客收藏分页增加按用户、收藏时间及资源 ID 排序的复合索引 |
| `V6__add_async_outbox_and_inbox.sql` | 增加加密消息 Outbox/Inbox、租约、尝试次数、generation、受控错误码及发布/消费扫描索引 |
| `V7__add_outbox_correlation_id.sql` | 为 Outbox 增加可空 ASCII 请求关联 ID，使 HTTP、发布、重试和消费者日志可关联，同时兼容 Flyway V6 阶段的历史积压 |

`infrastructure/database/legacy/cc4c.sql` 仅供历史对照，已移除默认管理员，不得用于初始化新环境。应用配置中的 `baseline-on-migrate` 默认并持续保持 `false`。

### 新建空数据库

先由数据库管理员创建使用 `utf8mb4_0900_ai_ci` 的空库，并为应用账号授予业务读写及 Flyway 所需的 `CREATE`、`ALTER`、`INDEX`、`REFERENCES` 权限。随后由用户根据 `backend/.env.example` 手工准备已忽略的 `backend/.env.local`，由 `backend/scripts/start-backend.ps1 -ConfirmDatabase <精确数据库名>` 通过共享加载器注入环境，使用已跟踪的脱敏 `application.yml`；Flyway 会按 V1–V7 初始化 18 张表并校验迁移。空库没有历史账号，不需要执行密码转换。

不要将数据库密码、SMTP 授权码或本机路径写入仓库配置、本文档或日志。生产环境不应为方便迁移而使用数据库管理员账号运行应用。

### 已有非空数据库

已有数据的数据库不得直接开启自动基线。迁移前必须：

1. 使用 `mysqldump --single-transaction --skip-lock-tables` 备份，并保存 SHA-256。
2. 核对 16 张表、主外键、重复评论归属及父评论孤儿数据；发现异常立即停止。
3. 确认当前结构与 V1 一致后，显式在版本 1 建立基线，再应用 V2/V3/V4/V5/V6/V7；完成后应有 16 张业务表和 2 张异步可靠性表。
4. 保持后端停止，使用备份文件、SHA-256 和精确数据库名称运行 `migrate-passwords.ps1`，将所有非 `{bcrypt}` 密码离线转换；工具不得输出账号、明文、哈希或数据库凭据。
5. 再次执行密码迁移必须转换 0 行，并确认明文或未知 `{id}` 格式剩余数为 0。
6. 第二次 Flyway `migrate` 必须为零新增迁移，随后执行 `validate` 和结构断言，最后才允许启动 Web 应用。

离线密码迁移只从固定 `backend/.env.local` 读取运行配置，不接受外部配置路径或旧目录回退。数据库名必须与备份确认信息逐字符一致；进程变量在 `finally` 中恢复，脚本不输出配置值。只有用户明确要求迁移时才执行：

```powershell
cd D:\codex\CC4C_v5
.\infrastructure\database\migrate-passwords.ps1 `
  -BackupPath <已验证备份的绝对路径> `
  -BackupSha256 <64-hex-sha256> `
  -ConfirmDatabase <exact-database-name>
```

普通 Web 启动会检查全部用户和管理员密码。只要仍存在明文或未知 `{id}` 格式，就会拒绝启动；应用不会在登录时懒迁移，也不能把迁移后的数据库直接交给 V3 密码安全升级前的旧代码。

迁移失败时不得直接执行 `repair`，也不得在原库上反复试错。应停止应用，将已验证备份恢复到一个新数据库，比较结构与数据后切换连接。Flyway Community 不提供伪造的 down migration，本项目也不维护破坏性的回滚脚本。

### 异步 Outbox 与 Inbox

Flyway 迁移 V6 增加的 `async_outbox` 是消息管理页面和人工恢复的事实来源，`async_inbox` 用于按 `consumer_name + event_id + generation` 去重。业务事务与 Outbox 行同提交、同回滚；不得使用独立新事务绕过业务回滚。`PUBLISHED` 仅表示 RabbitMQ Publisher Confirm 成功，只有消费者完成外部处理并写入 Inbox `DONE` 后才进入 `DELIVERED`。

载荷以 AES-256-GCM 保存，明文列只保留事件 ID、版本、类型、聚合类型/ID、generation、时间和密钥 ID。邮箱、验证码和邮件正文不得出现在表的摘要字段、受控错误码或运维查询结果中。活动写入密钥与只读旧密钥通过本机环境配置轮换；在旧 Outbox、Inbox 和 DLQ 超过保留期前不得移除旧密钥。

`DELIVERED`、`EXPIRED`、`IGNORED` Outbox 与 `DONE` Inbox 保留 31 天后分批清理，每批不超过 500 条；`PUBLISH_FAILED` 和 `DEAD` 不自动删除。Flyway 迁移 V6 只增加表和索引，不提供伪造 down migration。原 V3 方面五手册的回滚流程只适用于当时版本；当前维护必须保留两张表、未完成记录和密钥，单独评估目标代码与 Session 兼容。

### V7 请求关联兼容性

V7 只向 `async_outbox` 增加可空的 `correlation_id VARCHAR(64)`，不修改 V6 的密文、nonce、AAD、事件版本或索引语义。新 HTTP 事务写入校验后的 `X-Request-ID`，非 HTTP 事件使用 eventId；Publisher 将同一值传入受控 AMQP Header，重试和 DLQ 保持原关联 ID。升级前已存在且该列为空的消息由消费者回退到 eventId，因此无需重写或解密历史积压。

该字段仅用于日志关联，不能用于身份、幂等或授权，也不得写入 Cookie、邮箱、验证码、SQL 或连接信息。历史上仅支持 Flyway V6 的代码会忽略这个可空附加列；这不是 CC4C 版本 V6 的回滚承诺。不得为回滚删除列或执行 Flyway `repair`。

### 宿主机数据安全与备份

宿主机模式由 [宿主机运行手册](project-guide.md#配置与本机运行) 管理，要求用户预先创建并精确确认目标数据库；应用启动时由 Flyway V1–V7 完成迁移，不执行 `clean`、`repair`、降级、数据库创建或数据库删除。

升级或维护前，必须从运行中的 MySQL 服务执行单事务备份并保存 SHA-256，同时单独备份博客和头像上传目录。恢复时只能导入到用户预先创建的新数据库，核对结构和数据后再切换连接；不得在原库反复试错、伪造 down migration 或手工删除 V1–V7 历史。

### 产物与安全

数据库备份、SHA-256 和日志只允许写入用户明确指定的受保护位置或已忽略的 `temp/`。这些文件可能包含结构或数据线索，不得暂存、提交或上传。RabbitMQ definitions、消息密文和 DLQ 导出同样不得进入仓库。提交前必须确认三端 `.env.local`、其他本机配置、`target/`、`temp/` 和日志均未进入 Git。已跟踪的 `backend/src/main/resources/application.yml` 只能保留受控环境占位符，不允许用旧本机配置覆盖；历史 SQL 和 Flyway 文件保持原样，不参与 Maven 过滤。

## 异步消息维护

### 适用范围与安全边界

该链路始于 V3 方面五，当前仍处理验证码邮件、博客待审核通知和博客审核结果通知。MySQL `async_outbox` 是可靠受理与人工恢复的事实来源，RabbitMQ 负责至少一次投递，`async_inbox` 负责同一消费者、eventId 和 generation 的幂等处理。

任何操作前先确认精确环境、vhost、Rabbit namespace 和数据库名称。本文不授权以下操作：

- RabbitMQ `purge`、删除生产队列、删除 vhost 或无前缀清理；
- Flyway `clean`、`repair`、伪造 down migration 或删除 Outbox/Inbox；
- Redis `FLUSHDB`、`FLUSHALL` 或删除 Session、限流和其他 namespace；
- 输出或复制消息密文、邮箱、验证码、Cookie、Session ID、AES 密钥、SMTP 授权码或 RabbitMQ URL；
- 修改、读取或打包本机 `application.yml`。

日志与工单只允许记录 eventId、eventType、generation、状态、尝试次数、受控 errorCode 和时间。不要粘贴请求体、邮件正文、异常正文或连接字符串。

### 数据流与状态含义

```text
业务事务
  └─ 写业务数据 + async_outbox(PENDING)
       └─ Dispatcher 领取租约
            └─ RabbitMQ Publisher Confirm
                 └─ PUBLISHED
                      └─ Consumer + async_inbox 幂等
                           ├─ 成功：DELIVERED + Inbox DONE + ACK
                           ├─ 临时失败：30s → 5m → 30m retry queue
                           └─ 永久失败/耗尽：DEAD + DLQ
```

关键状态：

| 状态 | 含义 | 默认动作 |
| --- | --- | --- |
| `PENDING` | 业务事务已提交，等待发布或退避到期 | 等待 Dispatcher |
| `PUBLISHING` | 某实例持有 30 秒发布租约 | 等待 Confirm；租约过期可接管 |
| `PUBLISHED` | Broker 已确认接管，不代表邮件成功 | 等待消费者 |
| `DELIVERED` | 消费者已完成并写 Inbox DONE | 无需处理 |
| `PUBLISH_FAILED` | 发布有限重试耗尽 | 修复 Broker/拓扑后由管理员重试 |
| `DEAD` | 永久消费错误或三段重试耗尽 | 修复外部依赖后由管理员重试或忽略 |
| `EXPIRED` | 验证码事件超过 10 分钟 | 禁止重试 |
| `IGNORED` | 管理员明确停止恢复 | 不再自动投递 |

`PUBLISHED` 只证明 Publisher Confirm；RabbitMQ Confirm 和消费者 ACK 是两个独立阶段。系统不宣称 SMTP 端到端 exactly-once。

### 正常检查

1. 确认 Web 应用、MySQL、安全 Redis 和 RabbitMQ 均可连接。
2. 在 RabbitMQ 管理页确认当前 namespace 下三个主队列各有预期消费者，且没有持续增长的 `messages_ready`。
3. 管理员访问 `/admin/messaging`，默认只看待发送与失败消息；页面不应展示邮箱、验证码或载荷。
4. 检查日志是否存在相同 eventId 的 `confirmed` 和 `delivered`。不要用日志正文判断邮件内容。
5. 启用 OpenAPI 时确认 `/admin/messaging/messages` 三个接口仅描述安全摘要 DTO。

RabbitMQ 4.3.5 本地验收拓扑为 durable topic exchange、durable quorum 主队列、三段 retry quorum queue 和最终 DLQ。队列参数或 TTL 变化必须使用新版本 namespace，不能原地删除或重建生产队列。

### RabbitMQ 不可用

预期行为：

- `POST /users/email` 仍返回 202；博客提交和审核事务仍成功。
- 新事件保留在 MySQL，状态为 `PENDING` 或短暂 `PUBLISHING`，发布失败按有限退避处理。
- Listener 连接失败日志可以出现，但不得包含 Rabbit URL、凭据或载荷。
- 安全 Redis 的会话、验证码摘要和限流不因 RabbitMQ 故障降级。

恢复步骤：

1. 修复并启动精确的 RabbitMQ 节点，不要新建同名空 vhost 替代原数据。
2. 确认 AMQP 端口和目标 vhost 可用，拓扑没有 `PRECONDITION_FAILED` 或不可路由错误。
3. 保持 Dispatcher 开启，等待现有 `next_attempt_at` 到期；不要为缩短等待直接改数据库时间或尝试次数。
4. 核对同一 eventId 最终出现 `confirmed`，随后由消费者进入 `delivered`。
5. 验证码超过 10 分钟会转为 `EXPIRED`，不得发送或人工重试；应让用户重新申请。

只有得到单独授权后才可在本地验收中暂停精确 RabbitMQ 节点。恢复后必须确认服务状态和端口，不能把 Broker 留在停止状态。

### 暂停发布或消费

两个运行开关通过后端固定本机配置入口管理；当前严格加载器不接受额外环境来源覆盖：

```dotenv
CC4C_OUTBOX_DISPATCHER_ENABLED=false
CC4C_MESSAGE_CONSUMERS_ENABLED=false
```

- 暂停 Dispatcher：业务事件继续写入 MySQL `PENDING`，RabbitMQ 不新增消息。
- 暂停 Consumer：Dispatcher 继续发布并获得 Confirm，主队列 `messages_ready` 增长，Outbox 停留在 `PUBLISHED`。

恢复时将开关改回 `true` 并重启精确 CC4C 后端。Redis Session 会保留浏览器身份。恢复消费者后应观察 `consumers > 0`、`messages_ready` 下降和事件进入 `DELIVERED`。不要通过 purge 队列模拟消费完成。

### SMTP 失败、DEAD 与人工恢复

临时错误包括 SMTP 4xx、连接超时和未知网络错误，按 `30s、5m、30m` 进入对应 retry queue。永久错误包括非法地址、明确 SMTP 5xx、未知事件版本、解密失败和非法载荷，直接进入 `DEAD`；未知异常在有限重试耗尽后使用受控错误码结束。

恢复步骤：

1. 根据 `errorCode` 修复 SMTP、地址、密钥环或事件处理器。不得把异常正文写回数据库。
2. 恢复正常配置并重启后端，先用新的健康事件证明外部依赖可用。
3. 管理员打开 `/admin/messaging`，筛选 `PUBLISH_FAILED` 或 `DEAD`。
4. 核对 eventType、aggregateId、时间和错误码，不查看或导出载荷。
5. 对确需恢复的单条消息点击“重试”并二次确认。重试会把 generation 加一、清除租约和错误状态并回到 `PENDING`。
6. 等待新 generation 完成 `confirmed → delivered`。重复点击已完成消息应返回 409。
7. 若业务明确不应再投递，可点击“忽略”；该动作会记录管理员 actor ID 和时间。`IGNORED` 不得再次重试。

SMTP 可能已接收邮件，但消费者在写 Inbox DONE 前崩溃。此时恢复可能产生内容相同的重复邮件；确定性 `Message-ID` 和 `X-CC4C-Event-Id` 只能帮助识别，不能把外部 SMTP 宣称为 exactly-once。

### 验证码专项处理

- 验证码有效期从 HTTP 202 受理时开始，为 10 分钟，不从 SMTP 实际发送时重新计时。
- Consumer 发信前通过 Redis Lua 原子激活验证码，记录 eventId、issuedAt 和摘要；Redis 不保存邮箱原文或验证码。
- 延迟到达的旧事件不能覆盖更新 eventId 的验证码。
- 最终失败或过期时，只能在 Redis eventId 仍匹配时删除对应记录，不能误删新验证码。
- `EXPIRED` 验证码事件返回 422 且不可恢复；让用户重新申请，不要修改数据库过期时间。

### 消息密钥轮换

1. 生成新的独立 32 字节 AES 密钥，不复用 `CC4C_SECURITY_PEPPER`。
2. 先把新 key ID 和密钥加入 `CC4C_MESSAGING_PAYLOAD_KEYS`，保留全部仍可能被读取的旧密钥。
3. 启动并确认旧消息仍可解密，再把 `CC4C_MESSAGING_ACTIVE_KEY_ID` 切换为新 key ID。
4. 观察新 Outbox 使用新 key ID，旧消息仍能重试和消费。
5. 只有旧 Outbox、Inbox 和 DLQ 全部超过保留期且完成审计后，才能从可读 key ring 移除旧密钥。

未知 key ID 或 GCM AAD 校验失败属于永久错误。不得用 `repair`、改密文或替换 eventType 绕过校验。

### 保留与清理

- `DELIVERED`、`EXPIRED`、`IGNORED` Outbox 保留 31 天后分批清理。
- `DONE` Inbox 保留 31 天，必须长于 RabbitMQ DLQ 的 30 天保留期。
- 每批最多 500 条；`PUBLISH_FAILED`、`DEAD` 和所有待处理状态不自动删除。
- 历史 RabbitMQ 测试曾只清理当轮随机 test namespace 的精确资源；V5 已删除测试入口。当前生产 namespace 不执行自动删除。

如果管理页积压异常增长，应先暂停新发布、保留数据库和 Broker 证据，再定位根因；不要以删表、purge 或缩短 TTL 作为恢复方式。

### 版本恢复边界

原 V3 手册的 bc7dcf8 回滚流程属于当时尚未接入可靠消息的代码阶段，不能作为当前 V6 通用操作。原文见[固定 V5 来源](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/operations/messaging-failure-runbook.md)。

恢复前必须单独评估数据库、事件、密钥与 Session 兼容，确认精确环境和停止范围。保留 Outbox／Inbox、durable 队列、未完成 eventId／generation 和旧密钥；不把未处理消息伪标为 DELIVERED，不删除 DLQ。验证码过期后按 EXPIRED 处理，恢复正确代码和密钥后再受控启用 Dispatcher／Consumer。V6 新写入 Session 不保证可由 V5 读取。

不提供 down migration；不得使用破坏性 Git 恢复、Flyway clean／repair、清库或 purge 代替受控恢复。

### 关闭故障单前的证据

- 业务接口状态和 `code/data/msg` 契约正常；
- 目标 eventId 的 generation、Confirm、消费和最终状态完整；
- Rabbit 主队列消费者恢复，积压不再增长；
- 管理页面没有暴露敏感载荷；
- 验证码未过期且只能消费一次，或已按规则 EXPIRED；
- 日志没有邮箱、验证码、Cookie、Session ID、密钥或连接凭据；
- 未执行 purge、vhost 删除、数据库破坏性清理或 Flyway repair；
- 本机 `.env.*.local`、日志、Rabbit 导出和 `temp/` 未进入 Git。

## 观测架构

总览固定包含 8 项摘要，三个 Dashboard、20 个面板、39 条查询和 20 条告警由同一受控 catalog 与公开规则约束。

### 安全边界

中文观测后台由独立 Vue 3 应用和后端按 controller、service、security 和 support/monitoring 分层的观测实现组成。它使用唯一 `OBSERVABILITY` 账户、
`CC4C_OBSERVABILITY_SESSION` HttpOnly Cookie、专用 CSRF Cookie 和独立 Redis namespace；不复用业务
`USER`、`ADMIN`、`CC4C_SESSION` 或业务 CSRF。门户 Session 空闲 30 分钟失效，创建 8 小时后绝对失效。

浏览器只持有 Cookie 和内存中的 CSRF Token，不使用 localStorage 或 sessionStorage。Redis 键只保存随机
Session Token 的 HMAC-SHA256，值只包含用户名、创建时间和绝对期限。登录按账户和 IP 分别限流，错误账户
仍执行 BCrypt 校验。状态变更请求同时校验专用 CSRF 头和精确 Origin。

管理端口的 Basic Auth 使用独立 BCrypt 哈希，供外部 Prometheus 抓取；门户密码必须不同。Prometheus URL、
可选 Basic 凭据和所有 PromQL 仅存在于后端配置及固定 catalog，不进入 `observability/.env.local` 或浏览器
产物。

### 数据流

1. 浏览器从 `/observability/auth/csrf` 获取内存 CSRF Token，再登录独立门户。
2. 后端验证 BCrypt、限流、Origin 和 CSRF，向独立 Redis namespace 写入哈希 Session。
3. 已认证请求只能选择三个 Dashboard ID 和四种时间范围；浏览器不能提交 PromQL。
4. 后端从只读 catalog 选择固定表达式，以 4 个并发、32 个等待任务和明确超时访问 Prometheus。
5. 后端限制响应体、序列、标签和点数，将故障归一化为 `AVAILABLE`、`PARTIAL`、`EMPTY` 或
   `UNAVAILABLE`，不返回原始异常或上游响应。
6. 依赖页直接读取 Spring 健康贡献者及 ApplicationAvailability，不通过管理端口自调用。

### 二十个面板和三十九条查询

| 页面 | 面板 | 查询数 |
| --- | --- | ---: |
| API 与 JVM | HTTP 请求速率 | 1 |
| API 与 JVM | HTTP P50 / P95 / P99 | 3 |
| API 与 JVM | JVM 堆内存 | 2 |
| API 与 JVM | CPU 与 GC | 2 |
| API 与 JVM | HTTP 错误状态速率 | 1 |
| API 与 JVM | JVM 与 Tomcat 线程 | 3 |
| 数据库、缓存与安全 | MyBatis 各模块 P95 | 1 |
| 数据库、缓存与安全 | Hikari 连接池 | 3 |
| 数据库、缓存与安全 | 缓存结果速率 | 1 |
| 数据库、缓存与安全 | 认证与限流事件 | 2 |
| 数据库、缓存与安全 | MyBatis 操作与错误 | 1 |
| 数据库、缓存与安全 | 缓存命中率与 Redis 错误 | 2 |
| 数据库、缓存与安全 | 授权拒绝 | 1 |
| 异步消息 | Outbox 与 Inbox 状态 | 2 |
| 异步消息 | 最老待发与采样器年龄 | 2 |
| 异步消息 | 发布与消费 P95 | 2 |
| 异步消息 | RabbitMQ 队列 | 3 |
| 异步消息 | 重试、死亡、重复与过期 | 4 |
| 异步消息 | 发布与消费结果 | 2 |
| 异步消息 | RabbitMQ 死信队列 | 1 |

这些查询完整承接 V4 的三个 Grafana Dashboard。ECharts 只负责显示后端返回的有界序列，单 Dashboard
最多 16 条查询、单面板最多 4 条、单查询最多 50 个序列、单序列最多 300 个点。

### 二十条告警

告警页固定展示规则文件中的：`Cc4cBackendUnreachable`、`Cc4cHttp5xxRateHigh`、`Cc4cApiP95High`、
`Cc4cHikariPending`、`Cc4cHikariUtilizationHigh`、`Cc4cMybatisP95High`、
`Cc4cMybatisErrorRateHigh`、`Cc4cCacheHitRatioLow`、`Cc4cCacheFallbackIncreasing`、
`Cc4cAuthenticationFailuresHigh`、`Cc4cRateLimitRejectionsHigh`、`Cc4cOutboxBacklogOld`、
`Cc4cOutboxFailed`、`Cc4cMessagingSamplerStale`、`Cc4cRabbitBacklog`、`Cc4cRabbitNoConsumers`、
`Cc4cRabbitDeadLetters`、`Cc4cJvmHeapHigh`、`Cc4cProcessCpuHigh` 和 `Cc4cGcPauseP99High`。

运行状态来自 Prometheus `/api/v1/rules?type=alert`，中文标题、含义及级别来自固定 catalog。缺失规则会以
`MISSING` 显示，原始 PromQL 和 `lastError` 不返回浏览器。

### 故障语义

Prometheus 不可达、响应超限、格式错误或查询部分失败时，数据接口仍返回可渲染的脱敏状态。身份、CSRF、
输入和限流失败分别使用 401、403、400/404 和 429。前端为每张图保留文本最新值、空数据和失败替代，页面
隐藏或请求未结束时不发起重叠轮询。

## 代码质量

本文档描述当前活动源码的质量门禁。当前版本和工具版本以仓库根目录的
[`versions.yml`](../versions.yml) 为唯一基线；历史报告、迁移脚本和生成文件不在本规则的格式化范围内。

### 格式范围

- Java 使用 Spotless 2.44.5 和 Palantir Java Format 2.68.0，缩进为四个空格。
- JavaScript、Vue、CSS、JSON、YAML 和 Markdown 使用 Prettier，缩进为两个空格、单引号、保留分号、120 列和 LF 换行。
- PowerShell 和 SQL 的缩进分别为四个空格；所有活动文本文件使用 UTF-8、无 BOM、末尾换行。
- `backend/src/main/resources/db/migration`、`backend/openapi.json`、历史资料（原 `docs/history`，正文已归入性能历史与迭代总结）、锁文件、构建产物和本机配置不做格式化。

本地可以显式执行：

```powershell
cd D:\codex\CC4C_v5\backend
mvn spotless:apply

cd D:\codex\CC4C_v5\frontend
npm run format
npm run lint

cd D:\codex\CC4C_v5\observability
npm run format
npm run lint

cd D:\codex\CC4C_v5
.\infrastructure\quality\check-code-quality.ps1
```

本机质量入口先执行 PowerShell AST 与 Java 中文 Javadoc 覆盖检查，再执行 `spotless:check`、两端前端的
`npm run lint` 与 `npm run format:check`、源码质量、文档链接和观测契约检查。入口不自动改写文件、
不安装依赖、不运行自动化测试。[GitHub build 工作流](../.github/workflows/build.yml)在
`v6/readability`、`v5/restructure`、`main` 和面向 `main` 的拉取请求上执行相同质量门禁，并完成后端生产打包和两个前端
生产构建；它不使用 Docker、不连接业务外部服务，也不发布构建产物。

质量检查器位于 `infrastructure/quality/`，只使用 Git tracked 与非忽略未跟踪文件清单，排除已删除路径；
Git 查询失败即停止，不回退为递归扫描。源文件和父路径必须是普通路径，秘密、本机配置、构建产物、
上传数据、历史 SQL 和锁文件不进入正文扫描。观测契约检查固定验证 8 项总览、3 个 Dashboard、
20 个面板、39 条查询和 20 条告警。

```powershell
node .\infrastructure\quality\check-source-quality.mjs
node .\infrastructure\quality\check-doc-links.mjs
node .\infrastructure\quality\check-observability-contract.mjs
java --source 21 .\infrastructure\quality\JavaCommentCoverage.java --repository-root .
.\infrastructure\quality\check-powershell-quality.ps1
```

受控 PowerShell 使用 7.6.5，通过 AST 语法检查而不执行脚本主体。生产构建使用
`mvn --no-transfer-progress clean package -DskipTests` 和两端前端各自的 `npm run build`；
业务与观测验证采用用户确认的隔离环境和浏览器 smoke。

### 中文 Javadoc 边界

`JavaCommentCoverage.java` 使用 JDK 21 Compiler Tree API 检查生产 Java，不依赖正则推测声明。当前基线包含
13 个包说明、228 个具名类型、108 个显式构造器和 587 个显式方法，共 936 个文档单元。具名类型、
匿名类中的显式覆盖方法和全部显式构造器/方法都必须具有中文 Javadoc；匿名类类型本身、隐式 record
访问器和隐式构造器不计入覆盖。

- 类型说明职责、边界和主要协作对象；record 还要为每个组件提供 `@param`。
- 方法和构造器为全部值参数及类型参数提供 `@param`；非 `void` 方法提供 `@return`，显式异常提供
  `@throws` 或 `@exception`。
- 涉及事务、权限、脱敏、幂等、缓存失效、重试、故障旁路或外部副作用时，说明必须与当前实现一致，
  不得虚构额外保证。
- 注释不得改变标识符、HTTP/API 契约、SQL、数据库迁移、消息事件名或日志键，也不为已移除的测试资产
  保留伪入口。

### Vue 与 JavaScript 功能边界

两个 ESLint 配置共同加载本地 `eslint-functional-comments.mjs`。每个 `.vue` 组件需要中文职责说明；
具名/导出函数、API wrapper、composable、Pinia/Vuex action、事件处理器、业务派生函数以及生命周期、
`watch`、路由守卫、定时器、事件监听和 Axios 拦截器边界需要紧邻中文说明。

API 注释应区分只读请求和写入副作用，并写清 Session/CSRF 与错误处理责任。局部
`map`/`filter`/`reduce`/`find`/`forEach` 回调、Promise 链回调和懒加载组件由所属语义函数统一说明，
不要求逐行翻译式注释。规则只检查职责说明是否存在，评审仍需核对文字与实现一致。

### PowerShell 功能边界

`check-powershell-quality.ps1` 使用 PowerShell AST 校验语法，不执行被检查脚本。当前 18 个受控脚本必须
同时具有 `运行前提`、`外部依赖`、`破坏性边界`、`失败恢复` 和 `退出码` 五项头部；每个具名函数必须
有紧邻中文说明。检查范围来自 Git 清单，身份不明的路径或 Git 查询失败都会直接阻断。

### V6 方面五的审阅标准

本轮对 167 份生产 Java、业务端 51 份与观测端 23 份 Vue/JS、18 份 PowerShell 全量阅读，按需改写说明。审阅清单逐文件登记理由，Java 覆盖全部 936 个文档单元，两端登记 326 个既有功能边界并补充 Store 对象中的 action/mutation，PowerShell 覆盖五项头部与 38 个具名函数。准确说明保留，不用增加注释数量代替职责核实。

- 类型、组件说明具体职责与状态归属；页面委托给共享组件的操作不写成页面自身实现。
- 方法和 API wrapper 说明真实动作、返回与失败责任；参数只写实现支持的格式、范围、单位或特殊值，不虚构可空和默认值。
- 事务提交后缓存失效、Session 撤销与兼容、消息租约及 ACK/NACK、草稿删除、上传与通知等副作用，结合调用方和协作者核实。
- 轮询说明区分取消下一轮计时和中止当前请求；加载函数返回的时间不自动等于数据查询成功。生命周期只声明实际释放的资源。
- 简单访问器和派生值保持简短；局部集合回调由所属语义函数说明，不逐行翻译代码，不使用“执行某职责”等模板代替功能解释。
- PowerShell 头部依据真实命令、外部连接和失败分支书写；`#requires`、字符串、here-string、参数及退出逻辑保持不变。

本轮只整理改动的注释块，没有运行全仓格式化。独立辅助程序以真实解析器核对 Java/JS AST 与非注释 token、Vue 非脚本块原文、PowerShell token 顺序与换行边界；先用合成案例证明正文变化会被拒绝。该辅助留在本次 V2 检查点，不增加项目依赖、测试框架或常驻门禁。

静态门禁使用现有入口和离线 Maven；936／18 的常量没有修改。结构一致性只能证明本轮没有引入可执行正文差异，不能代替生产构建或运行验证；这些检查留给方面六。实际验收、过程中的辅助修正和文件占用中断见[方面五记录](iteration-summary.md#方面五中文注释审阅与结构对照)。

### 日志脱敏

前端生产环境不输出浏览器控制台。开发环境的
`frontend/src/utils/reportClientError.js` 只记录固定上下文、错误名称和截断后的错误消息；不得输出 Axios headers、请求体、Cookie、Token、响应正文或完整配置。页面仍负责保留原有的用户提示、错误状态和重试行为。

### 生成物和兼容资产

`target`、`node_modules`、`dist`、性能输出和临时扫描缓存属于可重建产物或历史证据，不进入源码格式化。Flyway V1–V7、OpenAPI 契约、RabbitMQ `*.v1` 事件、DTO、Cookie、CSRF 和既有隔离环境数据身份属于兼容资产，不能因为格式或静态引用结果而删除或改写。

### 历史超长活动文件审查记录

核心文件超过 300 行时，优先抽取纯转换、协议、表单、评论或错误处理职责；保留页面/服务作为协调入口。下面保留业务文件的既有职责审查记录；它不是对所有当前文件行数的自动断言。共享 `infrastructure/host/host-environment.ps1` 集中维护配置解析与精确进程身份规则，避免各入口出现安全行为分歧：

| 文件 | 保留原因 |
| --- | --- |
| `backend/src/main/java/com/cc4c/support/cache/BusinessCache.java` | 保留缓存事务、并发加载、失效和故障旁路协调；键与信封已抽取到独立 codec/factory。 |
| `backend/src/main/java/com/cc4c/support/messaging/ReliableMessageProcessor.java` | 保留 ACK/NACK、Inbox/Outbox、事务和重试协调；协议解析与消息构造已抽取。 |
| `backend/src/main/java/com/cc4c/service/CommunityService.java` | 保留博客权限、事务、缓存和仓储协调；响应转换已抽取。 |
| `frontend/src/views/course/CourseView.vue` | 保留课程目录、阅读器和路由协调；评论状态和视图已抽取到共享模块。 |
| `frontend/src/views/admin/AddCourseView.vue` | 保留发布流程和上传/提交协调；基础表单、模块编辑和 Markdown 编辑器已抽取。 |
| `frontend/src/views/blog/BlogDetailView.vue` | 保留博客详情、收藏、评论和导航协调；评论线程已抽取为共享 composable/组件，页面仍负责业务请求和状态组合。 |
| `frontend/src/views/course/CourseDetailView.vue` | 保留课程详情、章节阅读和导航协调；评论线程已抽取为共享 composable/组件，阅读与收藏行为保持在页面入口。 |
| `frontend/src/views/FavoriteView.vue` | 保留课程/博客收藏分页与页面协调；API 调用和用户状态来自共享模块，当前体量仍由双列表交互决定。 |
| `frontend/src/views/admin/CheckBlogView.vue` | 保留审核队列、预览、分页和审核操作协调；Markdown 预览和消息状态已复用共享组件。 |
| `frontend/src/views/course/AllCoursesView.vue` | 保留课程筛选、搜索、分页和路由协调；目录 API 已归入统一 API 模块，页面状态组合不宜继续拆散。 |
| `frontend/src/components/UserInfo.vue` | 保留用户资料页面协调；资料编辑和密码修改对话框已拆为独立组件，父组件继续维护提交、上传和提示状态。 |
| `frontend/src/views/HomeView.vue` | 保留首页课程/博客聚合和推荐展示协调；数据获取已使用统一 API，剩余内容是页面布局与业务状态的紧密组合。 |
| `frontend/src/views/blog/BlogWriteView.vue` | 保留草稿、提交、上传和编辑器协调；Markdown 编辑/预览职责已抽取，草稿请求和路由语义必须由页面统一控制。 |
| `frontend/src/views/blog/BlogManageView.vue` | 保留个人博客列表、草稿和分页协调；API wrapper 已集中，页面仍是多个操作状态的业务入口。 |
| `frontend/src/views/login/Login.vue` | 保留登录、会话恢复、验证码和路由跳转协调；验证码倒计时已抽取为 composable，会话权威仍由 hydrateSession 维护。 |
| `frontend/src/views/login/Register.vue` | 保留注册、验证码和表单校验协调；验证码倒计时已抽取为 composable，字段来源和注册请求体不能继续泛化。 |
| `frontend/src/views/blog/AllBlogsView.vue` | 保留博客筛选、分页和导航协调；社区 API 已集中，剩余代码是页面查询状态和展示规则的组合。 |
| `frontend/src/views/admin/index.vue` | 保留管理端入口和子路由布局；各管理域组件负责自身业务，入口不再承担可独立抽取的请求逻辑。 |
| `frontend/src/layout/Sidebar.vue` | 保留全局导航、权限菜单和响应式布局协调；作为布局组件需要同时处理路由、菜单和移动端展示，拆分会增加状态边界。 |
| `frontend/src/views/UserInfoView.vue` | 保留用户资料路由容器和页面布局协调；实际资料编辑职责已由 `UserInfo.vue` 及共享对话框承担。 |
| `backend/src/main/java/com/cc4c/repository/OutboxRepository.java` | 保留 Outbox SQL、租约和状态更新的一致性边界；仓储语句必须与 Flyway V6/V7 和消息重试语义一起维护，不适合按 SQL 方法拆成多个组件。 |

每次继续拆分都必须先保存行为基线，并以编译、lint、格式检查、API/消息协议静态对照和浏览器人工 smoke 证明等价性，不恢复自动化测试或性能资产。

## 资料与来源

- [OpenAPI 契约快照](../backend/openapi.json)保留原字节及其历史 info.version（3）；它不是重新生成的 V6 实时快照，也不以元数据 3 代替项目版本 6.0.0-SNAPSHOT。
- [十三张原始截图](../frontend/screenshots)只用于文档，不进入应用资源引用；首页仍展示 01、03、08、10 四张。
- 当前架构与运行说明合并自方面三父提交中的目录设计、技术分层、观测、宿主运行、数据库、消息与质量文档；完整 SHA／旧路径和检索命令见[固定来源](iteration-summary.md#固定来源与检索)。
- Flyway 和历史 SQL 是受控资产，真实配置、密码、上传、日志和数据库备份不属于文档附件。启动应用会触发已有 Flyway、Session 和消息行为，不能描述为无数据副作用。

### 设计参考

以下为原目录设计已引用的参考，不代表本轮重新查验外部文档：

- [Spring Boot 代码组织](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)：不强制特定包布局，启动类应位于合适的根包。
- [MyBatis-Plus 持久层接口](https://baomidou.com/guides/data-interface/#mapper-interface)：Mapper 提供数据库访问能力，是本项目 DAO 职责的实现。
- [Spring Modulith 模块定义](https://docs.spring.io/spring-modulith/reference/fundamentals.html)：用于解释 V5 历史模块机制，V6 已移除该依赖。
- [Alibaba Java Coding Guidelines](https://github.com/alibaba/Alibaba-Java-Coding-Guidelines)：作为职责和命名参考，不要求照搬某一目录树。
- [Vue 单文件组件](https://cn.vuejs.org/guide/scaling-up/sfc.html)、[组合式函数](https://cn.vuejs.org/guide/reusability/composables.html)、[状态管理](https://cn.vuejs.org/guide/scaling-up/state-management.html)和[路由](https://cn.vuejs.org/guide/scaling-up/routing.html)：用于区分组件、复用逻辑、共享状态和页面入口。
- [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3)：作为国内公开工程的目录习惯样本。

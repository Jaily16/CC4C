# CC4C 三端目标目录结构设计：精简版

> 状态：目标结构设计稿，尚未实施。
>
> 本文是 [V6 可读性迭代](../v6-iteration-plan.md)方面二的目标目录基线，已批准的精简结构继续保持。V6 六个方面尚未开始，本轮只准备规划文档。
>
> 当前源码仍采用 Spring Modulith，实际实现以[现有模块边界说明](./module-boundaries.md)和源码为准；本文不表示目录重组已经实施。

## 1. 设计原则

后端采用全局技术分层，`com.cc4c` 下只保留 **10 个一级包**；业务名称由类名表达，不再建立业务模块及 `api/internal` 目录。

默认原则是：**先平铺，通过清楚的类名或文件名定位；形成实际规模和稳定职责分组后再增加子目录。**

- 保留 Controller、Service、Mapper、Entity 等容易识别的核心分层。
- Mapper 承担 DAO 职责；已有 JDBC Repository 保持同层，不增加重复包装。
- 小类组直接并入对应层，不预建空的 `aspect`、`impl` 或单文件子目录。
- 业务前端继续使用 Vuex，观测端继续使用 Pinia；两端独立构建和运行。
- 仓库顶层以 `backend`、`frontend`、`observability`、`infrastructure` 和 `docs` 的现有分工为候选；顶层保留和精简清单由 V6 方面一盘点后与用户确认，本文不提前调整。

这是一套针对 CC4C 当前规模和个人维护需求的选择，不表示某种唯一行业标准。

## 2. 后端

### 2.1 目标目录

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

### 2.2 十个包的职责

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

### 2.3 目录合并表

本表左列是上一版设计中的路径，右列是本版唯一目标位置：

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

### 2.4 调用关系和安全边界

```text
Controller → Service → Mapper（DAO）→ 数据库
                   └→ Repository   → 数据库
```

MyBatis 创建 Mapper 的代理实现；`UserMapper extends BaseMapper<UserEntity>` 已是 DAO 层。已有 JDBC 代码继续使用 Repository，不改写访问方式，也不额外增加 `Dao → Mapper` 转发层。

Service 按用例需要调用缓存、邮件、文件和消息设施；消息消费者可以调用 Service 执行业务。已有 Lookup、UseCase 接口保留，不为每个具体服务机械增加接口与 Impl。

目录合并不改变框架机制：认证 Filter 仍在 Spring Security 过滤链中；MVC Interceptor、MyBatis Interceptor 和 AOP Aspect 仍是不同机制，不互相替换。当前没有的切面不预建目录。

业务与观测的认证、Cookie、CSRF、Session 和 namespace 继续分别维护；USER、ADMIN、OBSERVABILITY 的权限语义保持一致。管理端口抓取认证仍独立于观测门户，辅助程序不纳入主应用组件扫描。

## 3. 业务前端

保留 10 个常用基础目录，减少单页面和单文件的额外层级：

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
- CourseBasicsForm、CourseModuleEditor 仅由 AddCourseView 使用，目标为 `views/admin/components`。
- 布局组件平铺到 `layout`；路由仍集中在 `router/index.js`，不预设 routes/public/user/admin 文件。
- Vuex 保留熟悉的 `store/modules` 约定；管理页面直接放在 admin，不再预分 content、users、messaging 子目录。

路由使用懒加载。页面状态尽量留在组件或 composable，确有跨页面共享需求才进入 Store；页面和组件不重复创建 Axios 实例。

## 4. 观测前端

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

取消 charts/status/common 组件子目录和多层 Dashboard 页面目录。三个 API 文件继续保留，数据接口放在 observability.js，不提前拆成 overview/dashboard/operations 请求文件。

继续使用 4 个 Pinia Store 和 usePolling。ChartPanel 保持图表生命周期、ResizeObserver、释放资源和文本替代职责；当前不要求抽出新的图表 composable，也不预建 assets 或 utils。

浏览器只访问后端固定观测接口，不直接访问 Prometheus，不保存 Session Token 或拼接 PromQL。轮询、隐藏暂停、中止过期请求和安全退出行为保持原有约定。

## 5. 当前代码到目标位置的映射

当前源码中的业务模块名称仅用于定位旧文件，未来迁移后按下表归类：

| 当前内容 | 精简后的目标 |
| --- | --- |
| 各业务包中的 Controller | `controller` |
| Service、Lookup、UseCase 接口及现有转换辅助类 | `service`，保留类名和职责 |
| UserMapper、AdministratorMapper、CatalogMapper、BlogMapper、InteractionMapper | `mapper` |
| InboxRepository、OutboxRepository | `repository`，保留 JDBC 实现 |
| 持久化 Entity 或对应数据表的记录 | `entity` |
| CatalogDtos 等已有 DTO 容器、快照、查询 Row、ApiResponse 和分页结构 | `dto`，保留已有嵌套类型 |
| 配置类、所有 Properties、安全链装配 | `config` |
| BusinessException、GlobalExceptionHandler、IntValues、IntValuesValidator、通用错误码和工具 | `common` |
| Principal、角色枚举、认证器、会话安全实现、限流、安全 Filter 和 Handler | `security` |
| BusinessCache、缓存 Store、键工厂和编解码 | `support/cache` |
| 三个 *.v1 事件、消息信封、消息状态、消费者、发布器、可靠投递及清理编排 | `support/messaging`；数据访问仍由 Repository 承担 |
| 指标、采样器、健康贡献者、RequestCorrelationFilter、MybatisMetricsInterceptor、PrometheusClient、ObservabilityCatalog | `support/monitoring` |
| ObservabilityQueryService、ObservabilityDependencyService、登录等用例服务 | `service` |
| FileStorage、OutboundMailSender | `support` |
| 密码迁移、管理员引导和观测密码哈希程序 | 独立 `com.cc4ctools` 根包 |

业务前端的首页、资料页、收藏页和观测端六个页面沿用现有平铺方式。前端主要设计变更集中在共享组件与布局组件合并，以及两个课程表单就近归属；不改变运行资源内容或依赖版本。V6 方面二仅同步两端锁文件中的项目版本元数据，方面四按 V6 规划迁移全部十三张文档截图。

## 6. 后续迁移与文档验收

真实代码迁移属于后续工作，需保留以下核对事项：

1. 处理现有 Spring Modulith 的 `@ApplicationModule`、`@NamedInterface`、allowedDependencies 及不再使用的依赖；package-info 改为技术包职责说明。
2. 更新包级可见性、导入、Bean 名称、扫描路径、Mapper 注册、已有 XML namespace 和硬编码类路径；只扩大必要的成员可见性。
3. 核对 Session 序列化、缓存编解码和消息反序列化兼容性；保持 HTTP API、DTO 字段、Flyway V1–V7 和三个 *.v1 消息协议。
4. 保持业务和观测身份隔离；两端 Vue 的页面、组件归属调整不改变功能、路由地址、状态管理依赖和构建入口。
5. 在未来代码迁移时执行所需质量、生产构建和运行验证；当前文档不宣称这些迁移门禁已经通过。

V6 方面二须特别处理业务 Session JSON 中的旧 Java 类名，以及 MyBatis 指标按旧包名分类的耦合；不能以清空 Redis、降低安全约束或让指标全部进入兜底分类代替兼容处理。既有准确中文注释随源码保留，方面五再补齐和改善说明。

源码目录不容纳真实配置和运行数据。`.env.local`、IDE 私有配置、target、dist、node_modules、上传数据、日志和备份继续被忽略或保存在仓库外；两个 Vue 应用保持独立，不放入后端 resources。

本轮只更新本文和 V6 规划，不移动源码、不更改其他既有架构文档、不运行构建、测试、安装或服务，不暂存、提交或推送。文档验收为：后端恰好 10 个一级包、所有合并映射一致、前端文件均有归属、链接和空白检查通过。

V6 方面四移除本文前，先将本文备份到 V2 的指定位置，再把已批准且验证后的目录设计并入 `docs/project-guide.md`；剩余任务和验收状态由 `docs/iteration-summary.md` 承接。不得先删规划而使后续方面失去交接依据。

检查命令从仓库根目录执行：

```powershell
git diff --check
node .\infrastructure\quality\check-doc-links.mjs
git status --short --untracked-files=all
```

本文当前未跟踪，需额外直接检查全文尾随空白和目录树，不能只依赖 git diff。实际代码完成迁移并验证前，文档始终保留“设计稿”状态。

## 7. 设计依据

- [Spring Boot 代码组织](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)：不强制特定包布局，启动类应位于合适的根包。
- [MyBatis-Plus 持久层接口](https://baomidou.com/guides/data-interface/#mapper-interface)：Mapper 提供数据库访问能力，是本项目 DAO 职责的实现。
- [Spring Modulith 模块定义](https://docs.spring.io/spring-modulith/reference/fundamentals.html)：用于核对当前模块结构与后续迁移事项。
- [Alibaba Java Coding Guidelines](https://github.com/alibaba/Alibaba-Java-Coding-Guidelines)：作为职责和命名参考，不要求照搬某一目录树。
- [Vue 单文件组件](https://cn.vuejs.org/guide/scaling-up/sfc.html)、[组合式函数](https://cn.vuejs.org/guide/reusability/composables.html)、[状态管理](https://cn.vuejs.org/guide/scaling-up/state-management.html)和[路由](https://cn.vuejs.org/guide/scaling-up/routing.html)：用于区分组件、复用逻辑、共享状态和页面入口。
- [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3)：作为国内公开工程的目录习惯样本。

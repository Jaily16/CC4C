# CC4C 代码质量约定

本文档描述当前活动源码的质量门禁。当前版本和工具版本以仓库根目录的
[`versions.yml`](../../versions.yml) 为唯一基线；历史报告、迁移脚本和生成文件不在本规则的格式化范围内。

## 格式范围

- Java 使用 Spotless 2.44.5 和 Palantir Java Format 2.68.0，缩进为四个空格。
- JavaScript、Vue、CSS、JSON、YAML 和 Markdown 使用 Prettier，缩进为两个空格、单引号、保留分号、120 列和 LF 换行。
- PowerShell 和 SQL 的缩进分别为四个空格；所有活动文本文件使用 UTF-8、无 BOM、末尾换行。
- `backend/src/main/resources/db/migration`、`docs/reference/openapi.json`、`docs/history`、锁文件、构建产物和本机配置不做格式化。

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
不安装依赖、不运行自动化测试。[GitHub build 工作流](../../.github/workflows/build.yml)在
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

## 中文 Javadoc 边界

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

## Vue 与 JavaScript 功能边界

两个 ESLint 配置共同加载本地 `eslint-functional-comments.mjs`。每个 `.vue` 组件需要中文职责说明；
具名/导出函数、API wrapper、composable、Pinia/Vuex action、事件处理器、业务派生函数以及生命周期、
`watch`、路由守卫、定时器、事件监听和 Axios 拦截器边界需要紧邻中文说明。

API 注释应区分只读请求和写入副作用，并写清 Session/CSRF 与错误处理责任。局部
`map`/`filter`/`reduce`/`find`/`forEach` 回调、Promise 链回调和懒加载组件由所属语义函数统一说明，
不要求逐行翻译式注释。规则只检查职责说明是否存在，评审仍需核对文字与实现一致。

## PowerShell 功能边界

`check-powershell-quality.ps1` 使用 PowerShell AST 校验语法，不执行被检查脚本。当前 18 个受控脚本必须
同时具有 `运行前提`、`外部依赖`、`破坏性边界`、`失败恢复` 和 `退出码` 五项头部；每个具名函数必须
有紧邻中文说明。检查范围来自 Git 清单，身份不明的路径或 Git 查询失败都会直接阻断。

## 日志脱敏

前端生产环境不输出浏览器控制台。开发环境的
`frontend/src/utils/reportClientError.js` 只记录固定上下文、错误名称和截断后的错误消息；不得输出 Axios headers、请求体、Cookie、Token、响应正文或完整配置。页面仍负责保留原有的用户提示、错误状态和重试行为。

## 生成物和兼容资产

`target`、`node_modules`、`dist`、性能输出和临时扫描缓存属于可重建产物或历史证据，不进入源码格式化。Flyway V1–V7、OpenAPI 契约、RabbitMQ `*.v1` 事件、DTO、Cookie、CSRF 和既有隔离环境数据身份属于兼容资产，不能因为格式或静态引用结果而删除或改写。

## 超长活动文件审查记录

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

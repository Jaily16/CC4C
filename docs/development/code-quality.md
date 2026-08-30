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
cd D:\codex\CC4C_v2\backend
mvn spotless:apply

cd D:\codex\CC4C_v2\frontend
npm run format
npm run lint

cd D:\codex\CC4C_v2
.\scripts\check-code-quality.ps1
```

CI 只执行 `spotless:check`、`npm run lint`、`npm run format:check` 和无依赖源码质量检查，不自动改写文件、不安装额外工具、不读取本机秘密。

## 中文 Javadoc 边界

生产 Java 的公开类型必须使用准确的中文 Javadoc 说明用途、约束、事务边界、幂等、重试、故障旁路、安全脱敏或退出码。注释不改变标识符、HTTP/API 契约、SQL、数据库迁移、消息事件名或日志键。测试代码可以使用测试意图注释，但不以注释代替行为断言。

## 日志脱敏

前端生产环境不输出浏览器控制台。开发环境的
`frontend/src/utils/reportClientError.js` 只记录固定上下文、错误名称和截断后的错误消息；不得输出 Axios headers、请求体、Cookie、Token、响应正文或完整配置。页面仍负责保留原有的用户提示、错误状态和重试行为。

## 生成物和兼容资产

`target`、`node_modules`、`dist`、性能输出和临时扫描缓存属于可重建产物或历史证据，不进入源码格式化。Flyway V1–V7、OpenAPI 契约、RabbitMQ `*.v1` 事件、DTO、Cookie、CSRF 和 Compose `cc4c-v3` 数据身份属于兼容资产，不能因为格式或静态引用结果而删除或改写。

## 超长活动文件审查记录

核心文件超过 300 行时，优先抽取纯转换、协议、表单、评论或错误处理职责；保留页面/服务作为协调入口。当前仍超过 300 行的文件均已完成相应审查：

| 文件 | 保留原因 |
| --- | --- |
| `backend/src/main/java/com/cc4c/shared/BusinessCache.java` | 保留缓存事务、并发加载、失效和故障旁路协调；键与信封已抽取到独立 codec/factory。 |
| `backend/src/main/java/com/cc4c/shared/ReliableMessageProcessor.java` | 保留 ACK/NACK、Inbox/Outbox、事务和重试协调；协议解析与消息构造已抽取。 |
| `backend/src/main/java/com/cc4c/community/internal/CommunityService.java` | 保留博客权限、事务、缓存和仓储协调；响应转换已抽取。 |
| `frontend/src/views/course/CourseView.vue` | 保留课程目录、阅读器和路由协调；评论状态和视图已抽取到共享模块。 |
| `frontend/src/views/admin/AddCourseView.vue` | 保留发布流程和上传/提交协调；基础表单、模块编辑和 Markdown 编辑器已抽取。 |
| `frontend/src/views/blog/BlogDetailView.vue` | 保留博客详情、收藏、评论和导航协调；评论线程已抽取为共享 composable/组件，页面仍负责业务请求和状态组合。 |
| `frontend/src/views/course/CourseDetailView.vue` | 保留课程详情、章节阅读和导航协调；评论线程已抽取为共享 composable/组件，阅读与收藏行为保持在页面入口。 |
| `frontend/src/views/FavoriteView.vue` | 保留课程/博客收藏分页与页面协调；API 调用和用户状态来自共享模块，当前体量仍由双列表交互决定。 |
| `frontend/src/views/admin/CheckBlogView.vue` | 保留审核队列、预览、分页和审核操作协调；Markdown 预览和消息状态已复用共享组件。 |
| `frontend/src/views/course/AllCoursesView.vue` | 保留课程筛选、搜索、分页和路由协调；目录 API 已归入统一 API 模块，页面状态组合不宜继续拆散。 |
| `frontend/src/components/common/UserInfo.vue` | 保留用户资料页面协调；资料编辑和密码修改对话框已拆为独立组件，父组件继续维护提交、上传和提示状态。 |
| `frontend/src/views/HomeView.vue` | 保留首页课程/博客聚合和推荐展示协调；数据获取已使用统一 API，剩余内容是页面布局与业务状态的紧密组合。 |
| `frontend/src/views/blog/BlogWriteView.vue` | 保留草稿、提交、上传和编辑器协调；Markdown 编辑/预览职责已抽取，草稿请求和路由语义必须由页面统一控制。 |
| `frontend/src/views/blog/BlogManageView.vue` | 保留个人博客列表、草稿和分页协调；API wrapper 已集中，页面仍是多个操作状态的业务入口。 |
| `frontend/src/views/login/Login.vue` | 保留登录、会话恢复、验证码和路由跳转协调；验证码倒计时已抽取为 composable，会话权威仍由 hydrateSession 维护。 |
| `frontend/src/views/login/Register.vue` | 保留注册、验证码和表单校验协调；验证码倒计时已抽取为 composable，字段来源和注册请求体不能继续泛化。 |
| `frontend/src/views/blog/AllBlogsView.vue` | 保留博客筛选、分页和导航协调；社区 API 已集中，剩余代码是页面查询状态和展示规则的组合。 |
| `frontend/src/views/admin/index.vue` | 保留管理端入口和子路由布局；各管理域组件负责自身业务，入口不再承担可独立抽取的请求逻辑。 |
| `frontend/src/layout/components/Sidebar.vue` | 保留全局导航、权限菜单和响应式布局协调；作为布局组件需要同时处理路由、菜单和移动端展示，拆分会增加状态边界。 |
| `frontend/src/views/UserInfoView.vue` | 保留用户资料路由容器和页面布局协调；实际资料编辑职责已由 `UserInfo.vue` 及共享对话框承担。 |
| `backend/src/main/java/com/cc4c/shared/OutboxRepository.java` | 保留 Outbox SQL、租约和状态更新的一致性边界；仓储语句必须与 Flyway V6/V7 和消息重试语义一起维护，不适合按 SQL 方法拆成多个组件。 |
| `backend/src/test/java/com/cc4c/performance/PerformanceBenchmarkApplication.java`、`PerformanceDataSeeder.java` | 性能测试工具的启动配置与数据语义必须保持完整，不属于生产业务组件。 |
| `scripts/check-versions.mjs`、`scripts/check-structure.mjs` | 无依赖检查器集中定义受控路径、兼容例外和错误契约；拆分会削弱单一门禁入口。 |

每次继续拆分都必须先保存行为基线，并以现有测试、API 快照、消息协议测试和前端页面回归证明等价性。

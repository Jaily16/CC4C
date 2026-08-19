# CC4C V2 页面美化与用户友好性迭代开发计划（Implementation Plan）

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`（若当前环境提供）或 `superpowers:executing-plans`，逐任务执行；所有步骤均使用 checkbox（`- [ ]`）追踪。

**Goal:** 在不改变 V1 已验收业务功能和后端接口契约的前提下，按页面逐步完成 CC4C 的视觉统一、响应式适配、状态反馈与操作可发现性优化。

**Architecture:** 以现有 Vue 3 + Element Plus 单页应用为基础，先提供全局设计令牌、页面容器和通用反馈组件，再按“认证 → 内容发现 → 课程 → 博客 → 个人中心 → 管理端”的真实用户旅程改造页面。页面继续复用现有 Axios 接口、Vuex 用户状态和 Vue Router 查询参数，不新增后端接口、不改变请求字段或成功判定规则。

**Tech Stack:** Vue 3、`<script setup>`、Vue Router、Vuex、Element Plus、Axios、`md-editor-v3`、Vite、Spring Boot 2.6.11（仅用于回归验证）。

## 全局约束

- 本次 V2 只优化前端页面视觉、交互与使用友好性；除非出现阻塞性缺陷，不新增业务功能和后端接口。
- 保持 V1 已验证的 API 地址、请求参数、响应成功判定和 Cookie 登录态行为不变。
- 新增和修改的 Vue 组件使用 `<script setup>`、明确的 `props`/`emits`，不在 `script setup` 中使用 `this`。
- 页面必须在 1440px、1024px、768px 和 375px 宽度下可阅读、可操作且无横向溢出。
- 所有异步页面必须提供加载、空数据、错误和重试中的至少适用状态；不得以空白页面、静默失败或浏览器 `alert` 作为反馈。
- 表单必须有可见标签或可访问名称，支持键盘提交；主要按钮、链接和图标按钮必须有可见焦点状态。
- V1 的 17 个后端功能测试和前端 `npm run build` 是 V2 的最低回归门槛；页面任务完成后必须做对应浏览器功能回归。
- 不提交或打印本机密钥：`back-end/CC4C/src/main/resources/application.yml` 必须保持 Git 忽略；只提交脱敏的 `application-example.yml`。
- 禁止提交 `front-end/CC4C/node_modules/`、`front-end/CC4C/dist/`、`back-end/CC4C/target/`、`temp/`。
- 每个任务结束前仅暂存该任务明确列出的文件，执行 `git diff --cached` 后再提交；未经用户明确要求，不合并 PR、不重写历史、不推送本机配置。

---

## 0. 当前基线、范围与页面地图

### V1 基线

| 项目 | 当前约定 |
| --- | --- |
| 基线分支 | `codex/cc4c-functional-stability-iteration` |
| 基线提交 | `bf810a6` |
| 现有 PR | GitHub PR #4，记录时为草稿；开始 V2 前先确认它是否已合并 |
| 用户端壳层 | `front-end/CC4C/src/layout/index.vue`、`layout/components/header.vue`、`layout/components/sliber.vue` |
| 公共内容页面 | 首页、课程列表/详情、博客列表/详情、收藏、个人中心 |
| 认证页面 | 用户登录、注册、管理员登录 |
| 管理端页面 | 后台概览、课程新增、博客审核 |

### V2 页面优先级

| 优先级 | 页面/区域 | V2 目标 |
| --- | --- | --- |
| P0 | 全局样式、顶部导航、侧栏、页面状态 | 建立统一视觉、信息层级、响应式容器和反馈基线 |
| P0 | 登录、注册、首页 | 降低首次使用门槛，清晰展示入口、状态与下一步操作 |
| P1 | 课程列表、课程详情 | 提升检索、内容阅读、收藏与评论的可发现性 |
| P1 | 博客列表、博客详情、写作/管理 | 提升浏览、阅读、写作和审核前后反馈的一致性 |
| P1 | 个人中心、收藏 | 让资料维护、密码修改、头像上传和空收藏更易理解 |
| P2 | 管理端 | 统一后台导航、数据浏览、课程发布和审核体验 |
| P2 | 无障碍、移动端、视觉回归 | 消除横向溢出、键盘障碍、低对比度和页面状态缺失 |

### 文件结构与职责

| 文件 | V2 职责 |
| --- | --- |
| `front-end/CC4C/src/assets/main.css` | 全局 reset、字体、颜色、间距、响应式基础规则 |
| `front-end/CC4C/src/styles/design-tokens.css`（新建） | CSS 变量、阴影、圆角、过渡和断点令牌 |
| `front-end/CC4C/src/components/common/PageFeedback.vue`（新建） | 加载、空数据、错误和重试的通用展示 |
| `front-end/CC4C/src/components/common/ContentActionBar.vue`（新建） | 内容详情页的收藏、评论、登录引导动作区 |
| `front-end/CC4C/src/layout/**` | 用户端页面壳层、导航与内容区布局 |
| `front-end/CC4C/src/views/login/**` | 用户认证页面体验 |
| `front-end/CC4C/src/views/course/**` | 课程发现与课程阅读体验 |
| `front-end/CC4C/src/views/blog/**` | 博客发现、阅读、写作和管理体验 |
| `front-end/CC4C/src/views/HomeView.vue` | 首次登录后的信息呈现和内容推荐 |
| `front-end/CC4C/src/views/UserinfoView.vue`、`FavoriteView.vue`、`components/UserInfo.vue` | 个人中心与收藏体验 |
| `front-end/CC4C/src/views/admin/**` | 管理端布局与操作反馈 |
| `docs/CC4C项目迭代修改记录.md` | V1/V2 变更、验证和发布记录 |
| `docs/CC4C第二次迭代开发计划.md` | V2 任务状态、约束和交接提示词 |

---

### Task 1: 建立全局视觉基础、响应式壳层与通用页面反馈

**Files:**

- Create: `front-end/CC4C/src/styles/design-tokens.css`
- Create: `front-end/CC4C/src/components/common/PageFeedback.vue`
- Modify: `front-end/CC4C/src/assets/main.css`
- Modify: `front-end/CC4C/src/App.vue`
- Modify: `front-end/CC4C/src/layout/index.vue`
- Modify: `front-end/CC4C/src/layout/components/header.vue`
- Modify: `front-end/CC4C/src/layout/components/sliber.vue`

**Interfaces:**

- `PageFeedback.vue` 接收 `loading:Boolean`、`empty:Boolean`、`error:String`、`emptyTitle:String`、`emptyDescription:String` 和 `retryText:String`；在错误状态点击按钮时触发 `retry`。
- 所有用户端页面统一使用 CSS 变量：`--cc4c-primary:#2563eb`、`--cc4c-bg:#f5f7fb`、`--cc4c-surface:#ffffff`、`--cc4c-text:#172033`、`--cc4c-muted:#64748b`、`--cc4c-border:#e2e8f0`、`--cc4c-radius:12px`、`--cc4c-shadow:0 12px 28px rgba(15, 23, 42, .08)`。
- `layout/index.vue` 保持现有子路由渲染和侧栏功能，但内容区最小宽度为 `0`，在 768px 以下将侧栏改为可折叠或不遮挡正文的紧凑模式。

- [ ] **Step 1: 记录改造前的视觉与布局基线**

在浏览器分别打开 `/home`、`/allCourses`、`/courseDetail?courseName=黑马_20天学会JAVA`、`/allBlogs`、`/userinfo`，记录以下问题并保存为本任务的验收截图：顶部导航固定宽度、页面内联样式数量、正文横向溢出、空数据时的表现、加载时的表现。

- [ ] **Step 2: 新建设计令牌与全局基础样式**

创建 `src/styles/design-tokens.css`，写入下列令牌并在 `main.css` 导入：

```css
:root {
  --cc4c-primary: #2563eb;
  --cc4c-primary-hover: #1d4ed8;
  --cc4c-bg: #f5f7fb;
  --cc4c-surface: #ffffff;
  --cc4c-text: #172033;
  --cc4c-muted: #64748b;
  --cc4c-border: #e2e8f0;
  --cc4c-radius: 12px;
  --cc4c-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
}
```

在 `main.css` 增加 `box-sizing:border-box`、`body` 背景色、最小宽度、中文优先字体栈、图片自适应和 `:focus-visible` 焦点样式；不得设置会导致移动端裁切的全局固定宽度。

- [ ] **Step 3: 实现通用 PageFeedback 组件**

以 `ElSkeleton`、`ElEmpty`、`ElAlert` 和 `ElButton` 实现四种状态，组件必须允许页面通过默认插槽渲染正常内容：

```vue
<PageFeedback
  :loading="loading"
  :empty="!loading && items.length === 0"
  :error="errorMessage"
  empty-title="暂无课程"
  empty-description="可以切换语言或调整搜索关键词"
  @retry="loadCourses"
>
  <!-- 正常列表内容 -->
</PageFeedback>
```

- [ ] **Step 4: 改造用户端壳层与导航**

将 `header.vue` 的内联网格、硬编码 `aliceblue` 和登录状态 `this.$store` 替换为 `store.state.user`、具名类和响应式导航；当前路由必须有可见激活状态。保留“主页、所有课程、所有博客、登录/退出”入口，移动端不得使 Logo、导航和退出按钮重叠。

在 `layout/index.vue` 为内容区增加 `min-width: 0`、滚动容器和统一背景；保留 `router-view` 与现有 `Sliber` 功能，不修改路由 URL。

- [ ] **Step 5: 执行任务级验证并提交**

在 1440px、1024px、768px、375px 检查 `/home` 与 `/allCourses`：无横向滚动条、键盘 Tab 可见焦点、导航可点击、登录/退出行为不回归。执行：

```powershell
cd front-end/CC4C
npm run build
```

通过后仅暂存本任务列出的文件：

```powershell
git add front-end/CC4C/src/styles/design-tokens.css front-end/CC4C/src/components/common/PageFeedback.vue front-end/CC4C/src/assets/main.css front-end/CC4C/src/App.vue front-end/CC4C/src/layout
git diff --cached
git commit -m "feat: establish responsive visual foundation"
```

---

### Task 2: 优化登录、注册与首次使用引导

**Files:**

- Modify: `front-end/CC4C/src/views/login/Login.vue`
- Modify: `front-end/CC4C/src/views/login/Register.vue`
- Modify: `front-end/CC4C/src/views/admin/AdminLoginView.vue`
- Reuse: `front-end/CC4C/src/styles/design-tokens.css`

**Interfaces:**

- 继续调用现有用户登录、注册、验证码和管理员登录接口；不得修改接口路径、Cookie 名称或 Vuex 写入字段。
- 所有提交按钮以本地 `ref(false)` 的 `submitting` 状态防止重复提交；接口完成后在 `finally` 中恢复。
- 输入框必须具有 `autocomplete`、明确标签、回车提交和失败后保留用户已输入内容。

- [ ] **Step 1: 定义认证页统一信息层级**

把三页统一为“品牌区 + 表单卡片 + 帮助操作”布局：品牌区说明 CC4C 的学习/交流价值，表单卡片只放当前任务所需字段，底部放注册、返回用户登录或返回管理员登录的明确链接。保留原有路由 `/login`、`/register`、`/adminLogin`。

- [ ] **Step 2: 增加表单提交与字段反馈状态**

为 `Login.vue` 和 `Register.vue` 的账号、邮箱、密码、验证码字段提供实时而不过度打扰的校验文案；提交时按钮显示“登录中…”或“注册中…”。提交失败使用 `ElMessage` 和字段附近错误提示，不能只在控制台输出错误。

- [ ] **Step 3: 修复认证页键盘与辅助操作体验**

设置登录表单 `@submit.prevent`，令 Enter 键等效触发登录；密码框保留显示/隐藏能力；注册链接、找回密码入口和管理员入口必须以文本链接呈现，不能依赖图标或颜色单独传达含义。

- [ ] **Step 4: 优化管理员登录但不扩大权限范围**

将管理员登录页套用同一视觉令牌与错误/加载反馈；登录失败留在当前页并显示服务器消息，成功后保持原有后台跳转地址。不要修改管理员验证 API、Cookie 或权限判断逻辑。

- [ ] **Step 5: 执行认证回归并提交**

验证错误密码、空表单、无效邮箱、重复注册、正确用户登录、正确管理员登录、退出后再次访问用户页等路径。执行 `npm run build`，并只暂存三个认证页面后提交：

```powershell
git add front-end/CC4C/src/views/login/Login.vue front-end/CC4C/src/views/login/Register.vue front-end/CC4C/src/views/admin/AdminLoginView.vue
git diff --cached
git commit -m "feat: improve authentication usability"
```

---

### Task 3: 优化首页、课程发现与博客发现页面

**Files:**

- Modify: `front-end/CC4C/src/views/HomeView.vue`
- Modify: `front-end/CC4C/src/views/course/AllCoursesView.vue`
- Modify: `front-end/CC4C/src/views/course/CourseView.vue`
- Modify: `front-end/CC4C/src/views/blog/AllBlogsView.vue`
- Modify: `front-end/CC4C/src/views/blog/BlogView.vue`
- Reuse: `front-end/CC4C/src/components/common/PageFeedback.vue`

**Interfaces:**

- 继续使用 `/courses/home`、`/courses/search/{info}`、`/courses/language/{name}`、`/blogs/home` 及各页面现有博客列表接口。
- 课程详情导航继续传递 `courseName`，博客详情继续传递 `blogId`；进入详情前保留现有浏览量请求。
- 列表项使用稳定唯一键（课程 `courseId`，博客 `blogId`），不得依赖数组下标。

- [ ] **Step 1: 改造首页为清晰的推荐与继续浏览入口**

将首页拆成“欢迎/推荐说明、合作伙伴轮播、课程推荐、博客推荐”四个视觉区块；课程卡片展示语言、标题和难度，博客行展示作者、标题、发布时间和摘要（仅当接口已有字段时显示）。为每个区块提供加载骨架、空状态和“查看全部”按钮。

- [ ] **Step 2: 优化课程筛选与搜索流程**

在 `AllCoursesView.vue` 中将语言筛选、搜索框和结果统计置于同一个可换行工具栏；搜索框增加清除动作，清除后恢复当前选中语言的课程；搜索结果为空时显示 `PageFeedback`，不要保留固定 `height:1000px` 的空白容器。

- [ ] **Step 3: 优化博客列表的扫描效率**

在 `AllBlogsView.vue` 和 `BlogView.vue` 中使用统一列表卡片，确保标题是主要视觉层级、作者/时间/浏览信息为次要层级，点击整张卡片和键盘 Enter 均能进入详情；加载失败时显示可重试反馈。

- [ ] **Step 4: 完成发现页面的移动端布局**

在 768px 以下将课程网格改为 2 列，在 480px 以下改为 1 列；首页轮播高度使用 `clamp(180px, 35vw, 350px)`；博客列表元数据自动换行，所有“更多”链接保持可点击面积不少于 36px。

- [ ] **Step 5: 执行发现路径回归并提交**

验证首页推荐加载、课程语言切换、关键词搜索/清除、课程详情跳转、博客详情跳转、空结果和网络失败提示。执行 `npm run build` 后提交：

```powershell
git add front-end/CC4C/src/views/HomeView.vue front-end/CC4C/src/views/course/AllCoursesView.vue front-end/CC4C/src/views/course/CourseView.vue front-end/CC4C/src/views/blog/AllBlogsView.vue front-end/CC4C/src/views/blog/BlogView.vue
git diff --cached
git commit -m "feat: improve content discovery pages"
```

---

### Task 4: 优化课程详情的阅读、收藏与评论体验

**Files:**

- Create: `front-end/CC4C/src/components/common/ContentActionBar.vue`
- Modify: `front-end/CC4C/src/views/course/CourseDetailView.vue`
- Reuse: `front-end/CC4C/src/components/common/PageFeedback.vue`

**Interfaces:**

- `ContentActionBar.vue` 接收 `collected:Boolean`、`loggedIn:Boolean`、`commentOpen:Boolean`；触发 `toggle-collect`、`toggle-comment`、`require-login`。组件不直接发送 Axios 请求。
- `CourseDetailView.vue` 保留现有课程内容、目录、收藏、评论和回复接口；收藏成功后才更新 `isFavor`，评论/回复成功后重新加载评论列表。

- [ ] **Step 1: 处理课程加载和不存在课程状态**

使用 `PageFeedback` 包裹课程正文和侧栏；路由中缺少 `courseName`、接口返回空内容或请求失败时显示“返回所有课程”入口，而不是渲染空白 Markdown 编辑器。

- [ ] **Step 2: 改造阅读布局与目录定位**

正文区域使用可读最大宽度、至少 1.7 的正文行高和合理标题间距；桌面端目录保持在右侧，1024px 以下移到正文上方或折叠区，移动端不显示固定高度 1000px 容器。保持 `md-editor-v3` 的 `previewOnly`、`editorId` 和目录跳转行为。

- [ ] **Step 3: 接入通用动作栏**

使用 `ContentActionBar` 替换课程详情中分散的收藏/评论图标按钮。未登录用户点击收藏时提示登录并提供 `/login` 跳转；登录用户可看到收藏文字和当前状态；评论按钮显示已展开/收起状态。

- [ ] **Step 4: 优化评论树可读性与操作反馈**

为一级评论、回复、作者、发布时间和回复输入框建立清楚的缩进与边界；空评论提交在输入框附近提示；回复成功后焦点返回新回复区域；二级回复限制继续由后端决定，前端只展示后端失败消息。

- [ ] **Step 5: 执行课程详情回归并提交**

验证课程不存在、未登录浏览、登录后收藏/取消收藏、评论、一级回复、二级回复、空评论、窄屏目录与返回列表。执行 `npm run build` 后提交：

```powershell
git add front-end/CC4C/src/components/common/ContentActionBar.vue front-end/CC4C/src/views/course/CourseDetailView.vue
git diff --cached
git commit -m "feat: improve course reading experience"
```

---

### Task 5: 优化博客浏览、详情、写作与管理体验

**Files:**

- Modify: `front-end/CC4C/src/views/blog/BlogDetailView.vue`
- Modify: `front-end/CC4C/src/views/blog/BlogWriteView.vue`
- Modify: `front-end/CC4C/src/views/blog/BlogManageView.vue`
- Reuse: `front-end/CC4C/src/components/common/ContentActionBar.vue`
- Reuse: `front-end/CC4C/src/components/common/PageFeedback.vue`

**Interfaces:**

- 博客详情继续使用现有 `blogId` 查询参数、点击量、收藏、评论和回复接口。
- 写作页保留已有 `md-editor-v3` 上传图片、发布、草稿及语言选择请求；发布/草稿成功后继续使用已验证的清空或跳转行为。
- 管理页继续按现有接口读取用户博客，不修改审核状态或删除语义。

- [ ] **Step 1: 统一博客详情与课程详情的阅读动作**

将博客标题、作者、发布时间、语言、浏览量、正文、收藏和评论区按阅读优先级重新排列；复用 `ContentActionBar`，确保博客收藏/评论/登录引导的行为和课程一致。

- [ ] **Step 2: 提升博客写作过程的可预期性**

为标题、语言、正文和提交动作加入可见必填标记与字符/状态提示；图片上传中显示上传状态；草稿/发布按钮禁用重复点击；失败时保留编辑内容并提供后端消息，成功时明确说明“已保存草稿”或“已提交审核”。

- [ ] **Step 3: 改善我的博客管理与空状态**

将博客管理页改为状态清晰的卡片或表格：显示标题、状态、更新时间/发布时间、可执行操作；无博客、加载失败、被驳回等状态要有说明和下一步入口（例如“去写博客”）。

- [ ] **Step 4: 验证博客全路径的窄屏可用性**

在 375px 下测试编辑器工具栏、标题输入、语言选择、操作按钮、详情评论和管理列表；不允许编辑器外层出现页面级横向滚动，按钮应自动换行或全宽展示。

- [ ] **Step 5: 执行博客回归并提交**

验证公开博客浏览、浏览量请求、收藏/取消收藏、评论/回复、草稿保存、发布、发布失败反馈、图片上传失败反馈和个人博客管理。执行 `npm run build` 后提交：

```powershell
git add front-end/CC4C/src/views/blog/BlogDetailView.vue front-end/CC4C/src/views/blog/BlogWriteView.vue front-end/CC4C/src/views/blog/BlogManageView.vue
git diff --cached
git commit -m "feat: improve blog creation and reading experience"
```

---

### Task 6: 优化个人中心、收藏与账户维护体验

**Files:**

- Modify: `front-end/CC4C/src/components/UserInfo.vue`
- Modify: `front-end/CC4C/src/views/UserinfoView.vue`
- Modify: `front-end/CC4C/src/views/FavoriteView.vue`
- Reuse: `front-end/CC4C/src/components/common/PageFeedback.vue`

**Interfaces:**

- 继续使用 Vuex 的 `user.id`、`name`、`email`、`major`、`language`、`avatar` 字段和既有用户资料/密码/头像接口。
- 资料、密码和头像成功后仍重新读取用户信息并同步 Vuex；不得通过整页刷新同步状态。
- 收藏列表继续使用既有课程、博客收藏接口，点击内容保留 V1 的详情跳转参数。

- [ ] **Step 1: 建立个人中心信息架构**

把个人信息、收藏和我的文章入口改为清晰的二级导航，当前项有文字和色彩双重标识；资料卡展示头像、用户名、邮箱、专业和订阅语言，移动端从横向布局改为纵向布局。

- [ ] **Step 2: 优化资料、密码和头像对话框**

对话框中为每个输入字段提供标签、帮助文本和错误提示；头像上传显示格式/大小要求、上传中状态和预览；密码修改成功后清空敏感输入，失败时不清空用户已输入内容。

- [ ] **Step 3: 优化收藏中心的内容与空状态**

课程收藏和博客收藏以明确的切换标签呈现，显示数量和内容类型；没有收藏时显示“浏览课程”或“浏览博客”操作；请求失败时提供重试，不显示空白区域。

- [ ] **Step 4: 保障操作完成后的上下文连续性**

资料保存、头像上传、密码修改、切换收藏类型和打开收藏内容后，用户应留在预期页面；所有成功/失败反馈使用统一 `ElMessage` 文案和相同的颜色语义。

- [ ] **Step 5: 执行账户路径回归并提交**

验证用户资料回显、资料修改、密码校验、头像格式/大小校验、收藏切换、空收藏、收藏详情跳转和退出登录。执行 `npm run build` 后提交：

```powershell
git add front-end/CC4C/src/components/UserInfo.vue front-end/CC4C/src/views/UserinfoView.vue front-end/CC4C/src/views/FavoriteView.vue
git diff --cached
git commit -m "feat: improve profile and favorites usability"
```

---

### Task 7: 优化管理端导航、审核与课程发布反馈

**Files:**

- Modify: `front-end/CC4C/src/views/admin/index.vue`
- Modify: `front-end/CC4C/src/views/admin/CoursesAndBlogsView.vue`
- Modify: `front-end/CC4C/src/views/admin/AddCourseView.vue`
- Modify: `front-end/CC4C/src/views/admin/CheckBlogView.vue`

**Interfaces:**

- 保持 `/admin/verify`、管理员登录、课程模块/课程发布、博客审核接口及原有返回判断不变。
- 管理端鉴权失败使用 `ElMessage` 与 Vue Router 跳转 `/adminLogin`，不得使用 `alert` 或 `window.location.href`。
- 课程发布和博客审核成功后继续局部刷新数据，不使用整页刷新。

- [ ] **Step 1: 重构管理端壳层与当前菜单状态**

使用固定的后台标题栏、侧边导航和主内容区；菜单项显示图标和文字，当前路由高亮；1024px 以下侧栏可收起或变为顶部菜单，正文不被遮挡。

- [ ] **Step 2: 改善概览页的数据阅读与空状态**

在 `CoursesAndBlogsView.vue` 中为课程、博客统计/列表建立标题、数量、状态和空数据说明；表格或卡片在窄屏下可横向滚动容器内滚动，而不是让整个页面溢出。

- [ ] **Step 3: 优化新增课程表单流程**

将标题、难度、语言模块、正文按步骤分组，标明必填项；模块加载时显示加载反馈；语言模块为空或请求失败时禁用发布；发布成功后显示明确结果并保留 V1 的表单清空行为。

- [ ] **Step 4: 优化博客审核的决定反馈**

审核列表中突出标题、作者、提交时间和当前待审核状态；通过/驳回操作提供确认提示和进行中状态，接口失败显示后端消息且不从列表中错误移除项目。

- [ ] **Step 5: 执行管理员路径回归并提交**

验证未登录进入管理端、错误/正确管理员登录、课程模块加载/新增、课程发布、博客通过、博客驳回和列表局部刷新。执行 `npm run build` 后提交：

```powershell
git add front-end/CC4C/src/views/admin/index.vue front-end/CC4C/src/views/admin/CoursesAndBlogsView.vue front-end/CC4C/src/views/admin/AddCourseView.vue front-end/CC4C/src/views/admin/CheckBlogView.vue
git diff --cached
git commit -m "feat: improve admin workflow usability"
```

---

### Task 8: 完成跨页面可访问性、响应式与视觉回归

**Files:**

- Modify: 仅修改任务 1–7 中确有回归问题的页面文件
- Modify: `front-end/CC4C/FUNCTIONAL_TEST_REPORT.md`
- Modify: `docs/CC4C项目迭代修改记录.md`

**Interfaces:**

- 不新增接口、不更改后端返回结构、不重置 Vuex 登录状态。
- 所有页面沿用 `PageFeedback`、设计令牌和已建立的按钮/表单语义；不重新引入内联硬编码背景色、固定高度或浏览器原生 `alert`。

- [ ] **Step 1: 执行四个断点的全路由检查**

在 1440px、1024px、768px、375px 依次检查：`/login`、`/register`、`/home`、`/allCourses`、`/courseDetail`、`/allBlogs`、`/blogDetail`、`/userinfo`、`/favorite`、`/blogWrite`、`/blogmanage` 和三个管理员子页。记录横向滚动、截断文字、重叠按钮和无法点击元素。

- [ ] **Step 2: 执行键盘与焦点检查**

每页用 Tab/Shift+Tab 检查导航、搜索、筛选、主操作、对话框和关闭按钮；使用 Enter 提交登录/注册/搜索；确认焦点可见、顺序符合阅读顺序，图标按钮具有 `aria-label` 或等效文字。

- [ ] **Step 3: 执行加载、空数据与失败反馈检查**

使用不存在的课程名、无匹配搜索词、没有收藏的用户或浏览器网络离线模拟，确认每个数据页面显示加载骨架、空状态或重试反馈，不出现空白容器、未捕获异常或无限加载。

- [ ] **Step 4: 更新测试与迭代记录**

在 `FUNCTIONAL_TEST_REPORT.md` 记录 V2 新增的断点、键盘、空状态和失败状态验证；在《CC4C 项目迭代修改记录》中新增“第二次迭代”小节，列出实际完成任务、页面、截图位置、构建结果和遗留问题。

- [ ] **Step 5: 完成全量构建与回归提交**

执行：

```powershell
cd front-end/CC4C
npm run build
cd ../../back-end/CC4C
mvn test
```

确认前端构建成功、后端 17/17 通过、关键浏览器路径无 console error/warn 后，暂存实际修复页及两份文档并提交：

```powershell
git add front-end/CC4C docs/CC4C项目迭代修改记录.md front-end/CC4C/FUNCTIONAL_TEST_REPORT.md
git diff --cached
git commit -m "test: complete v2 usability regression"
```

---

### Task 9: 安全 GitHub 发布与本地运行保护

**Files:**

- Modify: 仅 V2 实际已验证的前端源文件、测试报告和文档
- Never stage: `back-end/CC4C/src/main/resources/application.yml`
- Maintain: `back-end/CC4C/src/main/resources/application-example.yml`（仅配置结构改变时更新）

**Interfaces:**

- 本机开发继续直接运行 `mvn spring-boot:run`，读取被忽略的真实 `application.yml`。
- GitHub 仓库只保留 `application-example.yml`，其中所有敏感项继续使用环境变量占位符。
- 前端依赖、构建目录、后端构建目录和临时目录永远不进入暂存区。

- [ ] **Step 1: 确认 V2 分支基线**

先确认 PR #4 是否已合并：若已合并，从最新 `main` 新建 `codex/cc4c-v2-ui-ux-iteration`；若未合并，从 `codex/cc4c-functional-stability-iteration` 新建同名 V2 分支，确保 V2 包含 V1 基线。不要在 `main` 直接开发。

- [ ] **Step 2: 执行发布前敏感信息与配置检查**

执行下列命令，并确认真实本机配置不在 Git 跟踪列表中：

```powershell
git status -sb
git check-ignore -v back-end/CC4C/src/main/resources/application.yml
git ls-files back-end/CC4C/src/main/resources/application*.yml
git diff --cached
```

期望结果是：`application.yml` 被 `.gitignore` 命中，跟踪列表中只有 `application-example.yml`；暂存区不得出现数据库密码、SMTP 授权码、真实邮箱账号或本机绝对私密路径。

- [ ] **Step 3: 只显式暂存当前任务的公开文件**

禁止使用 `git add -A`。示例：当任务只修改首页和设计系统时，使用：

```powershell
git add front-end/CC4C/src/views/HomeView.vue front-end/CC4C/src/assets/main.css front-end/CC4C/src/styles/design-tokens.css
git diff --cached --check
git diff --cached
```

发现无关文件、秘密或构建产物时，停止发布并从暂存区移除该文件；不要删除本机配置文件。

- [ ] **Step 4: 验证后提交、推送并更新草稿 PR**

仅在 `npm run build` 和相关浏览器回归通过后提交。推送前确认当前分支，推送后在 GitHub 更新或新建草稿 PR；PR 描述必须写明页面范围、用户体验改善、验证命令和不包含本机配置的安全措施。

```powershell
git status -sb
git push -u origin codex/cc4c-v2-ui-ux-iteration
```

- [ ] **Step 5: 发布后的本机运行复核**

推送完成后不修改本机 `application.yml`，执行：

```powershell
cd back-end/CC4C
mvn spring-boot:run
```

确认后端可启动且 `http://localhost:4080/courses/home` 返回成功；随后在前端执行 `npm run dev`，验证 UI 未因发布操作失效。

---

## 自检结果

| 检查项 | 结论 |
| --- | --- |
| 页面逐页优化 | Tasks 2–7 覆盖认证、首页、课程、博客、个人中心和管理端 |
| 全局视觉与可维护性 | Task 1 提供设计令牌、壳层和通用页面反馈 |
| 用户友好性 | 每个页面任务包含加载、空数据、失败、键盘或操作反馈要求 |
| 移动端与无障碍 | Task 1、3、4、5、8 覆盖四个断点、焦点和语义检查 |
| V1 功能保护 | 全局约束、任务接口和回归步骤禁止变更现有 API 契约 |
| 安全 GitHub 上传与本机运行 | Task 9 明确忽略本机 `application.yml`、显式暂存和发布后启动复核 |
| 不开始执行 V2 | 本文仅是计划；本会话不修改任何前端业务页面 |

## 新会话交接提示词

### 第一条提示词：只理解任务，不开始执行

```text
你正在接手 CC4C 项目的第二次迭代开发。请先只读并理解以下两份文档：
1. docs/CC4C项目迭代修改记录.md（重点阅读第 11 节 V1 发布基线与本地配置约定）
2. docs/CC4C第二次迭代开发计划.md

当前 GitHub 已提交的 V1 基线是 bf810a6，V2 聚焦页面美化与用户友好性，不能改变 V1 已通过的前后端功能和 API 契约。请特别记住：本机真实配置是 back-end/CC4C/src/main/resources/application.yml，已被 Git 忽略；GitHub 只允许提交脱敏的 application-example.yml，不能提交 node_modules、dist、target 或 temp。

本条消息只要求你进行只读检查并回复：你理解的项目现状、V2 任务顺序、第一项任务的边界、验证方式和安全发布规则。不要修改文件、不要执行测试、不要启动或停止服务、不要执行 Git 暂存/提交/推送，也不要开始 Task 1。等待我的下一条指令。
```

### 第二条提示词：开始执行第一个任务

```text
现在开始执行 docs/CC4C第二次迭代开发计划.md 中的 Task 1“建立全局视觉基础、响应式壳层与通用页面反馈”。先检查当前工作区和相关文件，再按任务中的步骤完成实现与验证；只处理 Task 1，不提前开始 Task 2。

必须保持所有 V1 功能和现有后端接口不变；使用 Vue 3 <script setup> 和现有 Element Plus 组件；完成后执行 npm run build，并用浏览器验证 /home 与 /allCourses 在 1440px、1024px、768px、375px 下无横向溢出、导航和登录态操作可用、焦点可见。不要提交、推送或创建 PR，除非我之后明确要求；更不能暂存或修改本机 application.yml。
```

## 新会话执行方式

新会话应先发送第一条提示词，等待模型只读确认。确认无误后发送第二条提示词，让模型仅完成 Task 1；每完成一个任务，先验收和汇报，再由用户决定是否继续下一个任务或执行 GitHub 安全发布。

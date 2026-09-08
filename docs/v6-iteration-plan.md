# CC4C 第六次迭代规划：可读性迭代

> 状态：方面一的顶层决策、受控备份验证和 V6 本地分支准备已完成；方面二已完成重组与静态、生产构建、合成兼容验证，方面三至六尚未开始。方面一的独立提交与最终 Git 验收以第六节及本次备份中的交接结果为准。
>
> 执行方式：每个方面在独立新对话中先只读理解、制定详细计划，经用户确认后实施；验收后记录独立提交和交接信息。本文不是一次性执行全部方面的授权。

**目标：** 以个人项目维护和社区开发者阅读为中心，精简仓库组织、落实三端目标目录、保留并增强中文说明，最终通过正常合并收口 GitHub main。

**架构：** 后端采用十个一级技术包；业务前端和观测端采用已批准的轻量平铺 Vue 结构，分别保留 Vuex 和 Pinia。三端继续独立构建，复用本机 MySQL、一个 Redis、RabbitMQ、SMTP 和外部 Prometheus。

**技术栈边界：** 沿用 V5 的 Java 21、Spring Boot、MyBatis-Plus、Vue 3、Vite、Element Plus 和 ECharts，不升级依赖；仅在方面二统一项目开发版本为 `6.0.0-SNAPSHOT`，移除不再适用的 Spring Modulith 元数据和依赖。

## 一、规划编制阶段与固定基线（历史记录）

本节保留 V6 规划编制时的输入、允许变化和预期状态，不表示方面一仍未开始。方面一已批准实施计划、实际检查及后续入口见第六节。目录设计稿保持编制时的原始字节，其中“六方面尚未开始”等文字属于该历史快照；当前实施进度以本文交接记录为准，方面二的目录重组仍未实施。

### 1.1 规划编制时允许的文件变化

- 新增本文：`D:\codex\CC4C_v5\docs\v6-iteration-plan.md`，记录六方面顺序、约束、验收标准、交接状态和两个新对话提示词。
- 更新[三端目录设计稿](architecture/project-directory-design.md)：明确它是 V6 方面二的目标目录基线，保留精简结构，继续标注“尚未实施”。
- 不修改 V5 历史规划、README、源码、配置、依赖或构建文件，不暂存、提交、创建或切换分支，不创建备份或启动应用。

实际路径为 `D:\codex\CC4C_v5` 和 `D:\codex\CC4C_v2`，不是 `D:\codex\CC4C\_v5` 或 `D:\codex\CC4C\_v2`。默认终端可能位于 V2，所有后续命令必须先确认执行目录。

### 1.2 规划编制时已核实的基线

| 项目 | 当前状态 |
| --- | --- |
| 工作区 | `D:\codex\CC4C_v5` |
| 当前分支 | `v5/restructure`，与远端同步，ahead/behind 为 `0/0` |
| HEAD / 实时 origin/v5/restructure | `6c62250d2183f19e2b5a8825b892c0eafae792fe` |
| 实时 origin/main | `5507bc7529ac1a2c691f2e51cdad6dc5514fdaea` |
| HEAD 与 origin/main 的 tree | `d0bc3cb8c9f27c1f9a51e68bc8321566711e43a7` |
| tracked 文件数 | 376 |
| 当前开发版本 | `5.0.0-SNAPSHOT` |
| origin URL | `https://github.com/Jaily16/CC4C.git` |
| 本地提交作者 | `Jaily <3188001246@qq.com>` |
| archive/v4-final | `d243f6a577120d3dd11206815bea802a1c1a6b42` |
| v4.0.0 标签对象 | `b8d86b04712192d141629964a5507a4e22c39059` |
| v4.0.0 剥离提交 | `ed3c7bb62b4402bd1a4e7aa616955f938cf2aaaf` |

`origin/main` 是 V5 PR #36 的双父合并提交，第一父提交为 `d243f6a577120d3dd11206815bea802a1c1a6b42`，第二父提交为当前 V5 HEAD。V6 必须从这个已合并的 main 提交准备，而不是仅从内容相同的 V5 分支头开始。

本轮写入前没有 tracked 修改或暂存，仅目录设计稿未跟踪。该设计稿写入前 SHA-256 为 `078E7428CD727B3F6FF0A4E823D1820F7CF7FF436ABF15CD30F693B812E48748`；此值用于识别本轮输入，不是更新后文件的预期哈希。

本轮文档完成后，预期仅本文和目录设计稿两份文件未跟踪，tracked 文件数仍为 376。方面一开始前必须重新记录两份文档的哈希和 Git 状态；不能把这两份已知文件当作需要清理的脏数据。

已确定 V6 从上述 main 建立本地 `v6/readability`，开发版本采用 `6.0.0-SNAPSHOT`。建分支留在方面一，版本及产物引用调整留在方面二；本轮不执行这两项操作。

## 二、六个方面及依赖顺序

顺序固定为 **一 → 二 → 三 → 四 → 五 → 六**。每方面在新对话中先制定详细计划，确认后实施，验收后按批准计划留下独立提交及交接记录；方面六之前不自动推送。

| 方面 | 实施范围 | 完成门禁 |
| --- | --- | --- |
| 一：盘点与冻结 | 与用户盘点顶层目录及必要子目录；确定保留、合并、归档、清理清单；备份 V5 基线和两份未跟踪规划文件；建立 V6 本地分支 | 用户确认顶层结构；备份可验证；不提前迁移三端源码 |
| 二：三端重组 | 按精简设计迁移 Java、业务前端、观测端；同步扫描、导入、配置加载、质量工具和构建引用；统一版本 | 新结构完整，旧源码结构移除，中文注释保留，静态检查与三端生产构建通过 |
| 三：本机启动与功能验证 | 复用现有配置，以直接命令启动三端；浏览器完成最小核心读写闭环 | 功能、身份隔离和 Prometheus 抓取正常；精确停止本次应用 |
| 四：文档收敛 | 汇总历史资料，docs 扁平化；迁出截图和 OpenAPI；更新 README 和链接 | docs 仅三份 Markdown，无子目录；资料可追溯、链接有效 |
| 五：中文可读性增强 | 保留准确注释，补齐职责、数据流、安全与副作用说明，改善笼统模板化注释 | 注释覆盖与质量检查通过；除注释和格式外无功能变化；轻量只读回归通过 |
| 六：GitHub 收口 | 最终目录、配置、构建与功能门禁；分别确认推送、PR、正常合并 main | Actions 成功、历史保留、归档和既有标签不变 |

### 2.1 方面一的精确边界

先展示顶层目录树和必要的下一层目录，区分受控源码、必要本机目录和没有 tracked 文件的旧目录，再与用户确认保留、合并、归档和删除建议。

方面一负责基线复核、受控备份、V6 本地分支准备、顶层决策与后续清单，不提前移动三端源码、不调整版本或依赖、不清理文档、不启动应用。旧目录没有 tracked 文件不等于物理目录为空，不能据此删除。

已有分支、目录或文件发生碰撞时停止，不覆盖或自动移动既有引用。远端状态偏离固定基线时先报告；不自动变基、合并或换用新的起点。

### 2.2 方面二的关键边界

- 后端固定采用设计稿批准的十个一级包：`controller`、`service`、`mapper`、`repository`、`entity`、`dto`、`config`、`common`、`security`、`support`。Mapper 承担 DAO 职责，已有 JDBC Repository 同层保留。
- 移除旧业务分包、后端 `api/internal/shared` 结构及不再适用的 Spring Modulith 元数据和依赖；不保留两套源码。两端前端用于 HTTP 封装的 `api` 目录继续保留。
- 业务前端保留 Vuex，观测端保留 Pinia，不升级技术栈；已符合目标的文件原地保留，不为迁移而迁移。
- 完整保留既有准确中文注释，随类、函数和组件一同迁移；修正因路径或职责归属变化而失实的说明，不以批量缩写注释降低可读性。
- 处理包可见性、扫描路径、Bean 名称、Mapper 注册、辅助程序及硬编码类路径，保持独立 `com.cc4ctools` 不被主应用组件扫描。
- 业务 Session JSON 当前带旧 Java 类名，迁移必须设计受限的序列化兼容处理；不能只改包名，也不能通过清空 Redis 或放宽反序列化限制解决。
- MyBatis 指标当前依赖旧包名分类，迁移时保持指标含义，不能让所有查询落入兜底分类。
- 质量门禁按迁移后的真实声明清单更新，不机械沿用旧包数、脚本数或通过降低检查要求放行。
- 统一 POM、两端 package/lock 项目版本、版本清单及相关 JAR 引用为 `6.0.0-SNAPSHOT`；除移除不适用的 Modulith 依赖外，不借版本调整升级其他依赖。
- HTTP 路径、DTO 字段、Flyway V1–V7、三个 `*.v1` 消息协议、上传 URL、权限与身份隔离保持不变；项目版本元数据调整不作为接口行为变更。

### 2.3 方面三的启动与验证方式

启动教程以三个独立前台终端为主，各终端先进入对应应用目录并完成受控配置加载：

| 应用 | 执行目录 | 主启动命令 |
| --- | --- | --- |
| 后端 | `D:\codex\CC4C_v5\backend` | `mvn spring-boot:run` |
| 业务前端 | `D:\codex\CC4C_v5\frontend` | `npm run dev -- --host localhost --port 5173 --strictPort` |
| 观测端 | `D:\codex\CC4C_v5\observability` | `npm run dev -- --host localhost --port 5174 --strictPort` |

另给出构建后的 `java -jar` 启动方式。以上是后续教程的目标入口，不是省略配置、依赖和数据库确认的立即执行指令；配置加载的准确命令在方面二确定、方面三验证后写入当前运行说明。

保留必要的安全配置加载辅助，不让用户把密码写进命令历史，不将整栈脚本作为唯一启动方式。后端与 Vite 的配置加载机制不同，不能假定三个应用都会自动读取同样的 `.env.local`。[Spring Boot 配置说明](https://docs.spring.io/spring-boot/reference/features/external-config.html)、[Vite 环境变量说明](https://vite.dev/guide/env-and-mode)。

业务前端继续仅接收公开 API 地址及两个非 `VITE_` 上传根路径变量，不继承后端秘密；保留既有上传映射，不能让图片因为绕开旧启动脚本而失效。观测浏览器端不接收 Prometheus 地址和凭据。

复用三份现有 `.env.local` 和上轮隔离环境，沿用数据库 `cc4cv5a3smoke`、账号、namespace、密钥、SMTP、管理端口及上传路径，不重建或清理数据。用户维护配置，实施者只通过受控加载代码供应用使用，不直接读取、打印或复制配置正文。

复用 `jaily`、管理员及观测账户，不重复注册或引导管理员。最小核心读写闭环覆盖：

- 登录、会话恢复、首页、课程和已审核博客读取，业务与观测身份隔离。
- 经逐项确认的收藏往返、上传、唯一标识博客发布和审核、评论及本人删除。
- 管理端内容及异步消息页查询，不对未知消息执行重试或忽略。
- 观测总览、三个 Dashboard、告警与依赖、刷新和安全退出；确认 Prometheus 抓取正常。

写入和删除前逐项确认精确目标，只撤销本次新增的可逆操作，不改动历史业务数据。用户自行输入凭据并确认邮件；实施者不读取 Cookie、Token、数据库内容或上传文件，不直接清理上传目录、消息队列或 Redis。

前台应用优先通过各自终端 `Ctrl+C` 停止，顺序为观测端、业务前端、后端；若需要进程处置，只能核对本次精确 PID 及身份。确认 4080、4081、5173、5174 释放，不按进程名或端口批量结束进程，不停止 MySQL、Redis、RabbitMQ、SMTP 或 Prometheus。

### 2.4 交接状态

- [x] 方面一：顶层决策、基线备份和 V6 分支准备已完成；独立提交及最终验收按第六节交接。
- [x] 方面二：三端重组及静态／生产构建、合成兼容验证完成；独立提交与最终交接见第七节。
- [ ] 方面三：直接命令启动和浏览器功能验证，未开始。
- [ ] 方面四：文档收敛与资料迁移，未开始。
- [ ] 方面五：中文可读性增强与轻量回归，未开始。
- [ ] 方面六：最终门禁及分段远端收口，未开始。

每方面验收后在交接记录中注明日期、实际 HEAD／父提交／tree、变更清单、通过与未执行的检查、运行停止结果、遗留事项和下一方面入口。未完成或未获授权的步骤不能标记为通过。方面一没有运行构建、应用测试、依赖安装、预检或服务，也没有执行停止操作；其独立提交的实际 SHA、父提交与 tree 由第六节规定的结果文件记录，不把未执行的运行门禁描述为通过。

## 三、最终目录与资料保留规则

方面一已确认分别保留现有 `backend`、`frontend`、`observability`、`infrastructure`、`docs`、`.github` 六个受控顶层目录，不新增受控顶层目录，不将共用基础设施并入后端。必要的本机依赖、构建和运行目录与 Git 受控目录分别保留；无 tracked 文件的旧目录只登记，不据此认定为空或执行删除。

### 3.1 docs 的最终形态

```text
docs/
├── iteration-summary.md       # 历次迭代总结及验收交接
├── performance-history.md     # 历史性能方法、结果、限制与来源
└── project-guide.md           # 项目说明、实现设计、启动和维护
```

- `iteration-summary.md` 汇总有证据支持的历次迭代目标、变化和验收结果，承接 V6 的状态与剩余任务，不编造缺失记录。
- `performance-history.md` 以[现有性能汇总](reports/v4/performance-testing.md)为主要证据，保留原有结果、运行标识、失败修复和限制；被移除资料的引用改为固定提交链接，不补造缺失的逐轮数据。
- `project-guide.md` 汇入已实施结构、实现设计、直接命令启动、数据库维护、消息恢复、观测、安全和质量说明，避免只删文件却丢失维护知识。
- 方面四移除本文和目录设计稿前，先备份，再将已批准设计并入项目指南，将剩余任务、验收状态和后续入口并入迭代总结，保证方面五、六仍有明确交接依据。
- 根 README 保留简洁介绍和直接命令快速启动，不承载迭代过程；本轮不修改它。

### 3.2 截图与接口资料

- 全部十三张截图由 `docs/reference/images/readme/` 移至 `frontend/screenshots/`，逐文件验证 Git blob 一致，不作为应用运行资源引入。
- README 仍展示原有 `01-home.png`、`03-course-detail.png`、`08-blog-write.png`、`10-admin-overview.png` 四张，其他九张保留但不扩大画廊。
- OpenAPI 从 `docs/reference/openapi.json` 移至 `backend/openapi.json`，保留接口契约，更新引用和质量检查路径。
- 不删除源码需要的 Flyway、观测 catalog、Prometheus 规则或其他非文档协议资产；不为满足 docs 扁平化将它们混入三份 Markdown。

### 3.3 备份与空目录安全

所有备份仅写入现有 V2 工作区的以下位置，唯一标识在方面一计划中确定，碰撞时不覆盖：

```text
D:\codex\CC4C_v2\temp\v6-backup\<唯一时间标识>\
```

备份 Git 历史、受控源码文档和本轮两份规划文件，验证后才允许移除原有受控资料。Git 历史使用可验证的受控导出方式，不复制整个旧 `.git`；不读取或复制本机秘密及运行数据，不覆盖、删除或改动 V2 既有文件。

备份验证只针对本轮新建的受控备份做完整性检查，不展开读取 V2 已有历史备份。实际备份和归档发生在后续批准的方面，本轮不创建备份目录或文件。

空目录只对核实后的精确路径逐个删除：必须位于允许范围，非 reparse point，包含隐藏项在内确实为空。受保护目录不进入检查；未知文件、链接或运行数据出现时立即停止，不能通过递归清理实现目录精简。

`target`、`dist`、`node_modules` 和仍使用的运行目录不因“精简”被误删。源码迁移和文档归档完成后分别核查遗留空目录，方面六再次核对最终物理结构与 Git 结构，不保留无用途的空目录。

## 四、检查与授权规则

### 4.1 规划编制阶段的文档验证（历史记录）

```powershell
Set-Location -LiteralPath 'D:\codex\CC4C_v5'
git diff --check
node .\infrastructure\quality\check-doc-links.mjs
git status --short --untracked-files=all
```

额外直接检查两份未跟踪文档的尾随空白、链接、目标树和全文一致性，不能只依赖 `git diff`。本轮验收预期为：

```text
?? docs/architecture/project-directory-design.md
?? docs/v6-iteration-plan.md
```

仅有这两份规划文档变化；tracked 文件仍为 376，无暂存，HEAD、分支和远端引用不变，V6 六方面仍未实施。

### 4.2 未来 V6 的共同门禁

- 不在 `D:\codex` 新建其他工作区；只在现有 V5 内迭代，项目内必要子目录可以按批准清单建立，备份仅进入指定 V2 位置。
- 三端各保留一份 `.env.example` 和被忽略的 `.env.local`；本机配置原地复用，由用户维护，实施者不直接读取、打印或复制其内容。
- 不查询数据库或 Redis 数据，不读取本机 `application.yml`、秘密、Cookie、Token、上传文件、日志或历史备份内容；受控源码和模板与真实配置明确区分。
- 不使用或操作 Docker，不恢复自动化测试、Testcontainers、Gatling 或性能测试资产；保留编译、构建、lint、格式、中文注释检查和浏览器 smoke。
- 使用已有本机工具链，不借可读性迭代升级依赖；依赖安装仅在相应方面明确批准后按现有锁文件执行，不自动安装来掩盖环境问题。
- 方面二通过静态和三端生产构建后才进入方面三。方面五后再次做构建及轻量只读回归，不能仅引用方面三的启动结果。
- 每次改动、暂存和提交前核对当前方面的精确白名单；不使用 `git add -A`，不纳入本机配置、秘密、构建产物、依赖目录、上传文件或运行数据。
- 推送 V6、创建 PR、合并 main 分别等待单独授权；普通快进推送，使用 merge commit 保留迭代历史，不强推、不 squash/rebase、不创建发布标签。
- 远端收口检查精确提交 SHA 和对应 Actions 结果；不沿用 V5 的推送、PR 或合并授权，不移动 `archive/v4-final` 或既有标签。
- 任何失败保留现场，不 reset、clean、stash、覆盖文件或清理外部数据；写操作失败或结果不确定后不自动重试，先只读判断状态并报告。

## 五、两个新对话提示词（规划编制时模板）

两个提示词依次用于“只读理解整个项目”和“进入方面一计划”，不是直接进入方面二。以下正文可分别完整复制。

方面一已完成这两个入口的只读接手和计划确认。下列模板保留为历史记录，不用于在已有 V6 分支上重复备份或建分支；下一方面入口见第六节。

### 5.1 提示词一：只读理解项目

```text
正在接手 CC4C 第六次迭代：可读性迭代。本条消息只允许只读理解，不开始任何方面。

实际工作区为 D:\codex\CC4C_v5，不是 D:\codex\CC4C\_v5。
请先切实核对工作目录，不因默认终端位于 V2 而操作错误项目。

请完整阅读：
1. D:\codex\CC4C_v5\docs\v6-iteration-plan.md
2. D:\codex\CC4C_v5\docs\architecture\project-directory-design.md
3. 根 README、当前模块边界、观测架构、宿主运行和代码质量说明。
4. V5 规划及历史性能汇总，用于理解既有能力与不可丢失的证据。

再只读检查：
- 当前 HEAD、分支、tree、tracked 文件数、暂存区和未跟踪状态。
- 实时 origin/main、origin/v5/restructure、归档和标签。
- 当前三端源码目录、基础设施、工作流、质量门禁和启动入口。
- 顶层真实目录与 Git tracked 目录的区别；不要进入受保护目录。
- 三端 .env.local 的忽略规则和加载代码，不读取配置内容。
- Java 包名与扫描、Session 序列化、指标分类、辅助程序之间的耦合。

V6 已锁定：
- 从已合并的 main 建立 v6/readability，版本采用 6.0.0-SNAPSHOT。
- 六方面依次为顶层盘点、三端重组、本机功能验证、文档精简、中文注释增强、GitHub 收口。
- 后端采用设计稿的十个全局技术包，Mapper 承担 DAO 职责。
- 业务前端保留 Vuex，观测端保留 Pinia。
- docs 最终为三份扁平 Markdown，全部十三张截图保留。
- 复用本机环境，使用少量配置加载辅助加标准 Maven/npm 命令启动。
- 只在现有 V5 工作区迭代，备份只能进入 V2，不新增其他工作区。

请回复：当前项目和 Git 状态、六方面依赖顺序、已确定目标与现状差异、必须保留的资产、主要风险、配置及数据安全边界，以及方面一的准备条件。

不要修改文件、创建备份、切换或创建分支、暂存、提交、推送、安装依赖、运行构建、测试或服务。
不得读取本机秘密配置、数据库、Redis 数据、Cookie、Token、上传文件、日志或历史备份内容。
若状态与规划基线不一致，只报告差异，不自行修复。完成后等待下一条指令。
```

### 5.2 提示词二：进入方面一计划

```text
现在进入计划模式，为 CC4C V6 方面一“顶层目录盘点、V5 基线备份与 V6 准备”制定可直接执行的详细计划，但本条消息仍不执行。

唯一工作区：D:\codex\CC4C_v5。
请先完整读取 docs\v6-iteration-plan.md 和 docs\architecture\project-directory-design.md，并重新验证上次只读理解结论。

请先向我展示最简单的当前顶层目录树和必要的下一层目录，区分：
- 受 Git 跟踪的源码、基础设施和文档；
- 必要的本机依赖、构建及运行目录；
- 没有 tracked 文件、但尚不能认定为空的旧目录。

逐项说明目录职责、是否保留，以及合并、归档或删除建议。会改变顶层组织方式的选择，请先与我确认。

方面一计划必须覆盖：
1. 验证实时 main 和本地 Git 状态，保留当前两份未跟踪规划文档。
2. 在 D:\codex\CC4C_v2\temp\v6-backup 下规划无覆盖的唯一备份位置，备份并验证 Git 历史、受控资料和规划文件；不复制本机配置、秘密或运行数据。
3. 从规划锁定的 main 创建本地 v6/readability，仍在现有 V5 目录内工作，不创建新工作区。
4. 明确已有分支、目录或文件发生碰撞时的停止规则，不覆盖或自动移动已有引用。
5. 确定顶层保留清单、后续迁移清单和最终空目录清理规则。
6. 输出方面一允许修改的精确清单、命令执行目录、验证命令、验收结果以及方面二的交接信息。

本方面不提前移动三端源码，不调整版本或依赖，不清理文档，不启动应用。6.0.0-SNAPSHOT 的版本调整留在方面二，与构建及启动引用一起完成。
未获授权不得创建远端分支、推送、创建 PR、合并 main 或打标签。

配置只检查路径元数据和忽略状态；不得读取 .env.local、本机 application.yml、秘密、数据库、Redis 数据、Cookie、Token、上传文件、日志或历史备份内容。
不能通过递归删除、reset、clean、stash 或覆盖文件来取得干净状态。

本条消息只进行只读检查和方案讨论。最终给出完整实施计划，等待我确认后再实施方面一。
```

## 六、方面一已批准实施计划与交接记录

### 6.1 授权和精确修改白名单

2026-09-08，用户确认六个受控顶层目录分别保留，并确认复用本文保存详细计划、目录决策和验收记录，随后明确授权实施方面一。唯一工作区为 D:\codex\CC4C_v5，备份仅进入现有 D:\codex\CC4C_v2。

方面一不改变公开 API、DTO、Java 类型、数据库结构、源码、依赖、配置加载方式或运行行为，项目版本仍为 5.0.0-SNAPSHOT。只使用已有 PowerShell 7、Git、Node 和 .NET 标准库，不新增其他工作区。

| 位置 | 允许动作 |
| --- | --- |
| docs/v6-iteration-plan.md | 补入已批准计划、目录决策、真实检查和交接；纳入 Git |
| docs/architecture/project-directory-design.md | 保持原始字节，仅纳入 Git |
| V5 Git 元数据 | 正常 Git 命令建立本地 V6 分支、精确暂存和生成一次本地提交；不手工编辑 .git |
| V2 的 temp/v6-backup | 在核实不存在后建立备份容器目录 |
| 本节锁定的备份叶目录 | 创建列明的备份、验证及提交后交接文件 |

没有文件删除、旧目录移动、三端源码迁移、版本或依赖调整授权。原 376 个受控文件保持原 mode 和 blob；V2 的其他文件、Git 引用及既有资料不修改。

### 6.2 顶层盘点和已确认归属

盘点来自 Git 文件清单及安全目录的单层名称和路径元数据。没有进入依赖、构建、运行、秘密、上传或历史备份目录。

| 顶层 | 职责与决定 |
| --- | --- |
| .git | Git 元数据，原地保留，不复制整个目录 |
| .github | build 工作流与 Dependabot，保留；方面一不编辑 |
| backend | 后端生产源码、框架资源和辅助工具，保留；方面二落实十个技术包 |
| frontend | 业务 Vue 应用、Vuex 和静态资源，独立保留 |
| observability | 观测 Vue 应用、Pinia 和 ECharts，独立保留 |
| infrastructure | 数据库维护、共用运行、Prometheus、RabbitMQ、质量工具，独立保留，不并入后端 |
| docs | 文档、截图、接口资料，当前全部保留；方面四收敛 |
| database、deploy、scripts | 无 tracked 文件的旧目录，仅登记，不认定为空、不复制内容 |
| temp | 必要本机运行资料，原地保留、受保护、不进入 |
| 四份根文件 | README、版本清单、忽略与编辑配置原地保留，方面一不编辑 |

必要的下一层目录分类：

~~~text
backend/
├── src/、scripts/                         受控
├── target/                                本机产物，保留
└── docker/                                旧目录，无 tracked 文件
frontend/
├── src/、public/、scripts/、.vscode/       包含受控文件
├── node_modules/、dist/                   本机依赖与产物，保留
└── docker/、tests/                        旧目录，无 tracked 文件
observability/
├── src/、scripts/                         受控
├── node_modules/、dist/                   本机依赖与产物，保留
└── grafana/、prometheus/                   旧目录，无 tracked 文件
infrastructure/
├── database/、host/、prometheus/、
│   quality/、rabbitmq/                    受控
├── observability/                         旧目录，无 tracked 文件
└── secrets/                               受保护，保留、不进入
docs/
└── architecture/、development/、history/、
    operations/、reference/、reports/     资料当前全部保留
.github/
└── workflows/                             受控
~~~

public 包含受控 favicon 不表示允许检查其上传子目录；deploy、infrastructure/secrets 可能包含受保护后代，不能为了移除旧父目录而进入、搬移或清理这些后代。

### 6.3 执行前复核与锁定输入

全部命令从 V5 根目录执行，每个原生命令显式检查退出码，非零立即停止。临时进程环境变量先保存，在 finally 中恢复；不调用任何应用配置加载函数。

~~~powershell
Set-Location -LiteralPath 'D:\codex\CC4C_v5'
$main = '5507bc7529ac1a2c691f2e51cdad6dc5514fdaea'
$v5 = '6c62250d2183f19e2b5a8825b892c0eafae792fe'
$tree = 'd0bc3cb8c9f27c1f9a51e68bc8321566711e43a7'
$backup = 'D:\codex\CC4C_v2\temp\v6-backup\20260908-193230-6c62250d'

git rev-parse --show-toplevel --show-object-format
git remote get-url origin
git -c core.fsmonitor=false status --porcelain=v2 --branch --untracked-files=all
git show -s --format='%H%n%P%n%T' origin/main
git for-each-ref --format='%(refname) %(objectname)' refs/heads refs/remotes/origin refs/tags
git ls-files
git worktree list --porcelain
git -c credential.interactive=never ls-remote origin refs/heads/main refs/heads/v5/restructure refs/heads/archive/v4-final refs/heads/v6/readability refs/tags/v4.0.0 'refs/tags/v4.0.0^{}'
git check-ignore -v -- backend/.env.local frontend/.env.local observability/.env.local
~~~

实际复核通过：第 1.2 节的远端、本地引用及 main 双父关系一致，V5 同步 0/0，tree 相同，376 个 tracked 文件均为 100644，无 tracked 修改或暂存。唯一未跟踪文件为两份规划，V6 分支在本地及远端均不存在。已有 main 对象可用，没有 fetch、pull、merge 或 rebase。

三份 .env.local 均存在且为普通文件，保持 Git 忽略；仅检查元数据和忽略状态，没有读取正文。工作区及备份父路径均为普通目录。V2 的 /temp/ 被忽略，计划创建的备份容器及叶目录原先不存在。

两份原始规划 SHA-256 已在复制前后核对：

~~~text
docs/v6-iteration-plan.md
BAC518FAF6BAF9E58DFB82C05AB8BF085E9FD1F48DA20363A8BD8A08B6C7119B

docs/architecture/project-directory-design.md
2F3348D4524648BF2B62AF5B8634D66D37FB846CD80C1AA828378FC36F662D25
~~~

本文此后按批准范围更新，因此第一项是备份输入哈希，不是本次提交后本文的预期哈希；目录设计稿继续保持第二项哈希。

### 6.4 备份内容、验证方法及已完成结果

唯一备份目录为 D:\codex\CC4C_v2\temp\v6-backup\20260908-193230-6c62250d。目录名在计划阶段固定，真实导出和验证时间分别记录在 baseline.json 与 verification.json 中。

备份及交接文件白名单：

~~~text
20260908-193230-6c62250d/
├── baseline.json
├── tracked-tree.json
├── history.bundle
├── tracked-tree.zip
├── planning-input/docs/
│   ├── v6-iteration-plan.md
│   └── architecture/project-directory-design.md
├── verification/
│   ├── history.pack
│   └── history.idx
├── verification.json
├── SHA256SUMS.json
├── aspect1-result.json
└── aspect1-result.sha256
~~~

最后两项只在提交验收成功后创建，其余为已导出并验证的基线备份。没有复制整个 .git，也没有把本机配置、依赖、构建产物、上传或运行数据放入备份。

已批准并执行的备份顺序：

1. 独占创建不存在的备份目录；检查全部父路径，拒绝链接、junction、reparse point 和同名目标。文件使用禁止覆盖的复制或 CreateNew 写入。
2. baseline.json 记录实际时间、路径、分支、HEAD、main、main 父提交、tree、版本、376 文件数、五个本地引用、实时远端引用、Git 状态、顶层名称元数据和规划输入哈希，不含配置正文。
3. tracked-tree.json 从固定 V5 提交的 git ls-tree -r 生成，逐项保存 mode、type、oid、path。
4. bundle 只包含五个已核实引用及其完整可达历史，不使用 --all，不导出 reflog、不可达对象、Git 私有配置或钩子，也不下载其他远端分支。
5. ZIP 直接从固定 Git 提交导出；受控 application.yml 来自 Git 对象，不读取本机配置作为替代。
6. 两份未跟踪规划以原始字节复制，源文件及副本哈希均与锁定输入一致。

实际导出命令：

~~~powershell
git -c core.quotePath=false ls-tree -r $v5
git bundle create --version=2 "$backup\history.bundle" refs/heads/v5/restructure refs/remotes/origin/v5/restructure refs/remotes/origin/main refs/remotes/origin/archive/v4-final refs/tags/v4.0.0
git archive --format=zip --output="$backup\tracked-tree.zip" $v5
~~~

实际验证命令：

~~~powershell
git bundle verify "$backup\history.bundle"
git bundle list-heads "$backup\history.bundle"
git index-pack --strict --check-self-contained-and-connected --no-rev-index -o "$backup\verification\history.idx" "$backup\verification\history.pack"
git verify-pack "$backup\verification\history.idx"
~~~

history.pack 仅从本次 bundle 的 v2 头部空行后按二进制原样提取，确认起始签名为 PACK；没有通过文本管道转存。严格检查不降级，pack 和 idx 均留在本次 V2 备份中，没有向 V5 对象库导入备份对象。

ZIP 验证通过 .NET ZIP API 在内存中逐项读取，不解压为另一个工作区。每个非目录条目计算 SHA1("blob " + 字节长度 + NUL + 原始字节)，与清单中的 Git blob 对照；拒绝重复、额外或缺失路径。

| 已完成检查 | 实际结果 |
| --- | --- |
| bundle | v2；五个引用与基线一致；无 prerequisite、无历史过滤；验证成功 |
| pack | 严格对象、完整自包含连接检查及 verify-pack 均成功 |
| 受控快照 | 376 个非目录条目逐项 Git blob 一致，无重复或额外文件 |
| 截图 | 十三张均在快照内，blob 一致 |
| Flyway | V1–V7 七份迁移均在快照内，blob 一致 |
| 接口与观测资源 | OpenAPI、观测 catalog 在快照内，blob 一致 |
| 规划原件 | 两份副本与锁定输入 SHA-256 一致 |
| 备份 SHA-256 | 九份基线文件生成清单后重新计算，全部匹配 |

verification.json 保存以上实际检查结果。SHA256SUMS.json 覆盖 baseline、tree 清单、bundle、ZIP、两份规划副本、pack、idx 和验证报告共九个文件，不包含自身和提交后交接文件。该清单本身的 SHA-256 为：

~~~text
DD65AABD382CD199D56A7AFB9779BE8BC8E89CF7E7D9DFE9F2EFF53B50CD16CE
~~~

### 6.5 本地分支、文档提交与最终验收

备份验证全部通过后，再次复核远端引用、原 V5 工作树和两份规划哈希，才执行：

~~~powershell
git switch --no-track -c v6/readability $main
~~~

切换后实际确认：当前为 v6/readability，HEAD 为锁定 main，tree 未变，没有 upstream；两份规划原件完整保留，原 V5 分支仍指向 6c62250d2183f19e2b5a8825b892c0eafae792fe。没有创建本地 main 或移动既有引用。

随后仅更新本文，并保持目录设计稿字节不变。下面是本次文档提交和最终验收的固定命令；命令列出本身不等于执行成功，实际结果由提交后的交接文件记录：

~~~powershell
node .\infrastructure\quality\check-doc-links.mjs
git diff --check
git status --short --untracked-files=all
git add -- docs/v6-iteration-plan.md docs/architecture/project-directory-design.md
git diff --cached --name-status
git diff --cached --check
git diff --name-only
git -c gc.auto=0 -c maintenance.auto=false commit -m "docs: record V6 aspect one baseline and directory decisions"
~~~

暂存前直接核对两份未跟踪 Markdown 的 UTF-8、无 BOM、尾随空白和末尾换行，不能仅依赖 git diff。暂存区必须恰好为两项 A，不使用 git add -A。原 376 个受控文件不变，新增两份规划后 tracked 数为 378。

最终验收命令：

~~~powershell
git branch --show-current
git show -s --format='%H%n%P%n%T%n%s' HEAD
git rev-list --count "$main..HEAD"
git diff --name-status $main HEAD
git diff --exit-code $main HEAD -- . ':(exclude)docs/v6-iteration-plan.md' ':(exclude)docs/architecture/project-directory-design.md'
git -c core.fsmonitor=false status --porcelain=v2 --branch --untracked-files=all
git ls-files
git for-each-ref --format='%(refname) %(objectname) %(upstream)' refs/heads refs/remotes/origin refs/tags
git -c credential.interactive=never ls-remote origin refs/heads/main refs/heads/v5/restructure refs/heads/archive/v4-final refs/heads/v6/readability refs/tags/v4.0.0 'refs/tags/v4.0.0^{}'
git check-ignore -v -- backend/.env.local frontend/.env.local observability/.env.local
~~~

完成条件是：相对锁定 main 只有一个新提交，唯一父提交为该 main；差异仅新增两份规划；工作树与暂存区干净；原 376 个路径的 mode 和 blob 完全一致；目录设计稿哈希不变；项目版本仍为 5.0.0-SNAPSHOT；远端 main、V5、归档及标签不变，远端 V6 分支仍不存在。

提交完成并通过验收后，创建 aspect1-result.json，记录实际日期、分支、提交 SHA、父提交、tree、两份文件 blob、备份位置与验证结果、SHA-256 清单哈希、tracked 数、Git 状态、远端引用、顶层决策、旧目录候选、安全范围、未执行检查和下一方面入口。结果文件另以 aspect1-result.sha256 记录哈希，不改写已验证的基线清单。

本文不嵌入尚未产生的自身提交 SHA，不为此 amend；实际提交身份以结果文件和最终交接为准。没有推送、PR、合并或新标签授权。

### 6.6 碰撞、失败和后续空目录规则

出现以下任一情况即停止并报告实际值与预期值：

- main、V5、归档、标签或本地状态偏离锁定基线。
- 额外 tracked 修改、暂存、非预期未跟踪文件，或已有 V6 分支。
- 备份目录、输出文件碰撞；路径重定向；规划输入哈希变化。
- 复制或完整性不一致，任何备份、文档、Git 检查非零。
- 切分支、暂存或提交失败，或命令结果不确定。

保留现场和本次新备份，不自动重试、不覆盖、不删除失败备份、不换分支起点；禁止 reset、clean、stash、强推及降低验证标准。提交结果不明确时先只读核对 HEAD、父提交和暂存区，避免重复提交。

方面一仅登记以下旧目录候选，不执行空目录检查或删除：

~~~text
database/
deploy/
scripts/
backend/docker/
frontend/docker/
frontend/tests/
observability/grafana/
observability/prometheus/
infrastructure/observability/
~~~

未来处理必须先获得所属方面的精确清单授权，验证绝对路径在 V5 内且不是 reparse point。受保护路径及其后代不进入检查；其他允许路径仅做包含隐藏项的单层检查，发现未知文件、目录、链接或运行数据就停止，不自动扩大范围。

只有确认真正为空的普通目录才能逐个使用非递归删除，例如 [System.IO.Directory]::Delete($absolutePath, $false)；目录非空时失败并保留。含受保护后代的旧父目录保留，不能通过移动秘密或数据获得整齐的物理树。源码迁移后、文档迁移后以及方面六分别复核；依赖、构建产物和必要运行目录不因精简误删。

### 6.7 方面二入口与后续交接

方面一完成后停止，不自动实施方面二。下一对话先核对方面一提交、实际 tree、干净状态及结果文件，再制定方面二详细计划。原有配置仅核对路径和忽略状态，不读取正文，也不读取数据库、Redis、Cookie、Token、上传文件、日志或历史备份内容。

方面二必须同时解决：

- 后端从七个业务／共享一级模块迁往已批准十个全局技术包，Mapper 承担 DAO，已有 JDBC Repository 同层保留；移除不适用 Modulith 元数据和依赖，不保留两套源码。
- 包级可见成员、Mapper 注册、Spring 组件与属性扫描、Bean 名称、META-INF 管理上下文完整类名和工具 POM／脚本入口一起更新；com.cc4ctools 继续独立于主应用扫描。
- 业务 Session 的类名类型标识与旧反序列化白名单采用受限兼容方案，不清 Redis、不扩大允许类型范围；同时核对缓存、消息与观测会话编解码。
- MyBatis 指标保持原模块分类语义，不能因新包名全部进入 shared 兜底。
- 业务前端保留 Vuex，调整共享组件、布局和两个课程表单归属；观测端保留 Pinia，已经符合目标的文件原地保留。
- POM、两端 package/lock、版本清单及 JAR／工作流引用统一为 6.0.0-SNAPSHOT，不升级其他依赖。
- 保留必要的安全配置加载辅助，支持标准 Maven/npm 命令；保留两个非 VITE_ 上传根变量、既有上传 URL 及路径边界，不向前端注入后端秘密或 Prometheus 凭据。
- 按真实声明更新质量门禁，保留中文注释；静态检查与三端生产构建通过后才进入方面三。

后续仍按三、四、五、六顺序推进：复用隔离环境并逐项确认写入／删除；docs 汇入三份扁平 Markdown，迁移全部十三张截图和 OpenAPI，保留历史性能方法、运行标识、失败修复、来源和未留存限制；改善中文说明并做必要回归；最后验证精确 Actions 和远端收口。

原 HTTP 路径、DTO、Flyway V1–V7、三个消息协议、权限与身份隔离保持不变。三份本机配置、既有账户、namespace、密钥和上传路径由用户维护、原地复用；不重建或清理业务数据，不操作 Docker 或外部中间件。未来推送、创建 PR 和正常合并 main 分别等待授权。

本方面没有执行构建、应用测试、依赖安装、应用预检或服务启停，不把这些未执行项标记为通过；也不声称已验证当前端口释放或中间件运行状态。

## 七、方面二实施记录与验收入口

### 7.1 批准范围、基线与检查点

用户已批准实施方面二；迁移、静态、生产构建与离线兼容验证已完成，实际修复与验收见第 7.6 节，不表示本机功能已验证。方面三至六尚未开始。

唯一工作区为 D:\codex\CC4C_v5，沿用本地 v6/readability，无 upstream。方面二父提交锁定为 31799169d56bb75555c5b7d09fdfa197ccd02424，tree 为 571fe16952ec97090d04690be7c95e90637ffc8d；执行前 378 个 tracked 文件、工作树和暂存区干净。实时 main、V5、归档和标签与方面一一致，远端 V6 分支不存在。

方面一检查点不改写。方面二检查点为 D:\codex\CC4C_v2\temp\v6-backup\20260908-aspect2-31799169，记录了基线、378 文件 tree、完整迁移和修改白名单。已从固定 Git 提交导出完整历史 bundle 和 ZIP，完成无 prerequisite 的 bundle、严格自包含 pack、378 项 blob、十三张截图、七份 Flyway、OpenAPI、catalog 和 SHA-256 验证。没有复制本机配置、依赖、运行数据或整个 .git。

迁移清单 migration-manifest.json 的 SHA-256 为 7C607AE14892273836691FDC7173CA63CBC86802E6C3C4E287C2D8C31A65BFE0；基线 SHA256SUMS.json 的 SHA-256 为 45AED71F9CFA0FB1468958F2782CC401B4112CAD924CBC317381C73C21E09160。后续离线校验与结果单独记录，不改写已封存清单。

### 7.2 已落实的结构与兼容方案

- 147 个 Java 类迁往十个技术包；五个 Mapper 承担 DAO，两个 JDBC Repository 同层保留。辅助程序继续位于 com.cc4ctools，主应用仍在 com.cc4c 根包。
- 删除十份 Modulith 包元数据，新增十三份技术包说明；移除 Modulith BOM/API，不升级其余依赖。
- 同步 import、必要跨包可见性、管理上下文 META-INF 完整类名和当前职责说明；保留 Bean 名称、扫描边界、安全链顺序与事务语义。
- SessionJsonRedisSerializer 保留业务会话 Bean 名称和 JSON 写入约定。未知类型处理只精确映射 V5 Principal 与 SessionAuthenticationToken 两个旧类名，校验目标基类型与许可；根对象和嵌套类型均走受限映射器，不替换普通字符串、不清理 Redis。兼容方向为 V6 读取 V5，新写入使用 V6 类名。
- MyBatis 根据完整 Mapper 名分类：User/Administrator 为 identity，Catalog 为 catalog，Blog 为 community，Interaction 为 interaction；未知 Mapper 保留 shared。
- 十一个 Vue 组件调整归属；业务 Vuex、观测 Pinia、API、页面、路由及上传 URL 保留，观测端已符合目标的源码原地不动。
- 项目版本统一为 6.0.0-SNAPSHOT，同步 POM、两端 package/lock、版本清单、三种 JAR、脚本和工作流引用；工作流增加 V6 分支，本方面不推送。
- 原 HTTP/DTO、SQL、Flyway V1–V7、三个 v1 消息、缓存和观测 Session 字段不变。截图、OpenAPI 和协议资源不迁移，文档不收敛，README 留在方面四整理。

### 7.3 前台命令辅助

新增 [with-app-environment.ps1](../infrastructure/host/with-app-environment.ps1)，接受 Application、一个前台原生命令 ScriptBlock、后端必需的 ConfirmDatabase，以及默认 4081 的 ManagementPort。调用目录必须对应所选应用。

辅助仅复用现有环境解析、校验和恢复机制，不创建日志或 PID、不调用预检、不管理进程。上传根解析移至共享辅助供新旧入口复用；业务前端仅获得公开 API 与两个非 VITE_ 上传根变量，观测端清除上传根和已知后端秘密。应用结束时恢复原环境，原生命令失败保留非零退出码。

以下命令用于方面三，方面二未执行：

~~~powershell
# 在 backend 目录，先按现有工具链要求临时选择 Java 21。
& ..\infrastructure\host\with-app-environment.ps1 -Application Backend -ConfirmDatabase cc4cv5a3smoke -Command { mvn spring-boot:run }

# 在 frontend 目录。
& ..\infrastructure\host\with-app-environment.ps1 -Application Frontend -Command { npm run dev -- --host localhost --port 5173 --strictPort }

# 在 observability 目录。
& ..\infrastructure\host\with-app-environment.ps1 -Application Observability -Command { npm run dev -- --host localhost --port 5174 --strictPort }

# 后端生产 JAR 的替代入口，同样留在方面三验证。
& ..\infrastructure\host\with-app-environment.ps1 -Application Backend -ConfirmDatabase cc4cv5a3smoke -Command { java -jar target/cc4c-6.0.0-SNAPSHOT.jar }
~~~

本机终端默认 Java 17；已只读确认 D:\tool\Java\jdk-21 为可用的 Java 21。构建命令只在自身进程中临时设置 JAVA_HOME/PATH，并在 finally 恢复，不安装或修改系统工具链。

### 7.4 正式验收顺序与停止规则

先核对逐文件白名单、旧源码引用、十个一级包及不变资产。AST 实际统计为十三个包说明、228 个具名类型、108 个显式构造器、587 个显式方法，共 936 个文档单元；PowerShell 变为十八份脚本。门禁采用真实清单，不降低覆盖要求。

从 V5 根运行 check-code-quality.ps1；其 Maven 调用通过临时 MAVEN_ARGS=-o 保持离线。随后从 backend 运行 mvn -o -B -ntp clean package -DskipTests，再分别从 frontend 和 observability 运行 npm run build。格式整理限于白名单源码；不安装依赖，不读取私有配置，不执行应用预检或服务启停。

用户另外批准一次纯内存合成数据校验，代码和所需本次构建产物仅留在本次 V2 verification 下，不引入仓库测试框架。覆盖新会话往返、旧根对象及嵌套类型恢复、USER/ADMIN、authorities/details、非法和近似类型拒绝、错误基类型、普通字符串不变、null/空输入/畸形 JSON，以及五个 Mapper 分类与兜底。

任一基线偏差、碰撞、路径重定向、白名单外变化或门禁失败均保留现场并停止；不安装依赖、降低标准、自动重试、reset、clean、stash 或回退覆盖。构建只使用标准目标产物目录，不执行额外递归删除。

通过后仅逐个非递归移除本次迁空的旧源码目录；方面一登记的其他旧目录保留。精确暂存后产生一个本地提交，提交说明为 refactor: reorganize applications for V6 readability，唯一父提交为本节锁定父提交。实际提交 SHA、tree、检查结果和未执行项写入本次检查点结果，不 amend 嵌入自身 SHA。

### 7.5 验收状态与方面三交接

静态门禁、三端生产构建与离线兼容已通过；独立提交及最终 Git 验收由本次检查点结果记录。

方面三继续复用既有配置、cc4cv5a3smoke、账户和 namespace，不重建或清理数据。先验证新前台命令、旧业务 Session 恢复、业务与观测身份隔离、上传映射及 Prometheus 抓取；任何写入/删除仍逐项确认精确目标。停止优先各终端 Ctrl+C，顺序为观测端、业务前端、后端，不操作中间件。本方面不授权进入方面三至六或执行远端收口。

### 7.6 修复、实际验收与交接

用户在首次源码门禁失败后明确授权修复并继续完成方面二，追加允许修改 infrastructure/quality/check-source-quality.mjs；原迁移清单不改写，追加授权记在本次检查点 migration-manifest-extension.json。

实际修复与证据：

- 源码检查器原来只向前读取十六行，误判 OutboxMessage 的完整长 Javadoc。现在按完整注释边界查找，只允许空白和完整注解位于说明与声明之间；六个合成边界用例通过，未缩短原注释或降低 AST 门禁。
- 编译发现 OpenApiConfiguration 同时导入项目 DTO 与 Swagger 的 ApiResponse，已移除误加的 DTO 导入。由该首个编译错误引出的 Lombok 访问器错误随之消失，没有改写实体字段或增加手工访问器。
- 合成 Session 校验暴露 @class 对象与数组类型标识的读取差异。写入映射器保留原协议，读取映射器接受属性标识和既有数组回退，两者共用精确类型许可。修复后 64 项 Session 与 Mapper 断言全部通过。
- 新增 public 的类恢复为“注解在前、public 在声明前”的常规布局，避免迁移造成紧凑但难读的注解排列。
- 产物比对按已核实的 Git 检出行尾处理：七份 Flyway 为 CRLF，catalog 与 application 为 LF；管理上下文文件位于生产 JAR 根 META-INF。源码与受控 Git blob 不变，不为适配验证而改写资源。

验收记录保存在本次检查点 verification 下，失败诊断与成功结果分别保留，不覆盖先前文件。

| 验收项 | 实际结果 |
| --- | --- |
| Java 源码与包 | 167 个源码文件，十个一级技术包，十三份技术包说明 |
| 迁移主体对照 | 除安全配置及指标分类两处明确行为调整外，145 个迁移类在忽略包导入、可见性、注释和空白后与原主体一致 |
| Java 中文 Javadoc | 936/936 |
| PowerShell 质量 | 18/18 |
| 前端静态质量 | 两端 ESLint、Prettier 通过 |
| 文档及观测契约 | 33 份 Markdown 链接有效；8 项总览、3 个 Dashboard、20 个面板、39 条查询、20 条告警 |
| 生产构建 | 后端离线 clean package -DskipTests、业务和观测 npm run build 通过 |
| 后端产物 | 主应用、管理员引导、观测密码工具三种 JAR 的入口与资源核对通过，无旧业务包或 Modulith |
| 合成兼容校验 | 64 项通过，无 Spring 应用上下文、网络或真实会话访问 |
| 配置、数据及远端 | 未直接读取本机配置或运行数据，未启停应用或中间件，未安装依赖，未操作远端 |
| 构建提示 | 两端存在超过 500 kB 的分包提示，保留给后续有授权的优化，不扩大本方面范围 |

运行行为仍未验证：方面三需验证三个前台入口、真实旧会话恢复、身份隔离、上传映射及 Prometheus 抓取。方面三至六继续未开始。

方面二本地提交与最终工作树、引用、资产及空目录验收，以本次检查点 aspect2-result.json 和独立 SHA-256 为准；本文不嵌入自身提交 SHA，不为此 amend。

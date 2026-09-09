# CC4C 迭代总结

本文按 V1–V6 区分目标、实际变更、已记录验证和限制。历史规划中的待办不是完成证明；过去的构建、测试、容器、性能与发布结果也不是当前版本重跑结果。当前实现与维护入口见[项目指南](project-guide.md)，实验方法和完整结果表见[性能历史](performance-history.md)。

## V1：功能修复与业务基线

目标是贯通课程、博客、收藏、评论、个人资料和管理流程。功能修复基线为 `bf810a63985a92160210e004d4ebd6094791cbdf`，核心实现记录为 `060c1bd5a1d17ed94f944a586f8be10a957e5cda`。

- 后端统一响应与异常边界，调整 Service 事务和数据访问，查询空结果返回空集合；密码不再出现在用户响应中。
- 修复 Vue computed/ref 使用、组合式代码中的 this、登录状态初始化竞争及图片资源引用；保留两级评论与父回复归属。
- 当时记录后端 17/17 测试通过、业务页面浏览器验收通过。真实 SMTP 收件和本机图片选择器仍未验证，不能写成全链路完成。
- 当时使用的 user_email／管理员 Cookie 判定后来由 V3 服务端身份体系替换。PR #4 在该记录时仍为 draft；本文不以这个快照推断发布状态。

原始逐项功能表和修复过程见来源中的“前端功能报告”和“项目迭代记录”。

## V2：界面与交互

目标是统一视觉变量、响应式布局和页面反馈，不改变业务接口。任务一至七覆盖基础样式、公共壳层、认证页、发现页、课程阅读、博客创作与个人资料、管理台，并有用户确认记录。任务八完成源码级可用性、焦点和响应式审查。

当时证据记录 Vite 3.2.4 构建处理 1,480 个模块、后端 17/17 通过；约 874 KiB 的包体及 TableId 警告属于当时构建，不能作为当前包体或告警。TableId 后续在 V3 处理。1440／1024／768／375 四档完整人工矩阵尚待执行，不能以源码审查代替；任务九的发布计划也不等于已发布。

## V3：安全、可靠性与可观测性

V3 从 `54262dad4053adeb4019be7dd95eb644995bc3da` 推进七个方面。下表提交是当时实现证据，测试数量只说明对应历史阶段。

| 方面 | 当时实际变化 | 实现提交 | 历史验证 |
| --- | --- | --- | --- |
| 一 | Java 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.17 与工具链升级 | `b1b9c1b` | 后端 23 项 |
| 二 | Modulith 1.4.12 业务模块、DTO、Flyway 与关系约束 | `57d769b` | 后端 40 项 |
| 三 | Spring Security、USER／ADMIN、Session、CSRF、BCrypt、验证码与限流 | `ca628e1` | 后端 63 项 |
| 四 | 公开热点 Cache-Aside、generation 失效、并发合并和故障旁路 | `bc7dcf8` | 后端 80 项及受控缓存实验 |
| 五 | Transactional Outbox、Inbox、AES-GCM、Publisher Confirm、重试与 DLQ | `5daf68c` | 后端 125 项 |
| 六 | Micrometer、关联日志、Prometheus、三个 Grafana Dashboard、Gatling | `f0f6fa1` | 后端 150 项、故障演练和性能收口 |
| 七 | 九个服务、八个命名卷的容器交付与供应链门禁 | `a22a329` | 后端 154 项、前端 4 项安全契约检查 |

V3 曾有意升级 HTTP 方法、响应、Cookie 和身份语义；V6 仅保持升级后契约，不把各版本都描述为无接口变化。V3 的 Modulith、自动化测试、Gatling 和 Docker 实现已在后续 V5/V6 移除，其历史证据保留，当前不提供对应入口。

### 失败、修复与限制

- 迁移遇到 MySQL collation／结构不一致时保留原库，另行批准新的恢复库；没有通过 clean、repair 或删除原数据继续。当时密码转换记录为 2 个用户和 5 个管理员，不能据此推断当前账户数量。
- 密码重置后的 CSRF Promise 缓存曾导致再次请求异常，修复后统一失效；消息检查曾假设全表为空，但已有 4 条合法 Outbox，改为核对本轮增量而未删除既有事件。
- Redis 异常被包装后落入通用 500，修复为有深度与循环保护的原因链分类并返回脱敏 503。MySQL 连接等待由默认 30 秒改为 3,000 ms，验证等待 1,000 ms；代理故障请求约 3,149 ms 后有界失败。
- 因安全与缓存使用同一原生 Redis，独立“缓存 Redis 停止”演练未执行。RabbitMQ 与 SMTP 故障后的受理、积压和恢复另有记录；SMTP 至少一次投递仍可能重复，不声明 exactly-once。
- 原始 Gatling、JSON、Prometheus 和 EXPLAIN 未提交的部分继续标为未留存。完整缓存、负载、观测开销和容器性能表留在性能历史，不跨环境计算提升。
- V3 首次远端检查出现 JVM／MySQL 时区和 Trivy 安装器失败，分别以 Instant／Timestamp 处理和固定 Action 修复。最终 main `8f2987267a942655c1059243aaa60cf4bd29748b` 对应 quality [33251873844](https://github.com/Jaily16/CC4C/actions/runs/33251873844) 成功；当时没有 V3 SemVer 标签或 GHCR 发布。

前端 High/Critical 为 0、浏览器通过及供应链结果均为历史检查，不能替代当前审计。详细命令、容器资源身份和未执行项可按来源检索，不在当前教程恢复这些入口。

## V4：工程整理与历史发布

V4 六方面依次整理旧资产、版本与目录、质量规则、开发／静态双运行模式和发布验收。形成 backend、frontend、versions.yml 与共用基础设施布局；当时保留 Docker、Nginx 和性能工具。

- 最终历史记录为后端 160 项、前端 4 项安全与 11 项 API 检查通过；当时 High/Critical 为 0。
- Vite 开发模式与 Nginx 1.28.3 静态模式均有后端、路由及停止验收；修复了 Nginx daemon off 参数和启动引用问题。
- 迁移时只复制实际存在的 4 个旧 cc4c-v3 卷，另外 4 个缺失卷没有为“凑齐迁移”创建。原卷保留；这些本机内容没有进入本轮读取或备份范围。
- 消息 retry／ignore 没有合适候选，记录为未执行，而不是通过。

### Actions 失败与发布闭环

| 历史运行 | 结果及解释 |
| --- | --- |
| quality [33355519788](https://github.com/Jaily16/CC4C/actions/runs/33355519788) | 成功 |
| quality [33355952829](https://github.com/Jaily16/CC4C/actions/runs/33355952829) | 缓存并发相关检查失败；修复后 [33356398667](https://github.com/Jaily16/CC4C/actions/runs/33356398667) 成功 |
| release [33357434615](https://github.com/Jaily16/CC4C/actions/runs/33357434615) | lockWaits 调度断言失败，性能和发布未执行 |
| release [33358398336](https://github.com/Jaily16/CC4C/actions/runs/33358398336) | 质量与 Compose smoke 通过；Linux bind mount 被 root 写入，summary.json 落盘权限失败，不是 HTTP 延迟门禁失败 |
| quality [33359828199](https://github.com/Jaily16/CC4C/actions/runs/33359828199) | 修复后的质量门禁成功 |
| release [33360497982](https://github.com/Jaily16/CC4C/actions/runs/33360497982) | 完成质量、Compose smoke、三轮性能及发布；性能 job 为 99391819204 |

修复保留单 loader 与确定性 miss 契约，去掉不可靠的调度断言；Linux 修复仅恢复该轮性能输出的 UID/GID。最终提交 `ed3c7bb62b4402bd1a4e7aa616955f938cf2aaaf` 是 `v4.0.0` 的剥离提交，标签对象为 `b8d86b04712192d141629964a5507a4e22c39059`。历史 GHCR backend／frontend 4.0.0 发布与 provenance 有记录。归档 `d243f6a577120d3dd11206815bea802a1c1a6b42` 包含标签后的文档，不能把归档提交当作标签提交。

这些 Actions 地址来自已跟踪报告，本方面没有重新访问运行结果、拉取镜像或验证远端制品。

## V5：宿主运行与独立观测端

V5 规划的“未开始”是编制时快照。实际 Git 历史表明以下变更已落地；提交证明文件变化，不自动证明规划中的每一项运行门禁都执行过。

| 提交 | 实际范围 |
| --- | --- |
| `8b1bf95e32028301bad26f0c0687ec9f57b06ace` | 冻结 V5 规划和范围 |
| `a64c5317c5ae135f0f6a605a069e42715398e175` | 汇总 V3–V4 性能证据 |
| `e01bba29c66c4639855167f31147124fc643b83e` | 移除 Docker、自动化测试及性能实现 |
| `e94b744bd18da8b46eb5fa949019647d9130deb6` | 简化三端本机配置与宿主入口 |
| `7db05b462c8d2aa49cd73c8a5ea0c2ff70636015` | 独立中文观测应用 |
| `5fd9888b26ff101a96ba14f5bb0311079471f92e` | 文档与中文功能注释 |
| `6c62250d2183f19e2b5a8825b892c0eafae792fe` | README、build 工作流与 V5 收口 |

V5 保留单个原生 Redis 的独立 namespace、MySQL／RabbitMQ／SMTP 与外部 Prometheus；观测端用固定 catalog 和 ECharts 承接原三个 Grafana Dashboard。Docker、Testcontainers、Gatling 和旧发布入口已移除。5.0.0-SNAPSHOT 的受控资料共 376 个文件，十三张截图是该阶段资料，不表示 V6 重新截图。

合并 main 为 `5507bc7529ac1a2c691f2e51cdad6dc5514fdaea`（PR #36），两个父提交分别为 V4 归档和 V5 HEAD，与 V5 共享 tree `d0bc3cb8c9f27c1f9a51e68bc8321566711e43a7`。V5 未逐项留存的功能、故障及 Actions 结果继续按未留存处理；V6 方面二构建和方面三本机验收是后来的独立证据。

## V6：可读性迭代与交接

唯一工作区为 `D:\codex\CC4C_v5`，备份只进入 `D:\codex\CC4C_v2\temp\v6-backup`，不新建工作区。分支 `v6/readability` 从上述已合并 main 创建，无 upstream；项目版本 `6.0.0-SNAPSHOT` 已由方面二完成。

### 六方面顺序与状态

| 方面 | 依赖与结果 | 当前状态 |
| --- | --- | --- |
| 一：顶层盘点、V5 备份与准备 | 冻结资料、六目录职责，从固定 main 建本地分支 | 已完成 |
| 二：三端重组与兼容 | 以方面一为基线迁移、更新版本／入口、生产构建与合成校验 | 已完成 |
| 三：本机启动与功能 | 验证方面二真实运行、身份隔离与 Maven → JAR 会话恢复 | 已完成，有限制 |
| 四：文档收敛与资料迁移 | 依赖前述事实，保留证据后合并为三份文档 | 已完成 |
| 五：中文注释增强 | 全量审阅、按需改写，并核对非注释结构与静态门禁 | 已完成 |
| 六：GitHub 收口 | 依赖前五方面，再复核目录、质量、运行和精确 Actions | 未开始 |

后端保留十个全局技术包和 support 的 cache／messaging／monitoring 三个子包；Mapper 承担 DAO 职责，不增加机械包装层。业务前端继续 Vuex，观测端继续 Pinia。不得以可读性整理改变 HTTP／DTO／SQL／事件／权限语义，不以清空 Session、日志或运行目录消除兼容问题。

### 方面一：目录决策与基线

提交 `31799169d56bb75555c5b7d09fdfa197ccd02424` 的父提交为锁定 main。V5 的 376 个受控文件和两份未跟踪规划分别保存并验证后，建立本地 V6 分支；只纳入两份规划，成为 378 个文件。

保留六个受控顶层目录：backend、frontend、observability、infrastructure、.github、docs；根 README、versions.yml、.gitignore、.editorconfig 保留。共用基础设施不并入后端；本机 node_modules、target、dist、temp、秘密和 Git 元数据原地保留，不纳入受控备份。

下列是“没有 tracked 文件但不能认定为空”的候选，方面一没有删除，本方面也没有处理：
`database/`、`deploy/`、`scripts/`、`backend/docker/`、`frontend/docker/`、`frontend/tests/`、`observability/grafana/`、`observability/prometheus/`、`infrastructure/observability/`。

未来清理需批准精确路径，排除受保护后代，确认位于 V5 且不是链接／reparse point，单层包含隐藏项检查。只对确实为空的普通目录逐个非递归删除；未知文件、运行数据或非空目录立即停止，不为整齐而移动秘密。

方面一备份位置：`D:\codex\CC4C_v2\temp\v6-backup\20260908-193230-6c62250d`。该路径是历史记录，本方面未读取其内容。

### 方面二：源码、兼容与生产构建

提交 `071a320cdbe55d7e09fcc503a94eda4d29ca6c02` 的父提交为方面一提交。147 个生产类迁移到技术层，Java 受控文件共 167 个（含 13 份包说明），移除 Modulith API／BOM 与旧业务包注解。业务前端 11 个组件移动并更新引用，Vuex、Pinia、懒加载路由和上传 URL 保持。

新增命令包裹辅助，退出恢复环境；业务前端只取得公开 API 和两个非 VITE_ 上传根变量，观测端清除上传根及已知后端秘密。全部版本与三种 JAR 引用更新为 6.0.0-SNAPSHOT，除移除 Modulith 外依赖树不变。

| 方面二验证 | 结果与边界 |
| --- | --- |
| 静态质量 | 936 个 Java 文档单元、18 个 PowerShell 脚本及原有门禁通过 |
| 后端离线生产打包、两个前端生产构建 | 通过；未安装依赖或启动应用 |
| 纯内存 Session／Mapper 合成校验 | 64 项通过；无 Spring 上下文、Redis、数据库或真实 Session |
| 兼容资产 | Flyway、catalog、模板、截图、OpenAPI 等原 blob 保持 |
| 真实功能／旧 Session | 不属于方面二的构建证明，留给方面三 |

实施中修正质量检查器对短 Javadoc 的误判，并用 6 个合成案例核对；处理 ApiResponse 导入冲突、根与嵌套 JSON 的受限读取及跨包可见性／注解布局。Flyway 工作区换行和 Git 原始字节按各自层次比较，未改写 SQL。

兼容方向为 V6 读取两个精确 V5 Session 类型别名，未知类型或错误基类型仍拒绝；普通字符串不替换。不保证 V5 读取 V6 新会话，不实施回滚清理。五个 Mapper 精确映射旧 module 指标标签，未知名称才使用 shared。详情见项目指南。

检查点：`D:\codex\CC4C_v2\temp\v6-backup\20260908-aspect2-31799169`；本方面未读取该旧检查点。

### 方面三：真实本机验收

提交 `b8d0b3462020fc75e3fca3bfa65597d4a555eb9c` 的唯一父提交为方面二提交；其 tree 为 `d09988e734a23a1afeac21ea829096cdc9ac573b`，383 个受控文件。2026-09-09 用户在三个前台终端操作启停，浏览器使用 Codex 内置浏览器，凭据和验证码由用户输入；未读取本机配置正文、日志或真实 Session。

| 检查 | 已记录结果 |
| --- | --- |
| PowerShell／Java／启动 | PowerShell 7.6.5、Java 21；离线 Maven 包裹入口及两端 npm dev 通过。最初 PowerShell 5.1 不满足 requires，切换正确终端后继续 |
| 外部故障 | Redis 未监听曾导致启动失败／readiness DOWN，由用户恢复；Prometheus 未运行导致观测不可用，用户另行授权启动。未更改代码或读取私有配置 |
| 公共与身份读取 | 首页、课程、博客、管理员内容与消息查询通过；USER、ADMIN 切换和独立观测登录／退出通过。属于页面验收，不是完整 HTTP 攻击测试 |
| 最小收藏 | 课程 ID 1“黑马_20天学会JAVA”新增收藏及单独确认取消通过 |
| 最小博客闭环 | 唯一合成博客发布、管理员审核、作者确认、评论新增／删除和博客删除通过；用户确认收到审核邮件 |
| 上传 | 用户批准公开 Logo，页面展示成功；原头像映射通过，未替换历史头像 |
| 观测 | 总览、三个 Dashboard、告警和依赖页面可用；六个依赖、20/20 规则与后端抓取 up=1 有记录 |
| JAR 重启恢复 | 用户停止 Maven 后端，端口释放后启动已有 JAR；公开 health／liveness／readiness 为 200／UP，业务及观测 V6 会话刷新恢复通过 |
| 真实 V5 Session | 未验证：没有有效 V5 会话。不能用 64 项合成检查或 V6 重启恢复替代 |
| 停止 | 用户按观测、业务、后端顺序 Ctrl+C；本次进程退出，4080／4081／5173／5174 释放，外部中间件未纳入停止范围 |

合成博客标题为 `CC4C-V6-A3-20260909-144912`，ID 为 `2097578257441521666`。各收藏、上传、发布、审核、评论、删除均逐项确认；没有消耗历史草稿或重试未知消息。上传图片的物理文件未删除，业务删除不代表审计、消息或图片残留清除；已存在博客的读取计数也可能增加（记录中 3 → 4），未回滚正常读取副作用。

部分观测面板无数据或截断，不能断言 39 条查询都获得完整动态数据。前端进程可执行文件路径在系统接口返回空值，按 PID、创建时间、端口和用户 Ctrl+C 后退出共同核实，并保留该限制。环境变量恢复未通过读取秘密值来动态证明。健康响应按 UTF-8 解析字节，直连检查使用 NoProxy 规避本机代理 502，不修改系统代理。

检查点：`D:\codex\CC4C_v2\temp\v6-backup\20260908-aspect3-071a320c`；本方面未读取该旧检查点。

### 方面四：资料布局、备份与验收

方面四已由提交 `08e1f1070fe8113e754ec3fb460f16b3ed8f069e` 完成，唯一父提交为上述方面三提交，tree 为 `7561a2b1d011f794f14b77d12f60bb16fd64fe89`。只改文档、公开资产路径和 OpenAPI 扫描排除路径。独立检查点为：
`D:\codex\CC4C_v2\temp\v6-backup\20260909-aspect4-b8d0b346`。

备份从固定 Git 对象导出 383 个文件及 V6 分支完整历史，未复制本机配置、.git、依赖、产物或运行数据。bundle 无 prerequisite／历史过滤，PACK 严格自包含连接检查通过；ZIP 逐项以 Git blob SHA-1 验证 383 个路径，SHA-256 清单复算通过。两份 V6 规划、全部历史文档、十三张截图及 OpenAPI 完整保存在该检查点和父提交中。

三份新文档先完成内容对照，再逐项删除原 30 份 Markdown；十三张截图移往 frontend/screenshots，OpenAPI 原字节移往 backend/openapi.json。根 README 重写当前入口，两份简短 README 保留职责和导航。性能历史完整保留九节、结果表、方法、阈值、环境、运行 ID、失败修复和未留存说明。具体章节去向在检查点 content-coverage.json 中逐份登记。

本提交的收口条件是以下各项全部通过，实际命令结果与提交 SHA／tree 保存在检查点 acceptance.json 和 aspect4-result.json；不为写入自身 SHA 再 amend：

- docs 物理目录恰好三份 Markdown、无子目录；全仓 Markdown 为六份。
- 十三张截图与 OpenAPI 的 mode/blob 保持，另外 335 个原路径不变；最终 356 个 tracked 文件。
- 质量脚本语法、六份文档链接、源码质量、观测契约和 git diff --check 通过。
- UTF-8、无 BOM、LF、末尾换行、无尾随空白；源码扫描仍排除 OpenAPI 和全部原有受保护范围。
- 配置仍被忽略、非 V6 本地引用与远端引用未变，工作树和暂存区干净。

本方面没有运行构建、应用测试、浏览器功能、服务启停或数据库维护，上述运行项目记录为未执行。方面三结果只作为历史来源，不冒充本轮通过。

### 方面五：中文注释审阅与结构对照

本方面从方面四提交原地实施，版本仍为 6.0.0-SNAPSHOT。独立检查点为
`D:\codex\CC4C_v2\temp\v6-backup\20260909-aspect5-08e1f107`；旧检查点没有重新读取。固定 HEAD 的 356 项 tree、受控 ZIP、V6 完整历史 bundle、严格 PACK 自包含验证和 SHA-256 清单在源码编辑前已保存并校验。

全量审阅 259 个受控文件，按实现决定保留或改写，不以模板检索数量代替质量结论：

| 范围 | 审阅与验证边界 |
| --- | --- |
| 后端 Java 167 份 | 936 个文档单元：13 个包、228 个类型、108 个构造器、587 个方法；核对参数、返回、异常、权限、事务与外部副作用 |
| 业务前端 51 份 | 核对组件职责、Vuex、API、composable、路由及事件；明确草稿删除、阅读计数、上传和审核行为 |
| 观测前端 23 份 | 核对独立身份、Pinia、固定查询、轮询替换与取消、图表和监听器释放 |
| PowerShell 18 份 | 五项头部与 38 个具名函数，核对环境恢复、精确进程身份、工具调用与失败保留；运行维护入口仅静态审阅，质量脚本按批准命令执行 |

两端现有功能边界共登记 326 项，另逐项补充 Vuex mutation/action 和 Pinia action 的状态责任。准确的 Session 受限序列化、包说明和脚本安全边界保留；说明性注释之外的类型、签名、字面量、SQL、事件、配置与行为保持不变。PowerShell 中只纠正密码迁移实际通过 Maven 调用、整栈三端数量、质量入口统一失败码等说明，不修改命令。

辅助程序只保存在本次检查点。Java 使用 JDK 21 AST 与编译器非注释 token；两端使用已有解析器比较 AST/token，Vue 非脚本块按字节对照；PowerShell 使用 Parser API 比较 token、换行边界和指令。Java 9 项、两端 11 项、PowerShell 11 项纯内存合成案例分别验证允许的注释变化及应拒绝的正文变化。工作区仅允许 CRLF→LF 的比较归一化；截图、OpenAPI 等资产按原始字节或 Git blob 核对。

曾因 Java 对照辅助的 AST 文本输出包含 Javadoc 而误报，经用户批准修正辅助程序后重新验证；未修改项目检查器。注释格式写入又曾遇到 Windows 文件映射占用，按停止规则保留现场。用户批准继续后确认全部中断文件哈希未变，目标可打开写入句柄；未确认此前占用者。后续由解析器先输出精确 Javadoc 补丁并退出，再核对原文写入，未结束进程、替换文件或强行解除占用。结构复核继续通过。

结构对照与现有静态质量入口均已通过。259 个审阅文件中，实际修改 219 个文件中的说明性注释：Java 153、业务前端 46、观测前端 13、PowerShell 7；另外 40 份保留。加上本总结和项目指南，本次提交精确变更 221 个文件。最终命令结果、提交 SHA／父提交／tree 及审阅明细保存在本次检查点，提交不嵌入自身 SHA，也不 amend：

- 使用 PowerShell 7、Java 21 与临时 `MAVEN_ARGS=-o` 执行现有质量入口；结束后恢复环境与目录。
- Java 936 单元、PowerShell 18 脚本基数和质量规则不变；源码、格式、lint、六份文档链接及观测契约已通过。
- 只有批准的注释文件和本总结、项目指南变化；95 个白名单外原路径不变，总 tracked 数仍为 356，docs 仍为三份 Markdown。
- 版本、依赖、模板、Flyway、catalog、Prometheus 规则、十三张截图及 OpenAPI 保持；配置仍被忽略，非 V6 本地引用和实时远端不变。
- 生产构建、应用测试、浏览器验证、服务启停、数据库维护均未执行，不以方面二或三的结果冒充本轮通过。

源码审阅另外记录 `CourseView.vue` 模板引用 `goToLogin`，但当前脚本没有对应声明；本轮不修复或运行页面验证。方面六应先评估该静态发现及修复范围，不能将其写为已验证的运行故障或已解决事项。

### 方面六入口与停止规则

方面六从本总结和项目指南继续，复核物理目录、生产构建／运行边界及精确 Actions；真实 V5 会话未验证、部分观测数据缺失或截断等方面三限制继续保留。远端分支、推送、PR、合并 main、标签或发布需要相应授权。本方面没有进行这些操作，方面六仍未开始。

任何基线偏差、目标碰撞、链接路径／reparse point、未知文件、白名单外差异或检查失败，都保留现场并停止报告；不用覆盖、reset、clean、stash、递归删除或降低门禁取得干净状态。秘密配置、数据库、Redis、Cookie／Token、上传、日志与历史备份不属于文档整理输入。

## 固定来源与检索

以下 30 份旧 Markdown 均已按章节登记到本次检查点 content-coverage.json。相同内容优先链接已发布 V5 的完整提交；V6 修改或新增资料只使用本地固定提交检索，不生成尚未推送的 GitHub 链接。所有旧文档也完整包含在本方面 Git bundle 与 ZIP 中。

| 原文档路径 | 固定来源 | 当前主要去向 |
| --- | --- | --- |
| `docs/architecture/module-boundaries.md` | 本地 `b8d0b3462020fc75e3fca3bfa65597d4a555eb9c` | [内容去向](project-guide.md#项目与目录) |
| `docs/architecture/observability.md` | 本地 `b8d0b3462020fc75e3fca3bfa65597d4a555eb9c` | [内容去向](project-guide.md#观测架构) |
| `docs/architecture/project-directory-design.md` | 本地 `b8d0b3462020fc75e3fca3bfa65597d4a555eb9c` | [内容去向](project-guide.md#项目与目录) |
| `docs/development/code-quality.md` | 本地 `b8d0b3462020fc75e3fca3bfa65597d4a555eb9c` | [内容去向](project-guide.md#代码质量) |
| `docs/development/v4-iteration-plan.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/development/v4-iteration-plan.md) | [内容去向](iteration-summary.md#v4工程整理与历史发布) |
| `docs/development/v4-validation-report.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/development/v4-validation-report.md) | [内容去向](iteration-summary.md#v4工程整理与历史发布) |
| `docs/development/v5-iteration-plan.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/development/v5-iteration-plan.md) | [内容去向](iteration-summary.md#v5宿主运行与独立观测端) |
| `docs/history/frontend-functional-test-report.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/frontend-functional-test-report.md) | [内容去向](iteration-summary.md#v1功能修复与业务基线) |
| `docs/history/plans/2026-08-29-v3-iteration-closure.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/plans/2026-08-29-v3-iteration-closure.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/project-iteration-record.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/project-iteration-record.md) | [内容去向](iteration-summary.md) |
| `docs/history/reports/v3/aspect6/README.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect6/README.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/reports/v3/aspect6/fault-drills.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect6/fault-drills.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/reports/v3/aspect6/observability.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect6/observability.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/reports/v3/aspect6/performance.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect6/performance.md) | [内容去向](performance-history.md) |
| `docs/history/reports/v3/aspect7/README.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect7/README.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/reports/v3/aspect7/browser-acceptance.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect7/browser-acceptance.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/reports/v3/aspect7/performance.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect7/performance.md) | [内容去向](performance-history.md) |
| `docs/history/reports/v3/aspect7/supply-chain.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect7/supply-chain.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/reports/v3/aspect7/validation.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/reports/v3/aspect7/validation.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/v2/iteration-plan.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/v2/iteration-plan.md) | [内容去向](iteration-summary.md#v2界面与交互) |
| `docs/history/v3/iteration-plan.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/v3/iteration-plan.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/v3/technology-upgrade-summary.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/v3/technology-upgrade-summary.md) | [内容去向](iteration-summary.md#v3安全可靠性与可观测性) |
| `docs/history/v4/architecture/0001-containerized-delivery.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/v4/architecture/0001-containerized-delivery.md) | [内容去向](iteration-summary.md#v4工程整理与历史发布) |
| `docs/history/v4/architecture/container-delivery.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/v4/architecture/container-delivery.md) | [内容去向](iteration-summary.md#v4工程整理与历史发布) |
| `docs/history/v4/operations/compose-identity-migration.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/v4/operations/compose-identity-migration.md) | [内容去向](iteration-summary.md#v4工程整理与历史发布) |
| `docs/history/v4/operations/container-runbook.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/history/v4/operations/container-runbook.md) | [内容去向](iteration-summary.md#v4工程整理与历史发布) |
| `docs/operations/host-runbook.md` | 本地 `b8d0b3462020fc75e3fca3bfa65597d4a555eb9c` | [内容去向](project-guide.md#配置与本机运行) |
| `docs/operations/messaging-failure-runbook.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/operations/messaging-failure-runbook.md) | [内容去向](project-guide.md#异步消息维护) |
| `docs/reports/v4/performance-testing.md` | [V5 固定原文](https://github.com/Jaily16/CC4C/blob/6c62250d2183f19e2b5a8825b892c0eafae792fe/docs/reports/v4/performance-testing.md) | [内容去向](performance-history.md) |
| `docs/v6-iteration-plan.md` | 本地 `b8d0b3462020fc75e3fca3bfa65597d4a555eb9c` | [内容去向](iteration-summary.md#v6可读性迭代与交接) |

V6 本地固定资料的检索方式（从 V5 根目录执行，路径取上表）：

~~~powershell
git show b8d0b3462020fc75e3fca3bfa65597d4a555eb9c:docs/v6-iteration-plan.md
git show b8d0b3462020fc75e3fca3bfa65597d4a555eb9c:docs/architecture/project-directory-design.md
~~~

历史容器、测试、回滚及发布命令只保留为对应版本的固定来源；当前操作以项目指南为准。没有留存的逐轮数据和验收结果仍标为缺失，不从本机历史目录恢复或补造。

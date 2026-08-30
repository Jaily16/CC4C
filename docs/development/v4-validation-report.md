# CC4C V4 验证与发布准备报告

> 本文件只记录方面一至六的实际验证结果。历史 V1–V3 规划、报告、备份和临时证据不在本文件中重写。

## 当前结论

| 项目 | 实际状态 |
| --- | --- |
| 开发版本 | `4.0.0-SNAPSHOT` |
| 规划基线 | `8f2987267a942655c1059243aaa60cf4bd29748b` |
| 默认 Compose 项目 | `cc4c` |
| 兼容/回滚 Compose 项目 | `cc4c-v3` |
| Compose 卷迁移 | 四个已有 `cc4c-v3_*` 源卷已复制并在启动前验证；源卷仍保留 |
| 源卷删除 | 未执行 `DeleteSource`，也未执行任何 `down -v` |
| 宿主机模式 | 隔离依赖上的 Dev 模式预检、启动、健康检查和精确 PID 停止通过 |
| 静态 Nginx 模式 | 未执行：当前没有用户提供的、已验证的 Nginx 1.28.3 可执行文件 |
| 浏览器业务写入 | 待用户在动作前确认临时测试凭据输入；尚未写入凭据或业务数据 |
| SemVer 标签 | 未创建 |
| GHCR 镜像 | 未发布 |
| 远程 V4 质量工作流 | 未执行；本轮未推送。运行 `33251873844` 仅为 V3/基线历史证据 |

当前工程验证已经达到本地质量和隔离运行收口条件，但由于用户浏览器写入验收和远程 V4 CI 尚未完成，不能将 V4 标记为最终发布完成。

## 外部证据与安全边界

方面六外部证据目录为：

`D:\codex\CC4C_v2_aspect6_manifest_20260830-235316\`

其中保存了基线、静态门禁、后端/前端质量、运行时 OpenAPI 和卷迁移的脱敏摘要；没有保存受保护文件内容、秘密、Cookie、Token、连接凭据、请求体、消息载荷或 Docker 卷内容。

变更前 HEAD 与 `origin/main` 均为 `8f2987267a942655c1059243aaa60cf4bd29748b`，暂存区为空。方面三至五的工作区修改和 V4 当前文档均被保留。`cc4c-a7verify2` 在验证期间保持 9 个容器运行，未被停止、重启或纳入清理目标。

受保护路径只记录了存在状态、长度和 UTC 修改时间，未读取内容；未触碰本机 `application.yml`、任意 `.env.*.local`、`deploy/secrets/local`、数据库备份、上传目录、Docker 卷内容、Redis/RabbitMQ 数据或历史临时证据。

## 命令与门禁记录

时间均为 UTC；逐条开始/结束时间和退出码以外部清单中的脱敏记录为准。

| 时间/证据 | 命令或动作 | 结果 |
| --- | --- | --- |
| 2026-08-30 15:53:18 | 基线 Git 状态、OpenAPI/Flyway 哈希、事件名和受保护路径元数据记录 | 通过；HEAD/origin 同步，暂存区为空 |
| 2026-08-30 16:05:00–16:06:09 | `powershell -NoProfile -File scripts/testing/run-backend-tests.ps1 clean verify` | 退出码 0；160/160，失败/错误/跳过均为 0 |
| 2026-08-30 16:07:11–16:07:35 | 前端 `npm ci`、lint、format:check、安全测试、API 测试、两次 audit、build | 全部退出码 0；安全 4/4、API 10/10、High/Critical 为 0 |
| 2026-08-31 本次最终重跑 | 版本、结构、源码质量、部署模式、文档链接静态门禁及测试 | 全部退出码 0；14/14、9/9、8/8、9/9、7/7；29 个 Markdown 文件 |
| 2026-08-31 本次最终重跑 | `powershell -NoProfile -File scripts/check-code-quality.ps1` | 退出码 0；Spotless 219 个 Java 文件、ESLint、Prettier 和源码质量通过 |
| 2026-08-31 本次最终重跑 | 活动 PowerShell 文件语法解析 | 退出码 0；30/30 通过 |
| 2026-08-31 本次最终重跑 | `docker compose -p cc4c config --quiet`、`cc4c-ci` 配置和 `cc4c-perf --profile performance` 配置 | 全部退出码 0 |
| 2026-08-31 本次最终重跑 | `node scripts/testing/openapi-snapshot.mjs check` | 退出码 0；快照未改写 |

报告更新后已完成最终重跑；提交前仍以最后一次门禁输出和精确暂存清单为准。

## 后端与前端质量

后端使用 Java 21 和 Maven 3.9.16 完成 `clean verify`。Surefire 实际结果为 160 个测试运行、160 个通过，失败 0、错误 0、跳过 0；生成：

- `backend/target/cc4c-4.0.0-SNAPSHOT.jar`
- `backend/target/cc4c-4.0.0-SNAPSHOT-admin-bootstrap.jar`

离线依赖树确认默认运行时 AMQP Client 为 `5.33.1`、Netty 为 `4.1.136.Final`，性能 profile 使用 Netty `4.2.14.Final`。

前端完成 `npm ci`、ESLint、Prettier 检查、Markdown 安全测试、API/当前用户/新增单元测试、完整和生产依赖审计及 Vite 生产构建。安全测试为 4/4，API 测试为 10/10，两次审计均无 High/Critical；构建只产生 canonical `frontend/dist`，`frontend/node_modules` 和 `frontend/dist` 不纳入提交。

## 性能验证

正式容器性能命令：

```powershell
powershell.exe -NoProfile -File .\scripts\performance\run-container-performance.ps1 -StandardRounds 3 -RabbitManagementPort 15689 -MailpitUiPort 18041
```

命令退出码为 0。三轮 `PublicReadStandard` 测量均无错误，结果来自 `temp/cc4c-performance-gatling/container/round-*/measurement/summary.json`：

| 轮次 | 请求数 | 错误数 | P50 | P95 | P99 | 吞吐 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 267443 | 0 | 1 ms | 2 ms | 4 ms | 885.57 req/s |
| 2 | 267435 | 0 | 1 ms | 2 ms | 4 ms | 885.55 req/s |
| 3 | 267116 | 0 | 1 ms | 3 ms | 6 ms | 884.49 req/s |

三轮结束后只停止了 `cc4c-perf` 项目，未删除性能卷；确认 `cc4c-perf` 运行容器为 0，`cc4c-a7verify2` 仍为 9 个运行容器。最终指标文件存在且无 staging 文件，未覆盖 `temp/cc4c-v3-*` 历史证据。

## Compose 隔离验收与卷身份

方面六卷迁移清单为：

`D:\codex\CC4C_v2_aspect6_manifest_20260830-235316\volume-migration-final5.json`

在复制前确认维护窗口、写入冻结、备份 SHA-256、Outbox/Rabbit drain 和外部写入停止条件；Copy 和 Verify 均成功，且是在新 Compose 首次启动前完成的。四个实际存在源卷的摘要校验一致：

| 旧源卷 | 新目标卷 | 复制/验证 |
| --- | --- | --- |
| `cc4c-v3_mysql_data` | `cc4c_mysql_data` | 通过 |
| `cc4c-v3_redis_security_data` | `cc4c_redis_security_data` | 通过 |
| `cc4c-v3_redis_cache_data` | `cc4c_redis_cache_data` | 通过 |
| `cc4c-v3_rabbitmq_data` | `cc4c_rabbitmq_data` | 通过 |

`cc4c-v3_prometheus_data`、`cc4c-v3_grafana_data`、`cc4c-v3_blog_uploads` 和 `cc4c-v3_avatar_uploads` 在迁移时不存在，因此迁移脚本没有为它们创建空目标。之后新 Compose 为自身运行需要创建了对应的 `cc4c_*` 空项目卷；这些卷不代表从旧卷复制了数据。四个旧源卷仍存在，未执行 `DeleteSource`。

隔离 Compose 使用临时端口覆盖文件启动 `cc4c` 项目；后端 readiness/liveness、会话接口、前端、Prometheus、Grafana 和 Mailpit HTTP 检查均返回 200。全量服务重启检查后长期服务均健康；一次手动重启并发触发的 Rabbit one-shot init 首次退出 1，随后使用精确的 `docker compose ... run --rm --no-deps rabbit-init` 重试退出 0，未清空队列或删除卷。验证完成后仅执行 `docker compose -p cc4c ... down`，未使用 `-v`。

运行时 OpenAPI 检查发现的唯一原始差异是隔离临时端口导致的 `servers[0].url`；将该临时 URL 归一化后，与 `docs/reference/openapi.json` 的 schema 完全一致。快照 SHA-256 仍为：

`77B30B15736C0AD3E2E5D5F53C895916F084B0BED459E4A57412F6CFAD177BB1`

## 宿主机模式与浏览器检查

宿主机验收使用工作区外隔离依赖和隔离上传目录：

- `host-preflight.ps1 -Component All` 及各组件预检通过；
- `start-host-stack.ps1 -FrontendMode Dev` 启动后端和 Vite，健康检查通过；
- `stop-host-stack.ps1` 只停止本轮记录的精确 PID，成功回收；
- 静态模式在缺少 Nginx 路径时按设计退出 1，未启动进程；当前 PATH 和工具目录没有可供验收的 Nginx 1.28.3 可执行文件；
- 过程未自动启停 MySQL、Redis、RabbitMQ、Mailpit 或现有 `cc4c-a7verify2` 栈。

隔离 Compose 和宿主机 Dev 前端均完成了所列路由 shell/重定向扫描，包含登录、注册、首页、课程、博客、收藏、用户信息、管理端和消息管理路径。Dev 模式的脱敏错误报告器产生了预期诊断事件，未输出请求头、Cookie、Token、请求体或响应正文。

尚未执行需要输入测试邮箱、密码和验证码的浏览器业务写入 smoke。原因是浏览器工具要求在输入敏感测试数据前获得动作时确认；当前没有使用真实凭据，也没有向隔离数据库写入用户/博客/收藏/评论数据。该项需用户明确确认后再执行。

## 兼容性不变量

- Flyway 文件仍为 V1–V7，文件哈希与方面六基线一致；未执行 Flyway `clean` 或 `repair`。
- `identity.verification-email.requested.v1`、`community.blog.submitted.v1`、`community.blog.reviewed.v1` 三个事件名保持字节级不变。
- OpenAPI schema 内容保持字节级不变；运行时临时端口只用于隔离检查。
- HTTP URL、DTO、Cookie、CSRF、上传路径、Redis/RabbitMQ namespace 和 Compose 服务/网络逻辑保持不变。
- `cc4c-v3_*` 源卷和 `cc4c-a7verify2_*` 资源均保留。

## 未执行项与发布边界

- 未执行静态 Nginx 宿主机验收：没有用户提供的、已验证的 Nginx 1.28.3 可执行文件。
- 未执行需要敏感测试数据输入的浏览器业务写入：等待用户动作时确认。
- 未执行远程 V4 GitHub Actions：本轮授权不含推送；现有 `33251873844` 不代表 V4 当前结果。
- 未执行源卷删除：用户验收和远程质量工作流尚未完成，且本轮明确保留源卷。
- 未创建 SemVer 标签、未推送、未发布 GHCR 镜像。

本地收口提交仍可在所有已授权本地门禁通过后创建，但提交说明和报告必须继续如实保留上述未执行边界；V4 最终完成状态要等远程 CI 和用户验收完成后再收口。

## 提交前清单边界

最终暂存前的只读候选清单包含 390 个 tracked 差异路径和 456 个未跟踪文件，去重后 846 个精确路径。路径规则检查发现 0 个受保护路径命中、0 个工作区外/未批准根目录路径；暂存区仍为空。候选范围仅覆盖方面一至六的 canonical/legacy 重命名、当前文档、脚本、配置、源码和测试，不包含 `application.yml`、`.env.*.local`、`deploy/secrets/local`、`temp`、`target`、`dist`、`node_modules`、数据库备份、上传文件或 Docker 数据。

## 验收结论

本报告不把未执行项当作通过项，不宣称 V4 已最终发布完成。下一步是重跑报告更新后的静态门禁和提交前 allowlist 检查；浏览器业务写入、静态 Nginx、远程 CI、推送、标签和 GHCR 发布仍分别受其授权边界约束。

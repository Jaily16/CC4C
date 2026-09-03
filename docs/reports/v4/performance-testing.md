# CC4C V3–V4 性能测试证据汇总

> 本文是在 V5 删除性能与容器实现之前形成的只读证据汇总。它只复述 Git 跟踪文档、固定提交中的源码定义和公开 GitHub Actions 结果，不读取或复制已忽略的 `temp` 原始结果、本机性能环境文件、凭据、数据库、上传数据、Docker 卷或历史备份。

## 1. 范围、证据等级与来源

### 1.1 证据等级

| 标签 | 含义 |
| --- | --- |
| `[V3 原始实验]` | V3 当时直接执行并写入 Git 跟踪记录的实验结果：缓存初测 `bc7dcf8`、观测与 Gatling `f0f6fa1`、容器性能 `a22a329`。 |
| `[V3 收口重跑]` | V3 方面六收口时，在同一受控方法下重新执行缓存基准或 smoke 后记录的结果。 |
| `[V4 本地重跑]` | V4 发布验收期间在本机执行的三轮容器性能结果。 |
| `[V4 Actions 重跑]` | 提交 `ed3c7bb` 对应的公开 GitHub Actions release `33360497982` 中实际执行的结果。 |
| `[源码定义]` | 从相应历史提交内 Git 跟踪的 Java、PowerShell、POM、Compose 或工作流读取的方法、参数和门禁。 |
| `[历史引用]` | Git 中只保存了汇总值或中位数，原始 Gatling/JSON/Prometheus/EXPLAIN 文件没有提交。 |
| `[未留存]` | 授权证据中没有该字段，本文不使用其他时期的版本或数据代替，也不推测或补造。 |

这些标签描述的是证据来源，不表示不同环境之间可以直接做性能同比。V3 本机、V3 容器、V4 本机和 GitHub Runner 必须分别理解。

### 1.2 可追溯来源

仍保留在仓库中的主要文档来源：

- [V3 方面六性能与环境证据](../../history/reports/v3/aspect6/performance.md)
- [V3 方面六故障演练](../../history/reports/v3/aspect6/fault-drills.md)
- [V3 方面七容器性能证据](../../history/reports/v3/aspect7/performance.md)
- [V3 方面七验证报告](../../history/reports/v3/aspect7/validation.md)
- [V3–V4 项目迭代记录](../../history/project-iteration-record.md)
- [V4 验证与发布准备报告](../../development/v4-validation-report.md)
- [V5 迭代规划](../../development/v5-iteration-plan.md)

方面三会删除的性能实现使用不可变提交链接，而不是指向工作树文件：

- [V3 缓存初测记录（`bc7dcf8`）](https://github.com/Jaily16/CC4C/blob/bc7dcf839d531a5bcef8cb7643f62ccc9db94974/README.md)
- [V3 方面六 Gatling 入口（`f0f6fa1`）](https://github.com/Jaily16/CC4C/blob/f0f6fa117e180ca1f41fd09f7efb4cc5a502bc9e/back-end/CC4C/run-aspect6-gatling.ps1)
- [V3 容器性能入口（`a22a329`）](https://github.com/Jaily16/CC4C/blob/a22a3297fea7e2750b3f833511abe7abda927123/deploy/scripts/run-container-performance.ps1)
- [V4 数据生成器](https://github.com/Jaily16/CC4C/blob/d243f6a577120d3dd11206815bea802a1c1a6b42/backend/src/test/java/com/cc4c/performance/PerformanceDataSeeder.java)
- [V4 缓存基准实现](https://github.com/Jaily16/CC4C/blob/d243f6a577120d3dd11206815bea802a1c1a6b42/backend/src/test/java/com/cc4c/performance/PerformanceBenchmarkApplication.java)
- [V4 Gatling 公共支持代码](https://github.com/Jaily16/CC4C/blob/d243f6a577120d3dd11206815bea802a1c1a6b42/backend/src/gatling/java/com/cc4c/performance/PerformanceSupport.java)
- [V4 Gatling 执行脚本](https://github.com/Jaily16/CC4C/blob/d243f6a577120d3dd11206815bea802a1c1a6b42/scripts/performance/run-performance-gatling.ps1)
- [V4 容器性能脚本](https://github.com/Jaily16/CC4C/blob/ed3c7bb62b4402bd1a4e7aa616955f938cf2aaaf/scripts/performance/run-container-performance.ps1)
- [V4 release 工作流](https://github.com/Jaily16/CC4C/blob/ed3c7bb62b4402bd1a4e7aa616955f938cf2aaaf/.github/workflows/release.yml)

公开远端证据：

- [最终 release `33360497982`](https://github.com/Jaily16/CC4C/actions/runs/33360497982)
- [最终 `container-performance` job `99391819204`](https://github.com/Jaily16/CC4C/actions/runs/33360497982/job/99391819204)
- [Linux 输出归属失败 release `33358398336`](https://github.com/Jaily16/CC4C/actions/runs/33358398336)
- [对应失败 job `99386348181`](https://github.com/Jaily16/CC4C/actions/runs/33358398336/job/99386348181)
- [调度断言失败 release `33357434615`](https://github.com/Jaily16/CC4C/actions/runs/33357434615)
- [修复后 quality `33359828199`](https://github.com/Jaily16/CC4C/actions/runs/33359828199)

## 2. 环境、隔离边界与固定数据

### 2.1 环境矩阵

| 项目 | V3 本机原生实验 | V3 容器实验 | V4 本机容器重跑 | V4 GitHub Actions |
| --- | --- | --- | --- | --- |
| 证据标签 | `[V3 原始实验]` / `[V3 收口重跑]` | `[V3 原始实验]` | `[V4 本地重跑]` | `[V4 Actions 重跑]` |
| Git 基线 | 方面六记录为 `5daf68c`，当时方面六变更尚未提交；最终证据提交为 `f0f6fa1` | 实现提交 `a22a329` | 验证报告最终修复提交 `ed3c7bb` | `ed3c7bb62b4402bd1a4e7aa616955f938cf2aaaf` |
| 操作系统 | Windows 11 专业版 `10.0.26200` | Windows 11 专业版 `10.0.26200` | 同一 V4 验收开发机；报告未重复记录系统版本 | `ubuntu-24.04` |
| CPU / 内存 | AMD Ryzen 7 9700X、16 逻辑处理器；31.1 GiB | AMD Ryzen 7 9700X、16 逻辑处理器；31.1 GiB | `[历史引用]` V4 验收沿用同一本机，但性能表未重新采集硬件快照 | `[未留存]` Runner 的具体 CPU 和内存没有进入授权证据 |
| Java / Maven | Eclipse Temurin 21.0.12.1；Maven 3.9.16 | Java 21.0.12.1；性能工具由 Maven 3.9.16 / Temurin 21 镜像构建 | Java 21；Maven 3.9.16 基线 | Maven 3.9.16 / Temurin 21 性能工具镜像 |
| 应用和数据访问 | Spring Boot 3.5.16、MyBatis-Plus 3.5.17、HikariCP | 同左 | 同左 | 同左 |
| Gatling | Java DSL 3.15.1；Maven Plugin 4.21.10 | Gatling 3.15.1 | Gatling 3.15.1 | Gatling 3.15.1 |
| MySQL | `[未留存]` 本机服务实际版本未记录 | MySQL 8.4.11 固定镜像 | MySQL 8.4.11 固定镜像 | MySQL 8.4.11 固定镜像 |
| Redis | `[未留存]` 本机服务实际版本未记录 | Redis 7.4.10 固定镜像；安全与业务缓存使用两个服务 | Redis 7.4.10 固定镜像；安全与业务缓存使用两个服务 | Redis 7.4.10 固定镜像；安全与业务缓存使用两个服务 |
| RabbitMQ | RabbitMQ 4.3.5，启用 `rabbitmq_prometheus` | RabbitMQ 4.3.5 固定镜像 | RabbitMQ 4.3.5 固定镜像 | RabbitMQ 4.3.5 固定镜像 |
| Prometheus / Grafana | Prometheus 3.13.2；Grafana OSS 13.1.0 | 同版本固定镜像 | 同版本固定镜像 | 性能 job 采集应用 Prometheus 指标；Grafana 不参与请求负载 |
| Docker / Compose | 不适用 | Engine 28.0.4；Compose 2.34.0 | `[未留存]` 验收报告未重新记录版本 | GitHub 托管 Runner；具体 Engine/Compose 版本未写入汇总报告 |

容器版本来自 V3/V4 对应提交的 Compose 固定镜像；它们不能反向填充 V3 本机 MySQL、Redis 的未知版本。

### 2.2 固定数据集

`[源码定义]` 数据生成器使用固定种子 `20260827`（源码常量 `20_260_827L`），数据量如下：

| 类型 | 数量 |
| --- | ---: |
| 用户 | 2,000 |
| 课程 | 1,000 |
| 博客 | 20,000 |
| 课程收藏 | 50,000 |
| 博客收藏 | 50,000 |
| 顶层评论 | 50,000 |
| 回复 | 50,000 |
| 收藏、评论与回复合计 | 200,000 |

生成器只删除工具保留的用户、课程、博客和评论 ID 区间后重新写入确定性数据。Flyway `clean` 被禁用，不执行 `DROP DATABASE`、`clean` 或 `repair`。性能数据库名必须精确以 `_perf_test` 结尾，并与显式确认变量相同。

Session、业务缓存和 RabbitMQ 使用彼此不同的性能 namespace。认证混合场景只使用 20 个固定性能用户和保留资源；性能流程不调用验证码、博客审核或真实邮件。容器性能模式关闭 Dispatcher 和 Consumer，避免数据准备产生的消息干扰请求测量。

## 3. 缓存基准

### 3.1 方法

`[源码定义]` 基准固定访问九个公开读取目标：课程首页、课程语言列表、课程详情、课程模块、课程推荐、博客首页、博客全部列表、博客语言列表和已审核博客详情。

每次完整基准依次运行无缓存基线和缓存开启模式：

1. 每种模式启动独立应用上下文并使用随机运行 namespace。
2. 冷路径对九个目标顺序请求十个周期，共 90 次请求；缓存开启时每个周期前清理本轮缓存 namespace，保证每个周期都是冷读取。
3. 缓存开启模式在热路径测量前清理 namespace，再把九个目标顺序预热五遍。
4. 预热后清零缓存指标和 MyBatis 查询计数。
5. 每种模式执行三轮；每轮 3,000 请求、并发上限 16，目标按固定顺序循环。
6. p50、p95、p99、吞吐、请求数和错误数分别排序取三轮中位数；MyBatis 拦截器只累计测量阶段的 `SELECT`。

门禁为：冷/热 HTTP 错误全部为 0；热缓存正向命中率不低于 85%；MyBatis SELECT 减少不低于 80%；热 p95 改善不低于 30%；缓存冷 p95 相对无缓存冷 p95 的退化不超过 15%；缓存热 p99 相对无缓存热 p99 的退化不超过 15%。

### 3.2 V3 原始实验

`[V3 原始实验]` 提交 `bc7dcf8` 保存的同机三轮中位数：

| 指标 | 无缓存基线 | 热缓存 | 结论 |
| --- | ---: | ---: | --- |
| HTTP 错误 | 0 | 0 | 通过 |
| 热缓存命中率 | 不适用 | 100% | 通过 ≥85% 门禁 |
| MyBatis SELECT | 10,995 | 0 | 减少 100% |
| p50 | 14.727 ms | 3.224 ms | 改善 |
| p95 | 182.514 ms | 5.177 ms | 改善约 97.16% |
| p99 | 207.472 ms | 6.720 ms | 未恶化 |
| 吞吐 | 464.458 req/s | 4,633.079 req/s | 同机受控对照 |
| 冷路径 p95 | 96.047 ms | 96.279 ms | 退化约 0.24%，小于 15% |

### 3.3 V3 收口重跑

`[V3 收口重跑]` 方面六在相同专用数据库、Redis、固定请求组合和并发 16 下得到：

| 指标 | 无缓存基线 | 热缓存 | 结论 |
| --- | ---: | ---: | --- |
| HTTP 错误 | 0 | 0 | 通过 |
| 热缓存命中率 | 不适用 | 100% | 通过 ≥85% 门禁 |
| MyBatis SELECT | 10,995 | 0 | 减少 100% |
| p95 三轮中位数 | 181.599 ms | 5.486 ms | 改善约 96.98% |
| p99 三轮中位数 | 207.291 ms | 7.367 ms | 未恶化 |
| 冷路径 p95 | 95.875 ms | 97.848 ms | 退化约 2.06%，小于 15% |

`[历史引用]` 两次缓存实验提交的都是汇总或中位数。各轮 p50/p95/p99、吞吐和错误原值位于当时被忽略的输出中，没有进入 Git，因此本文不构造逐轮表。收口重跑的 p50 和吞吐汇总也未在跟踪报告中留存。

## 4. Gatling 负载模型

### 4.1 公共请求组合

`[源码定义]` 一次 `publicRead` 链依次访问九个课程/博客接口，每次链结束暂停 1 秒。HTTP 客户端共享连接，单主机最大连接数为 200；Base URL 只允许 loopback HTTP，容器模式还必须显式确认 `backend-perf`。

### 4.2 场景定义

| 场景 | 并发和时间 | 行为 | 门禁 |
| --- | --- | --- | --- |
| `PublicReadSmoke` | 闭环 20 并发，1 分钟 | 重复九接口公共读取 | 错误数 0；p95 ≤500 ms；p99 ≤1,000 ms |
| `PublicReadStandard` | 每轮先以闭环并发 1→100 预热 2 分钟，再闭环 100 并发测量 5 分钟；共三轮 | 重复九接口公共读取 | 场景本身不写绝对延迟断言；外部汇总和对照脚本执行门禁 |
| `AuthenticatedMixed` | 闭环 20 并发，5 分钟；20 个固定用户 | 先登录，再按 20% 公共读取、70% 已登录读取、10% 课程收藏增删随机混合 | 错误数 0 |
| `StepCapacity` | 50、100、200、500 并发各 2 分钟，按 0、2、4、6 分钟延迟依次开始 | 每阶段重复公共读取 | 50 和 100 并发组失败率 ≤1%；更高阶段只定位本机拐点 |

`StepCapacity` 的 200/500 并发不是已认证容量门禁，任何阶段都不构成生产容量声明。

### 4.3 V3 汇总结果

| 场景 | 请求数 | 错误 | p95 | p99 | 吞吐 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `[V3 原始实验]` `AuthenticatedMixed` | 158,023 | 0 | 11 ms | 106 ms | 523.25 req/s |
| `[V3 原始实验]` `StepCapacity` | 885,823 | 0 | 9 ms | 16 ms | 1,837.81 req/s |
| `[V3 收口重跑]` `PublicReadSmoke` | 10,469 | 0 | 7 ms | 19 ms | 168.85 req/s |

`[历史引用]` 以上三个场景只有单次聚合结果被跟踪，没有可授权恢复的逐轮数据。

## 5. 观测开销对照

### 5.1 方法和门禁

`[V3 原始实验]` 对同一数据、提交、JVM 和硬件分别执行观测关闭和观测开启的 `PublicReadStandard`。每种模式各三轮，每轮先预热 2 分钟，再以闭环 100 并发测量 5 分钟。

- 基线模式要求管理 HTTP 端口关闭。
- 观测开启要求管理端口和 Prometheus 端口可用，包含 Micrometer 指标、ECS 结构化请求完成日志和 Prometheus 每 15 秒抓取。
- 对照脚本要求两侧都恰好有三个摘要并分别取中位数。
- 两侧 HTTP 错误必须为 0；观测开启 p95 退化不得超过 10%，p99 退化不得超过 15%，吞吐下降不得超过 10%。

### 5.2 结果

| 指标 | 观测关闭 | 观测开启 | 结论 |
| --- | ---: | ---: | --- |
| HTTP 错误 | 0 | 0 | 通过 |
| p95 | 5 ms | 5 ms | 0% 退化 |
| p99 | 7 ms | 8 ms | 退化 `(8-7)/7=14.29%`，小于 15% |
| 吞吐 | 869.98 req/s | 868.91 req/s | 下降约 `(869.98-868.91)/869.98=0.12%`，小于 10% |

观测期间 Hikari pending 最大值为 0。Prometheus 保存 3,672 条后端时序，其中 HTTP 路由时序 21 条，保持在路由模板和标签基数门禁内。

`[历史引用]` Git 只保存了两种模式的三轮中位数，六轮原值未跟踪；本文不会从已忽略目录恢复或猜测这些数据。

## 6. 容器性能证据

三组结果的提交、运行环境和脚本阶段不同。下表只验证各自受控环境内的回归门禁，不跨组计算“提升”或“下降”。

### 6.1 V3 容器结果

`[V3 原始实验]` `PublicReadSmoke` 使用 20 并发、1 分钟：10,656 请求、0 错误、p95 5 ms、p99 18 ms，通过零错误、p95 ≤500 ms 和 p99 ≤1 秒门禁。

`PublicReadStandard` 三轮测量如下：

| 轮次 | 错误 | p95 | p99 | 吞吐 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 0 | 2 ms | 5 ms | 885.17 req/s |
| 2 | 0 | 2 ms | 4 ms | 886.13 req/s |
| 3 | 0 | 2 ms | 4 ms | 886.52 req/s |

三轮中位数为：错误 0、p50 1 ms、p95 2 ms、p99 4 ms、吞吐 886.13 req/s。`[未留存]` 每轮请求数和每轮 p50 没有写入跟踪报告，不能由吞吐反推。

### 6.2 V4 本地三轮重跑

`[V4 本地重跑]` 正式命令退出码为 0：

| 轮次 | 请求数 | 错误 | p50 | p95 | p99 | 吞吐 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 267,443 | 0 | 1 ms | 2 ms | 4 ms | 885.57 req/s |
| 2 | 267,435 | 0 | 1 ms | 2 ms | 4 ms | 885.55 req/s |
| 3 | 267,116 | 0 | 1 ms | 3 ms | 6 ms | 884.49 req/s |

各字段按三值排序后，中位数为：请求 267,435、错误 0、p50 1 ms、p95 2 ms、p99 4 ms、吞吐 885.55 req/s。

运行结束只停止了精确的 `cc4c-perf` 性能项目，没有删除本地性能卷，也没有覆盖 V3 历史输出。

### 6.3 V4 GitHub Actions 三轮重跑

`[V4 Actions 重跑]` 最终 release `33360497982` 的 `container-performance` job 在 `ubuntu-24.04` 上成功：

| 轮次 | 请求数 | 错误 | p50 | p95 | p99 | 吞吐 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 266,884 | 0 | 1 ms | 4 ms | 9 ms | 883.72 req/s |
| 2 | 267,419 | 0 | 1 ms | 3 ms | 6 ms | 885.49 req/s |
| 3 | 267,291 | 0 | 1 ms | 3 ms | 8 ms | 885.07 req/s |

中位数为：请求 267,291、错误 0、p50 1 ms、p95 3 ms、p99 8 ms、吞吐 885.07 req/s。该 job 从 2026-08-31 05:32:03 UTC 运行到 05:58:33 UTC；整个 release 从 05:25:36 UTC 运行到 06:07:23 UTC。

## 7. 历史命令

以下命令按运行时期保留。V3 路径来自当时提交；V4 路径来自重构后的仓库。方面三删除对应资产后，这些命令只用于审计，不能再从 V5 当前树直接执行。

### 7.1 V3 缓存基准

```powershell
Set-Location .\back-end\CC4C
.\run-aspect4-benchmark.ps1
```

### 7.2 V3 观测与 Gatling

当时的脚本参数定义可复核为以下标准调用。跟踪文档没有保存操作者终端历史，因此这些命令表示源码规定的可重复入口，不声称逐字符还原当时的交互式命令行：

```powershell
.\run-aspect6-gatling.ps1 -Simulation PublicReadStandard -Mode baseline -Rounds 3
.\run-aspect6-gatling.ps1 -Simulation PublicReadStandard -Mode observability-on -Rounds 3
.\run-aspect6-gatling.ps1 -Simulation AuthenticatedMixed -Rounds 1
.\run-aspect6-gatling.ps1 -Simulation StepCapacity -Rounds 1
.\run-aspect6-gatling.ps1 -Simulation PublicReadSmoke -Rounds 1
```

### 7.3 V3 容器性能

```powershell
.\deploy\scripts\run-container-performance.ps1 -StandardRounds 3
```

### 7.4 V4 本地正式重跑

```powershell
powershell.exe -NoProfile -File .\scripts\performance\run-container-performance.ps1 `
  -StandardRounds 3 -RabbitManagementPort 15689 -MailpitUiPort 18041
```

### 7.5 V4 GitHub Actions

```powershell
./scripts/performance/run-container-performance.ps1 -StandardRounds 3
```

工作流先通过质量门禁，再运行独立性能项目；无论成功失败，最后只删除该次 release 的 `cc4c-perf` 项目资源。

## 8. 失败、修复与最终闭环

### 8.1 V3 依赖故障暴露的性能问题

`[V3 收口重跑]` Redis/MySQL 故障演练暴露两个会影响失败延迟和 smoke 稳定性的缺口：

1. Lettuce 连接异常被外层运行时异常包装时曾落入通用 HTTP 500。修复后使用带深度和循环保护的 cause-chain 分类，Redis 依赖错误统一返回脱敏 503。
2. Hikari 默认 30 秒连接等待使 MySQL 故障请求超出验收时间。修复后连接等待为 3,000 ms、验证等待为 1,000 ms，并校验二者范围和大小关系。

修复后重新执行缓存基准和 `PublicReadSmoke`，得到 10,469 请求、0 错误、p95 7 ms、p99 19 ms。该结果证明健康路径仍满足 smoke 门禁，不把故障演练本身当作吞吐测量。

### 8.2 V4 调度相关断言失败

早期 release `33357434615` 在内置 quality/backend 阶段失败于 `BusinessCacheTest.concurrentMissesUseOneLocalLoader` 的 `lockWaits` 调度断言。其他已完成质量 job 通过，`container-performance` 和 publish 没有执行，因此该运行没有性能结果。

修复保留八次确定性 miss 和单次 loader 的业务契约，移除依赖线程调度时序的 `lockWaits` 断言。本地并发回归和后端门禁通过后，后续 release 才进入容器性能阶段。

### 8.3 Linux 性能输出归属失败

release `33358398336` 的质量和 Compose smoke 已通过，`container-performance` 进入实际性能流程，但容器以 root 写入 bind mount。宿主 PowerShell 汇总器尝试写入第一轮 `summary.json` 时得到 `Access to the path ... is denied`，job 退出码为 1，publish 被跳过。

该失败发生在报告落盘权限，不是 HTTP 错误、延迟或吞吐门禁失败。提交 `ed3c7bb` 增加 Unix 专用的输出所有权恢复：在 smoke、每次预热和每次测量后，以隔离性能工具容器把本轮 Gatling 输出递归交还给 Runner 的 UID/GID，不触碰其他目录或 Docker 卷。

修复提交随后通过 quality `33359828199`；最终 release `33360497982` 完成质量、Compose smoke、三轮容器性能及后续发布 job。旧失败运行继续作为诊断证据保留。

## 9. 结论与使用限制

- 固定数据、固定负载和记录环境内，V3 缓存初测与收口重跑均满足缓存命中、SELECT 减少、热点延迟改善和冷路径退化门禁。
- V3 观测开启相对关闭的 p95、p99 和吞吐变化均处于既定阈值内；这只覆盖当时的 Micrometer、结构化请求日志和 15 秒 Prometheus 抓取组合。
- V3 容器、V4 本地和 V4 Actions 的 smoke/Standard 结果分别通过各自零错误与性能回归检查。
- V3 与 V4 的提交、宿主环境、容器脚本和 Runner 不完全相同，不应把三组数字直接解释为版本性能趋势。
- `StepCapacity`、100 并发 Standard、容器吞吐及所有分位数只代表受控实验，不是生产容量、SLA、可用性目标或扩容承诺。
- 被忽略的原始 Gatling HTML、simulation log、JSON、Prometheus 文本和 EXPLAIN 没有进入 Git。本文明确保留其缺失状态，不从本机历史目录、数据库或备份恢复，也不把未执行或未留存的数据描述为通过。

# 方面七容器性能证据

## 目的与边界

该基准证明容器化交付在当前开发机、固定数据和负载下满足项目门禁，不表示生产容量，也不与方面六本机原生结果直接比较。

环境为 Windows 11、Ryzen 7 9700X、31.1 GiB、Docker Engine 28.0.4、Java 21.0.12.1 和 Gatling 3.15.1。性能 profile 使用 Compose 内独立 `cc4c_perf_test`，随机种子 `20260827`，数据规模为 2,000 用户、1,000 课程、20,000 博客和 200,000 条互动关系。Session、缓存与 Rabbit namespace 独立；Dispatcher 和 Consumer 关闭，避免性能准备污染运行消息。

## PublicReadSmoke

- 并发用户：20。
- 持续时间：1 分钟。
- 请求组合：课程首页/详情/语言/模块/推荐，博客首页/列表/语言/详情。
- 请求数：10,656。
- 错误数：0。
- p95：5 ms。
- p99：18 ms。

结果通过错误为 0、p95 不超过 500 ms、p99 不超过 1 秒的门禁。

## PublicReadStandard

- 闭环并发用户：100。
- 每轮：2 分钟预热、5 分钟测量。
- 三轮使用同一提交、数据库、缓存、JVM 参数与硬件。

| 统计 | 三轮中位数 |
| --- | ---: |
| 错误 | 0 |
| p50 | 1 ms |
| p95 | 2 ms |
| p99 | 4 ms |
| 吞吐 | 886.13 req/s |

各轮测量摘要：

| 轮次 | 错误 | p95 | p99 | 吞吐 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 0 | 2 ms | 5 ms | 885.17 req/s |
| 2 | 0 | 2 ms | 4 ms | 886.13 req/s |
| 3 | 0 | 2 ms | 4 ms | 886.52 req/s |

## 证据保留

原始 Gatling HTML、simulation log、每轮 JSON、Prometheus 文本和中位数 JSON 位于已忽略的 `temp/cc4c-v3-aspect7-gatling/`，不会提交。若在其他硬件或 GitHub Runner 上运行，应重新记录系统、镜像 digest、JVM 参数、数据规模、三轮结果与误差，不能复用本文数字作为其结果。

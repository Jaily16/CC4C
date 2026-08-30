# 方面六性能与环境证据

## 环境

| 项目 | 实际值 |
| --- | --- |
| Git 基线 | `5daf68c`；方面六变更尚未提交 |
| 操作系统 | Windows 11 专业版 10.0.26200 |
| CPU / 内存 | AMD Ryzen 7 9700X，16 逻辑处理器；31.1 GiB 可见内存 |
| Java / Maven | Eclipse Temurin OpenJDK 21.0.12.1 LTS；Maven 3.9.16 |
| 应用 | Spring Boot 3.5.16、MyBatis-Plus 3.5.17、HikariCP |
| Gatling | Java DSL 3.15.1；Maven Plugin 4.21.10 |
| Prometheus / Grafana | Prometheus 3.13.2；Grafana OSS 13.1.0 |
| RabbitMQ | RabbitMQ 4.3.5，启用 `rabbitmq_prometheus` |

性能库名称精确以 `_perf_test` 结尾，脚本同时校验确认变量。数据由固定种子 `20260827` 生成：2,000 用户、1,000 课程、20,000 博客以及合计 200,000 条收藏、评论与回复关系。Session、缓存和 RabbitMQ 使用彼此不同的性能 namespace，压测不调用验证码、博客审核或真实邮件。

## 观测开销对照

`PublicReadStandard` 使用闭环 100 并发，每轮先预热 2 分钟再测量 5 分钟，观测关闭和开启各运行三轮并取中位数。观测开启包含 Micrometer 指标、ECS 请求完成日志和 Prometheus 15 秒抓取。

| 指标 | 观测关闭 | 观测开启 | 门禁结论 |
| --- | ---: | ---: | --- |
| HTTP 错误 | 0 | 0 | 通过 |
| p95 | 5 ms | 5 ms | 0% 退化，通过 |
| p99 | 7 ms | 8 ms | 14.29% 退化，小于 15% 上限 |
| 吞吐 | 869.98 req/s | 868.91 req/s | 下降 0.12%，小于 10% 上限 |

观测期间 Hikari pending 最大值为 0；Prometheus 后端时序 3,672 条，其中 HTTP 路由时序 21 条，保持在路由模板和标签基数门禁内。

## 其他 Gatling 场景

| 场景 | 请求数 | 错误 | p95 | p99 | 吞吐 |
| --- | ---: | ---: | ---: | ---: | ---: |
| AuthenticatedMixed | 158,023 | 0 | 11 ms | 106 ms | 523.25 req/s |
| StepCapacity | 885,823 | 0 | 9 ms | 16 ms | 1,837.81 req/s |
| 当前构建 PublicReadSmoke | 10,469 | 0 | 7 ms | 19 ms | 168.85 req/s |

`StepCapacity` 的 50→100→200→500 并发仅用于定位本机拐点，不构成生产容量承诺。当前构建 smoke 在 Redis/数据库故障修复和 Hikari 超时配置完成后重跑，验证健康路径仍满足 p95 ≤500 ms、p99 ≤1 s 和零错误门禁。

## 缓存基准回归

方面六收口后使用相同专用库、Redis、固定请求组合和并发 16 重跑方面四基准：

| 指标 | 无缓存基线 | 热缓存 | 结论 |
| --- | ---: | ---: | --- |
| HTTP 错误 | 0 | 0 | 通过 |
| 缓存命中率 | 不适用 | 100% | ≥85% 门禁通过 |
| MyBatis SELECT | 10,995 | 0 | 减少 100% |
| p95 三轮中位数 | 181.599 ms | 5.486 ms | 改善 96.98% |
| p99 三轮中位数 | 207.291 ms | 7.367 ms | 未恶化 |
| 冷路径 p95 | 95.875 ms | 97.848 ms | 退化约 2.06%，小于 15% 上限 |

这些数字只证明当前本机受控对照满足既定相对门禁。

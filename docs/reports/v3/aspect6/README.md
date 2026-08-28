# CC4C V3 方面六验证报告

## 结论

方面六“可观测性与性能证据”于 2026-08-28 在基线提交 `5daf68c` 上完成实现和本地验证。自动测试、Prometheus 规则、Grafana Dashboard、性能门禁、故障演练和用户浏览器验收均已通过；方面七的 Docker、容器编排、Testcontainers 和 CI/CD 未实施。

本报告只保存脱敏摘要。Gatling 原始报告、Prometheus 查询结果、应用日志和临时故障文件位于已忽略的 `temp/`，不得提交。

## 验证总览

| 门禁 | 实际结果 |
| --- | --- |
| 后端 `clean verify` | 150/150 通过，0 失败、0 错误、0 跳过 |
| 前端 | `npm ci` 和 Vite 生产构建通过；仍有 10 个既有 npm 漏洞提示和大 chunk 警告 |
| Flyway | V1–V7、已有库升级、空库重建、重复 migrate 和 validate 通过 |
| Prometheus | 配置有效；20 条告警规则通过 `check rules` 与规则单元测试 |
| Grafana | 三个固定 UID Dashboard 已 Provision，用户确认均有数据且无查询错误 |
| 性能 | 方面四缓存门禁重跑通过；方面六标准、混合、阶梯与当前构建 smoke 均为 0 HTTP 错误 |
| 故障恢复 | Redis、MySQL 应用连接、RabbitMQ、消费者和 SMTP 场景符合设计并全部恢复 |
| 安全 | 管理 Basic 身份与业务会话隔离；JAR 不含本机 `application.yml`；日志/指标/报告不含秘密 |

## 证据索引

- [性能与环境](performance.md)
- [告警、健康与 Dashboard](observability.md)
- [故障演练](fault-drills.md)

本机性能结果用于同一硬件、提交、数据和脚本下的相对比较，不表示生产容量或 SLA。

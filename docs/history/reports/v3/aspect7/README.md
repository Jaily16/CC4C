# CC4C V3 方面七证据索引

方面七以本地提交 `f0f6fa1` 为唯一基线，于 2026-08-29 完成容器化、Testcontainers 测试隔离、前端安全升级、GitHub Actions 交付定义、容器性能与故障验证和用户浏览器验收；实现已收口为本地 `main` 提交 `a22a329`。

| 文档 | 内容 |
| --- | --- |
| [validation.md](validation.md) | 自动测试、Compose、OpenAPI、观测、镜像和故障门禁 |
| [performance.md](performance.md) | 容器 Gatling 环境、结果与适用边界 |
| [supply-chain.md](supply-chain.md) | 依赖审计、Trivy、工作流、镜像和未发布状态 |
| [browser-acceptance.md](browser-acceptance.md) | 用户逐项确认的浏览器与持久化验收 |

关联交付物：

- [容器运行手册](../../../../operations/container-runbook.md)
- [容器交付架构](../../../../architecture/container-delivery.md)
- [ADR-0001](../../../../architecture/adr/0001-containerized-delivery.md)
- [OpenAPI 快照](../../../../reference/openapi.json)

本目录只提交脱敏摘要。Testcontainers 状态、Gatling 原始报告、Prometheus TSDB、日志、数据库备份、卷数据、secret 和镜像导出均保持忽略。

本次已经创建方面七本地提交，但没有执行 Git 推送、Git 标签或 GHCR 发布。因此“工作流已定义并通过本地等价门禁”不表示 GitHub-hosted Actions 已运行，“本地镜像 ID”也不表示存在 registry digest。

# 方面七供应链与发布证据

## 已验证

- 前端完整和生产依赖 `npm audit --audit-level=high` 均无 High/Critical。
- Trivy 对明确安全的源码集合以及当前前后端镜像执行 vuln、secret、misconfig 扫描，High/Critical 为 0。
- Compose 主配置与外部 SMTP 覆盖通过解析，配置展开不包含任何本机 secret 值。
- 后端 JAR 与镜像不含本机 `application.yml`。
- OpenAPI 快照、Prometheus 配置/规则/规则测试、Grafana Dashboard JSON 和工作流 actionlint 均通过。
- GitHub Actions 第三方 Action 固定到完整提交 SHA；Dependabot 覆盖 Maven、npm、Docker 与 Actions。

## 质量工作流

`.github/workflows/quality.yml` 在 PR、main push、手工调用或复用时定义：

1. Java 21 + Testcontainers `clean verify`。
2. Node 24.18.0/npm 11.16.0、Markdown 安全测试、两类 audit 与生产构建。
3. Dependency Review、Trivy 源码扫描。
4. Compose、Prometheus、Grafana 与 OpenAPI 漂移门禁。
5. amd64 Compose smoke、非 root/JAR/secret/Rabbit/上传持久化检查和前后端镜像扫描。
6. `always()` 只删除 CI 项目及其卷。

## 发布工作流

`.github/workflows/release.yml` 只响应严格 `vX.Y.Z` 标签：

1. 重跑完整质量工作流。
2. 在隔离 Compose profile 中运行三轮容器标准性能门禁。
3. Buildx 构建 `linux/amd64,linux/arm64`。
4. 发布 SemVer 和 Git SHA 不可变标签至 `ghcr.io/jaily16/cc4c-backend` 与 `ghcr.io/jaily16/cc4c-frontend`。
5. 生成 SBOM、最大 provenance 和 GitHub artifact attestation。

权限限制为按作业授予的 `packages:write`、`id-token:write` 与 `attestations:write`；没有服务器部署步骤。

## 尚未发生

- 方面七文件尚未提交或推送。
- 没有创建 SemVer Git 标签。
- GitHub-hosted Actions 尚未运行。
- GHCR 镜像、SBOM、provenance 和 attestation 尚未发布。
- 本地镜像没有 RepoDigest，只有内容寻址 image ID。

因此任何后续发布说明都必须引用真实远程工作流 URL 与 Buildx/registry digest，不得把本文的本地门禁或 image ID冒充发布证据。

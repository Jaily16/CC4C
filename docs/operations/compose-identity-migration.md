# Compose 身份迁移说明

## 身份与兼容边界

方面五将默认 Compose 项目切换为 `cc4c`。当前活动配置使用逻辑卷名 `mysql_data`、`redis_security_data`、`redis_cache_data`、`rabbitmq_data`、`prometheus_data`、`grafana_data`、`blog_uploads` 和 `avatar_uploads`；在不写死卷 `name:` 的前提下，默认实际卷名为 `cc4c_*`。服务名、网络逻辑、端口、上传挂载、`deploy/secrets/local`、数据库结构以及 Redis/RabbitMQ 命名空间不变。

旧 `cc4c-v3` 项目和卷是回滚兼容资产，不是可以猜测或替代的新数据源。旧卷到新卷的唯一允许映射为：

| 旧卷 | 新卷 |
| --- | --- |
| `cc4c-v3_mysql_data` | `cc4c_mysql_data` |
| `cc4c-v3_redis_security_data` | `cc4c_redis_security_data` |
| `cc4c-v3_redis_cache_data` | `cc4c_redis_cache_data` |
| `cc4c-v3_rabbitmq_data` | `cc4c_rabbitmq_data` |
| `cc4c-v3_prometheus_data` | `cc4c_prometheus_data` |
| `cc4c-v3_grafana_data` | `cc4c_grafana_data` |
| `cc4c-v3_blog_uploads` | `cc4c_blog_uploads` |
| `cc4c-v3_avatar_uploads` | `cc4c_avatar_uploads` |

缺失的旧卷标记为“未创建”，不为迁移目的创建空卷。Redis Session、业务缓存和 RabbitMQ 的现有 `v3` namespace、队列、Outbox/Inbox 和消息事件协议均保持原值。

## 维护窗口与排空门禁

迁移前必须由负责人明确批准维护窗口，并完成以下顺序：

1. 只读记录 `cc4c-v3` 的容器、实际卷名、卷 label/driver/scope、挂载关系、镜像摘要、数据库元数据和消息积压摘要；不得读取或导出秘密、消息载荷或数据库内容。
2. 完成数据库、上传文件和消息系统备份，并对备份执行 SHA-256 校验；备份文件不得进入仓库。
3. 冻结外部写入和用户流量，精确停用本次迁移范围内的 dispatcher、consumer 和其他写入 Outbox/Inbox/RabbitMQ 的进程。
4. 等待 Outbox 达到零个未处理发布项、RabbitMQ 无未确认消息且约定的 DLQ/重试积压门禁通过；不得 purge 队列、删除 vhost、清空 Redis 或执行 Flyway `clean`/`repair`。
5. 精确停止旧 `cc4c-v3` 服务并确认没有容器、卷挂载或其他写入者。不得使用 `docker compose down -v`，不得触碰 `cc4c-a7verify2` 或其他 Compose 项目。

## 受控执行接口

迁移工具只处理上表中的八个精确卷名，manifest 必须保存在工作区外，并且只包含路径、label、driver、scope、文件数量、字节数和摘要校验：

```powershell
`.\\scripts\\deployment\\migrate-compose-identity.ps1 -Mode DryRun
.\\scripts\\deployment\\migrate-compose-identity.ps1 -Mode Copy -ManifestPath <external-manifest>
.\\scripts\\deployment\\migrate-compose-identity.ps1 -Mode Verify -ManifestPath <external-manifest>
```

`DryRun` 只查询并生成映射，不创建、复制、挂载或删除卷。`Copy` 只在新卷不存在时创建，并使用固定 digest 的一次性辅助镜像复制不透明卷内容；源卷缺失时跳过，不创建空目标。`Verify` 检查摘要、label、目标挂载关系和 Compose 项目归属。任何失败都停止后续动作，并只回收本次创建且明确记录的目标卷。

`Copy` 前必须在工作区外的 manifest 中由负责人根据实际证据将 `maintenanceWindowConfirmed`、`writeFreezeConfirmed`、`backupSha256Verified`、`outboxDrained`、`rabbitDrained` 和 `externalWritersStopped` 逐项设为 `true`；脚本不会替用户猜测维护窗口、备份或消息排空状态。源卷删除还需要额外将 `targetAcceptanceConfirmed` 设为 `true`。

只有在新项目健康、契约 smoke、业务 smoke、重启持久化和用户验收全部通过后，才可单独执行源卷删除。该命令必须使用固定确认值，且每个源卷逐项处理：

```powershell
.\\scripts\\deployment\\migrate-compose-identity.ps1 `
  -Mode DeleteSource `
  -ManifestPath <external-manifest> `
  -ConfirmSourceDeletion DELETE-CC4C-V3-SOURCE-VOLUMES
```

工具不执行 `docker volume prune`，不使用通配符，不按 label 批量删除。源卷删除中途失败时保留已删除/未删除状态，立即停止，不自动继续。

## 切换、健康检查与验收

源卷仍保留期间，使用默认项目启动：

```powershell
docker compose -p cc4c up --build -d --wait
```

依次检查 MySQL、两个 Redis、RabbitMQ、Mailpit、后端、前端、Prometheus 和 Grafana 的健康状态，执行 OpenAPI 快照检查、管理员引导、登录/注册、课程、博客、收藏、评论和消息管理 smoke，并在普通重启后确认数据库和上传文件仍可读。CI 使用 `cc4c-ci`，性能使用 `cc4c-perf`；它们不得连接 `cc4c`、`cc4c-v3` 或 `cc4c-a7verify2` 的卷。

验收记录至少包含：DryRun 精确清单、源/目标摘要和 label、项目/卷归属、健康与契约结果、业务 smoke、重启持久化结果、删除前后卷清单及磁盘变化。用户未完成验收时，旧卷和旧项目回滚入口必须保留。

## 失败回滚

若静态检查、复制、启动、健康、契约或业务 smoke 失败，立即停止新项目，不删除源卷，不清空 Redis，不 purge RabbitMQ，不执行 Flyway `clean`/`repair`。源卷仍存在时：

```powershell
docker compose -p cc4c down
docker compose -p cc4c-v3 up -d --wait
```

若源卷已经按明确授权删除，必须先停止新项目，再使用同一外部 manifest 检查目标卷完整性并反向恢复缺失的旧卷：

```powershell
.\\scripts\\deployment\\migrate-compose-identity.ps1 `
  -Mode RestoreLegacy `
  -ManifestPath <external-manifest>
docker compose -p cc4c-v3 up -d --wait
```

`RestoreLegacy` 只在目标项目停止、旧目标卷不存在且新卷摘要完整时创建反向卷，不覆盖已有数据。回滚后重新检查 Compose 身份、卷 label、健康状态、Outbox/RabbitMQ 积压和 Git 状态；只停止新服务、恢复旧配置/namespace 并重新启用之前精确停用的消费者。

本说明不授权卷删除、数据库写入、服务启停、秘密读取、Git 操作或发布操作；每项都必须由负责人单独确认。

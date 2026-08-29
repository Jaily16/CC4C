# CC4C 数据库说明

## 结构来源

`back-end/CC4C/src/main/resources/db/migration` 中的 Flyway 迁移是数据库结构和公开目录基线的唯一来源：

| 迁移 | 内容 |
| --- | --- |
| `V1__create_cc4c_schema.sql` | 创建现有 16 张表，不含 `DROP`、锁表语句或默认账号 |
| `V2__seed_catalog_reference_data.sql` | 幂等写入 4 种语言、61 门课程、9 个课程模块和 61 条模块关系 |
| `V3__harden_relations_and_add_query_indexes.sql` | 统一文本字符集，强化评论归属与父回复完整性，增加博客及回复查询索引 |
| `V4__expand_password_columns.sql` | 将用户与管理员密码列扩展到 255 字符，为 `{bcrypt}` 格式保留空间；不读取或转换明文 |
| `V5__add_interaction_query_indexes.sql` | 为课程收藏和博客收藏分页增加按用户、收藏时间及资源 ID 排序的复合索引 |
| `V6__add_async_outbox_and_inbox.sql` | 增加加密消息 Outbox/Inbox、租约、尝试次数、generation、受控错误码及发布/消费扫描索引 |
| `V7__add_outbox_correlation_id.sql` | 为 Outbox 增加可空 ASCII 请求关联 ID，使 HTTP、发布、重试和消费者日志可关联，同时兼容 V6 历史积压 |

`database/legacy/cc4c.sql` 仅供历史对照，已移除默认管理员，不得用于初始化新环境。应用配置中的 `baseline-on-migrate` 默认并持续保持 `false`。

## 新建空数据库

先由数据库管理员创建使用 `utf8mb4_0900_ai_ci` 的空库，并为应用账号授予业务读写及 Flyway 所需的 `CREATE`、`ALTER`、`INDEX`、`REFERENCES` 权限。随后复制 `.env.runtime.example` 为已忽略的 `.env.runtime.local`，由 `run-local.ps1` 显式加载脱敏配置；Flyway 会按 V1–V7 初始化 18 张表并校验迁移。空库没有历史账号，不需要执行密码转换。

不要将数据库密码、SMTP 授权码或本机路径写入仓库配置、本文档或日志。生产环境不应为方便迁移而使用数据库管理员账号运行应用。

## 已有非空数据库

已有数据的数据库不得直接开启自动基线。迁移前必须：

1. 使用 `mysqldump --single-transaction --skip-lock-tables` 备份，并保存 SHA-256。
2. 核对 16 张表、主外键、重复评论归属及父评论孤儿数据；发现异常立即停止。
3. 确认当前结构与 V1 一致后，显式在版本 1 建立基线，再应用 V2/V3/V4/V5/V6/V7；完成后应有 16 张业务表和 2 张异步可靠性表。
4. 保持后端停止，使用备份文件、SHA-256 和精确数据库名称运行 `migrate-passwords.ps1`，将所有非 `{bcrypt}` 密码离线转换；工具不得输出账号、明文、哈希或数据库凭据。
5. 再次执行密码迁移必须转换 0 行，并确认明文或未知 `{id}` 格式剩余数为 0。
6. 第二次 Flyway `migrate` 必须为零新增迁移，随后执行 `validate` 和结构断言，最后才允许启动 Web 应用。

离线密码迁移命令要求数据库连接以进程变量提供，且必须与备份确认信息指向同一精确数据库：

```powershell
cd back-end/CC4C
./migrate-passwords.ps1 `
  -BackupPath <verified-backup.sql> `
  -BackupSha256 <64-hex-sha256> `
  -ConfirmDatabase <exact-database-name>
```

普通 Web 启动会检查全部用户和管理员密码。只要仍存在明文或未知 `{id}` 格式，就会拒绝启动；应用不会在登录时懒迁移，也不能把迁移后的数据库直接交给方面二旧代码。

迁移失败时不得直接执行 `repair`，也不得在原库上反复试错。应停止应用，将已验证备份恢复到一个新数据库，比较结构与数据后切换连接。Flyway Community 不提供伪造的 down migration，本项目也不维护破坏性的回滚脚本。

## 异步 Outbox 与 Inbox

V6 增加的 `async_outbox` 是消息管理页面和人工恢复的事实来源，`async_inbox` 用于按 `consumer_name + event_id + generation` 去重。业务事务与 Outbox 行同提交、同回滚；不得使用独立新事务绕过业务回滚。`PUBLISHED` 仅表示 RabbitMQ Publisher Confirm 成功，只有消费者完成外部处理并写入 Inbox `DONE` 后才进入 `DELIVERED`。

载荷以 AES-256-GCM 保存，明文列只保留事件 ID、版本、类型、聚合类型/ID、generation、时间和密钥 ID。邮箱、验证码和邮件正文不得出现在表的摘要字段、受控错误码或运维查询结果中。活动写入密钥与只读旧密钥通过本机环境配置轮换；在旧 Outbox、Inbox 和 DLQ 超过保留期前不得移除旧密钥。

`DELIVERED`、`EXPIRED`、`IGNORED` Outbox 与 `DONE` Inbox 保留 31 天后分批清理，每批不超过 500 条；`PUBLISH_FAILED` 和 `DEAD` 不自动删除。V6 只增加表和索引，不提供伪造 down migration。回滚旧代码时必须保留两张表和未完成记录，待方面五代码恢复后继续处理。

## V7 请求关联兼容性

V7 只向 `async_outbox` 增加可空的 `correlation_id VARCHAR(64)`，不修改 V6 的密文、nonce、AAD、事件版本或索引语义。新 HTTP 事务写入校验后的 `X-Request-ID`，非 HTTP 事件使用 eventId；Publisher 将同一值传入受控 AMQP Header，重试和 DLQ 保持原关联 ID。升级前已存在且该列为空的消息由消费者回退到 eventId，因此无需重写或解密历史积压。

该字段仅用于日志关联，不能用于身份、幂等或授权，也不得写入 Cookie、邮箱、验证码、SQL 或连接信息。回滚到 V6 代码时旧版本会忽略这个可空附加列；不得为回滚删除列或执行 Flyway `repair`。

## Testcontainers 集成测试数据库

方面七起，标准测试不再依赖本机测试数据库或 `.env.test.local`。`run-tests.ps1` 只校验 Java 21 和 Docker Engine，然后由 Testcontainers 1.21.4 在本轮测试 JVM 内启动 MySQL 8.4.11、两个 Redis 7.4.10 和 RabbitMQ 4.3.5。

- MySQL 容器的主库从空状态应用 Flyway V1–V7，并承载业务功能回归。
- 迁移测试在同一隔离容器中创建随机临时库，分别验证空库初始化、模拟 V1 已有库升级、第二次 migrate 零新增和 validate。
- 容器数据库名和凭据由测试基础设施动态注入，不从系统环境、本机 `.env` 或开发配置回退。
- Testcontainers reuse 明确禁用，Ryuk 只清理本轮容器与网络；测试不会接触本机 MySQL、Redis、RabbitMQ 或任何 Compose 持久卷。
- 任何 schema 删除只允许发生在已校验的容器临时库中，仍禁止 Flyway `clean/repair` 和数据库级无范围破坏操作。

执行方式：

```powershell
cd back-end/CC4C
./run-tests.ps1 clean verify
```

历史本机测试库授权、备份和恢复脚本仅保留为迁移追溯资料，不再是标准测试入口。

## Compose 数据卷与备份

`compose.yml` 为 MySQL 使用项目级命名卷 `mysql_data`。普通 `docker compose -p cc4c-v3 down` 只停止并移除容器与网络，不删除数据库卷；禁止随手添加 `-v`。显式重置只能通过 `deploy/scripts/reset-local.ps1 -ConfirmProjectName cc4c-v3`，脚本还要求再次输入精确确认文本。

升级镜像或进行破坏性维护前，必须从运行中的 MySQL 服务执行单事务备份并保存 SHA-256，同时备份博客和头像上传卷。回滚只允许切换到已记录的旧镜像 digest，并在新恢复库中验证备份；不得在原库执行 Flyway `clean/repair`、伪造 down migration 或手工删除 V1–V7 历史。

## 独立性能数据库

方面四基准只接受数据库名精确以 `_perf_test` 结尾的 JDBC URL，并要求 `CC4C_PERF_CONFIRM_DATABASE` 与 URL 中的实际数据库名完全一致。复制 `.env.performance.example` 为已忽略的 `.env.performance.local`，填写性能库账号、独立缓存 Redis 和确认值后运行 `back-end/CC4C/run-aspect4-benchmark.ps1`。

性能准备工具使用固定种子 `20260827`，默认生成 2,000 用户、1,000 课程、20,000 博客以及合计 200,000 条收藏、评论与回复关系。它只删除工具保留的有限 ID 区间，禁止 Flyway `clean`/`repair`、`DROP DATABASE`、无范围删除以及对功能测试库执行。基准和 `EXPLAIN FORMAT=JSON` 结果只写入已忽略的 `temp/`；这些数字是同一提交、数据、Redis 和硬件上的本地对照，不代表生产容量。

## 失败后的恢复库

`database/test-database-recovery-setup.sql` 和 `back-end/CC4C/restore-flyway-test-backup.ps1` 只用于已经发生迁移失败、且用户明确授权的恢复流程。脚本将目标固定为 `cc4c_recovery_test`，并拒绝以下情况：

- `.env.test.local` 的主 URL 不再指向原 `cc4c_test`；
- 备份不位于已忽略的 `temp/` 或文件名不符合方面二备份格式；
- 备份包含选库、数据库级破坏语句或 `flyway_schema_history`；
- 恢复库授权、结构检查或导入失败。

授权并建立恢复库后，命令格式为：

```powershell
cd back-end/CC4C
./restore-flyway-test-backup.ps1 -BackupPath <verified-backup.sql> -MySqlBin <mysql-bin>
```

恢复成功后仍需将测试 URL 显式切换到恢复库，再执行迁移、重复迁移、`validate`、结构断言和完整功能测试；未经比对不得替换原库。

## 产物与安全

数据库备份、SHA-256、EXPLAIN 原始结果和日志只允许写入已忽略的 `temp/`。这些文件可能包含结构或数据线索，不得暂存、提交或上传。RabbitMQ definitions、消息密文和 DLQ 导出同样不得进入仓库。提交前必须确认本机 `application.yml`、`.env.runtime.local`、`.env.test.local`、`.env.performance.local`、`target/`、`temp/` 和日志均未进入 Git。

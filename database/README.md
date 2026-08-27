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

`database/legacy/cc4c.sql` 仅供历史对照，已移除默认管理员，不得用于初始化新环境。应用配置中的 `baseline-on-migrate` 默认并持续保持 `false`。

## 新建空数据库

先由数据库管理员创建使用 `utf8mb4_0900_ai_ci` 的空库，并为应用账号授予业务读写及 Flyway 所需的 `CREATE`、`ALTER`、`INDEX`、`REFERENCES` 权限。随后复制 `.env.runtime.example` 为已忽略的 `.env.runtime.local`，由 `run-local.ps1` 显式加载脱敏配置；Flyway 会按 V1–V5 初始化并校验迁移。空库没有历史账号，不需要执行密码转换。

不要将数据库密码、SMTP 授权码或本机路径写入仓库配置、本文档或日志。生产环境不应为方便迁移而使用数据库管理员账号运行应用。

## 已有非空数据库

已有数据的数据库不得直接开启自动基线。迁移前必须：

1. 使用 `mysqldump --single-transaction --skip-lock-tables` 备份，并保存 SHA-256。
2. 核对 16 张表、主外键、重复评论归属及父评论孤儿数据；发现异常立即停止。
3. 确认当前结构与 V1 一致后，显式在版本 1 建立基线，再应用 V2/V3/V4/V5。
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

## 本地集成测试数据库

测试使用两个不同的专用数据库：

- 主测试库：名称必须以 `_test` 结尾，但不能以 `_flyway_test` 结尾，用于 V2 功能回归和现有库基线测试。
- 空迁移库：名称必须以 `_flyway_test` 结尾，只用于从 V1 重建、重复迁移和 `validate`。

准备流程：

1. 由数据库管理员确认主测试库和测试账号已存在，再审阅并执行 `database/test-database-admin-setup.sql`，为主库补充迁移权限并创建空迁移库。
2. 在 `back-end/CC4C` 复制 `.env.test.example` 为已忽略的 `.env.test.local`，填写 `CC4C_TEST_DB_URL`、`CC4C_TEST_EMPTY_DB_URL`、`CC4C_TEST_DB_USERNAME`、`CC4C_TEST_DB_PASSWORD`、`CC4C_TEST_REDIS_URL` 和 `CC4C_TEST_CACHE_REDIS_URL`。
3. 首次迁移已有测试库前执行 `./prepare-flyway-tests.ps1 -MySqlBin <mysql-bin>`，完成库名检查、数据预检、备份和 SHA-256 记录。
4. 如需保留索引治理证据，在迁移前后分别执行 `./capture-query-plans.ps1 -Phase Before -MySqlBin <mysql-bin>` 和 `./capture-query-plans.ps1 -Phase After -MySqlBin <mysql-bin>`。
5. 使用 Java 21 运行 `./run-tests.ps1 clean verify`。脚本会先单独执行两个迁移门禁，成功后才进入完整 Maven 测试。

`.env.test.local` 缺失、六个变量任一为空、URL 没有显式数据库名、库名后缀不合法或两个数据库 URL 相同时，脚本会快速失败，不会读取开发库配置或回退到其他变量。每次测试分别生成独立的 Session 与业务缓存 namespace，只允许删除本次 namespace 下的键，禁止 `FLUSHDB` 和 `FLUSHALL`。

只有名称精确满足 `_flyway_test` 约束的空迁移库允许由迁移测试清理。主 `*_test` 库、恢复库和其他数据库永远不会被测试自动清理。

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

数据库备份、SHA-256、EXPLAIN 原始结果和日志只允许写入已忽略的 `temp/`。这些文件可能包含结构或数据线索，不得暂存、提交或上传。提交前必须确认本机 `application.yml`、`.env.runtime.local`、`.env.test.local`、`.env.performance.local`、`target/`、`temp/` 和日志均未进入 Git。

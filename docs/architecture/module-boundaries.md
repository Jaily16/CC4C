# CC4C 技术分层与业务边界

V6 后端采用全局技术分层，`com.cc4c` 下固定十个一级包。业务名称由类名表达；
原 Spring Modulith 业务包及 `api/internal/shared` 结构已迁移。当前验收进度见
[V6 规划](../v6-iteration-plan.md)，目录设计见[三端目标目录](project-directory-design.md)。

| 技术包 | 职责与调用边界 |
| --- | --- |
| `controller` | HTTP 参数、校验、响应和用例调用，不直接访问数据库 |
| `service` | 业务规则、事务、已有 Lookup/UseCase 和独立转换辅助 |
| `mapper` | 五个 MyBatis-Plus 接口承担 DAO 职责，不增加包装层 |
| `repository` | Inbox/Outbox JDBC 数据访问，与 Mapper 同层 |
| `entity` | 持久化实体和 Outbox 表记录，不作为公开 HTTP 响应 |
| `dto` | 请求响应、快照、查询 Row 和分页结构，保留协议字段 |
| `config` | 组件、属性及安全链装配，真实配置外置 |
| `common` | 公共错误、校验、异常处理和无状态工具 |
| `security` | 身份、认证、Session、CSRF、限流和安全检查 |
| `support` | 文件、邮件及 cache、messaging、monitoring 三个技术子包 |

Controller 调用现有服务；服务使用 Mapper/Repository 和必要的技术支撑。消息消费者仍通过既有
服务接口执行业务，Lookup/UseCase 保留在 service，不机械增加接口或 Impl。
现有上传适配器及消息运维编排沿用原调用方式，不在本次目录迁移中扩大重构。

主应用仍位于 `com.cc4c.CC4CApplication`；密码迁移、管理员引导及观测密码工具保留独立
`com.cc4ctools` 根包，不纳入主应用组件扫描。管理上下文通过 META-INF 导入 config 中的专用配置。

## 观测边界

观测实现按同一技术层组织，但身份边界不变：不读取业务用户或管理员 Session，不访问业务身份表，
也不新增数据库表。浏览器只能调用 `/observability/**` 固定接口；Prometheus 地址、认证和 PromQL 留在后端。

管理端口的 Basic Auth 仅供 Prometheus 抓取，与观测门户账户是两套密码和两种认证协议。门户权限固定为
`OBSERVABILITY`，不能转换为 `USER` 或 `ADMIN`，业务安全链也不接受观测 Cookie。

## 兼容资产

Flyway V1–V7、业务 DTO、公开 HTTP 路径、`CC4C_SESSION`、业务 CSRF 和三个 `*.v1` 消息协议保持不变。
业务 Session 使用专用 JSON 序列化器，精确兼容 V5 Principal 和认证 Token 的旧类名；新写入采用新类名。
这只保证 V6 读取 V5 会话，不承诺旧应用读取 V6 新会话，也不清理 Redis。

MyBatis 指标按五个 Mapper 的完整名称映射到既有 identity、catalog、community、interaction 标签，
未知 Mapper 保留 shared 兜底；技术包名称不成为新的业务指标标签。
观测契约和固定指标映射详见[独立观测架构](observability.md)。

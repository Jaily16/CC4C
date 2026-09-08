# CC4C 模块边界

CC4C 后端是 Spring Modulith 单体。模块按业务能力划分，模块根包可以依赖自身内部包和明确允许的
API；数据库实体、Mapper、控制器实现及安全实现不得跨模块直接调用。

| 模块 | 职责 | 允许依赖 |
| --- | --- | --- |
| `shared` | 通用响应、配置、持久化、缓存、邮件、消息可靠性、指标和请求关联 | 无 |
| `identity` | 用户与管理员身份、业务 Session、验证码和账户资料 | `shared` |
| `catalog` | 课程目录、模块及课程查询 | `shared` |
| `community` | 博客、草稿、审核状态及社区查询 | `shared`、公开模块 API |
| `interaction` | 收藏和评论 | `shared`、公开模块 API |
| `moderation` | 管理员审核及异步消息运维 | `shared`、公开模块 API |
| `observability` | 独立观测身份、Redis Session、固定 Prometheus 查询、告警和依赖状态 | `shared` |

## 观测边界

观测模块不读取业务用户或管理员 Session，不访问业务身份表，也不新增数据库表。浏览器只能调用
`/observability/**` 固定接口；Prometheus 地址、认证和 PromQL 都留在后端。模块只通过 `shared` 中的安全
哈希、指标、健康贡献者和请求关联能力与运行基础设施协作。

管理端口的 Basic Auth 仅供 Prometheus 抓取，与观测门户账户是两套密码和两种认证协议。门户权限固定为
`OBSERVABILITY`，不能转换为 `USER` 或 `ADMIN`，业务安全链也不接受观测 Cookie。

## 兼容资产

Flyway V1–V7、业务 DTO、公开 HTTP 路径、`CC4C_SESSION`、业务 CSRF 和三个 `*.v1` 消息协议是稳定兼容
资产。模块调整不得改写这些协议；需要跨模块协作时，应先在所属模块的 `api` 包定义最小只读接口或事件。

观测契约和固定指标映射详见 [独立观测架构](observability.md)。

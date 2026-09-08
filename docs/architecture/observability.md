# CC4C 独立观测架构

## 安全边界

中文观测后台由独立 Vue 3 应用和后端按 controller、service、security 和 support/monitoring 分层的观测实现组成。它使用唯一 `OBSERVABILITY` 账户、
`CC4C_OBSERVABILITY_SESSION` HttpOnly Cookie、专用 CSRF Cookie 和独立 Redis namespace；不复用业务
`USER`、`ADMIN`、`CC4C_SESSION` 或业务 CSRF。门户 Session 空闲 30 分钟失效，创建 8 小时后绝对失效。

浏览器只持有 Cookie 和内存中的 CSRF Token，不使用 localStorage 或 sessionStorage。Redis 键只保存随机
Session Token 的 HMAC-SHA256，值只包含用户名、创建时间和绝对期限。登录按账户和 IP 分别限流，错误账户
仍执行 BCrypt 校验。状态变更请求同时校验专用 CSRF 头和精确 Origin。

管理端口的 Basic Auth 使用独立 BCrypt 哈希，供外部 Prometheus 抓取；门户密码必须不同。Prometheus URL、
可选 Basic 凭据和所有 PromQL 仅存在于后端配置及固定 catalog，不进入 `observability/.env.local` 或浏览器
产物。

## 数据流

1. 浏览器从 `/observability/auth/csrf` 获取内存 CSRF Token，再登录独立门户。
2. 后端验证 BCrypt、限流、Origin 和 CSRF，向独立 Redis namespace 写入哈希 Session。
3. 已认证请求只能选择三个 Dashboard ID 和四种时间范围；浏览器不能提交 PromQL。
4. 后端从只读 catalog 选择固定表达式，以 4 个并发、32 个等待任务和明确超时访问 Prometheus。
5. 后端限制响应体、序列、标签和点数，将故障归一化为 `AVAILABLE`、`PARTIAL`、`EMPTY` 或
   `UNAVAILABLE`，不返回原始异常或上游响应。
6. 依赖页直接读取 Spring 健康贡献者及 ApplicationAvailability，不通过管理端口自调用。

## 二十个面板和三十九条查询

| 页面 | 面板 | 查询数 |
| --- | --- | ---: |
| API 与 JVM | HTTP 请求速率 | 1 |
| API 与 JVM | HTTP P50 / P95 / P99 | 3 |
| API 与 JVM | JVM 堆内存 | 2 |
| API 与 JVM | CPU 与 GC | 2 |
| API 与 JVM | HTTP 错误状态速率 | 1 |
| API 与 JVM | JVM 与 Tomcat 线程 | 3 |
| 数据库、缓存与安全 | MyBatis 各模块 P95 | 1 |
| 数据库、缓存与安全 | Hikari 连接池 | 3 |
| 数据库、缓存与安全 | 缓存结果速率 | 1 |
| 数据库、缓存与安全 | 认证与限流事件 | 2 |
| 数据库、缓存与安全 | MyBatis 操作与错误 | 1 |
| 数据库、缓存与安全 | 缓存命中率与 Redis 错误 | 2 |
| 数据库、缓存与安全 | 授权拒绝 | 1 |
| 异步消息 | Outbox 与 Inbox 状态 | 2 |
| 异步消息 | 最老待发与采样器年龄 | 2 |
| 异步消息 | 发布与消费 P95 | 2 |
| 异步消息 | RabbitMQ 队列 | 3 |
| 异步消息 | 重试、死亡、重复与过期 | 4 |
| 异步消息 | 发布与消费结果 | 2 |
| 异步消息 | RabbitMQ 死信队列 | 1 |

这些查询完整承接 V4 的三个 Grafana Dashboard。ECharts 只负责显示后端返回的有界序列，单 Dashboard
最多 16 条查询、单面板最多 4 条、单查询最多 50 个序列、单序列最多 300 个点。

## 二十条告警

告警页固定展示规则文件中的：`Cc4cBackendUnreachable`、`Cc4cHttp5xxRateHigh`、`Cc4cApiP95High`、
`Cc4cHikariPending`、`Cc4cHikariUtilizationHigh`、`Cc4cMybatisP95High`、
`Cc4cMybatisErrorRateHigh`、`Cc4cCacheHitRatioLow`、`Cc4cCacheFallbackIncreasing`、
`Cc4cAuthenticationFailuresHigh`、`Cc4cRateLimitRejectionsHigh`、`Cc4cOutboxBacklogOld`、
`Cc4cOutboxFailed`、`Cc4cMessagingSamplerStale`、`Cc4cRabbitBacklog`、`Cc4cRabbitNoConsumers`、
`Cc4cRabbitDeadLetters`、`Cc4cJvmHeapHigh`、`Cc4cProcessCpuHigh` 和 `Cc4cGcPauseP99High`。

运行状态来自 Prometheus `/api/v1/rules?type=alert`，中文标题、含义及级别来自固定 catalog。缺失规则会以
`MISSING` 显示，原始 PromQL 和 `lastError` 不返回浏览器。

## 故障语义

Prometheus 不可达、响应超限、格式错误或查询部分失败时，数据接口仍返回可渲染的脱敏状态。身份、CSRF、
输入和限流失败分别使用 401、403、400/404 和 429。前端为每张图保留文本最新值、空数据和失败替代，页面
隐藏或请求未结束时不发起重叠轮询。

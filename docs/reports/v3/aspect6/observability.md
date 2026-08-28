# 方面六观测、健康与告警证据

## 管理面与请求关联

- 业务端口保持 4080；Actuator 独立绑定 `127.0.0.1:4081`。
- `health`、`liveness` 和 `readiness` 匿名只返回脱敏状态；`dependencies`、`info` 和 `prometheus` 要求独立无状态 Basic 身份。
- USER/ADMIN Session 不能替代观测身份；管理链只接受 GET/HEAD，不创建 `CC4C_SESSION`，且未暴露 `env`、`configprops`、`heapdump`、`loggers`、`shutdown` 等端点。
- 合法 `X-Request-ID` 会被保留，缺失或非法值由服务端生成；成功与 4xx/5xx/Security 响应均返回该 Header。
- 同一关联 ID 贯穿 Outbox 行、AMQP Header、重试、DLQ 和消费者 MDC。V6 历史消息缺失关联值时回退到 eventId。

## 指标与健康

Micrometer 指标覆盖 HTTP/JVM/GC/Tomcat/Hikari、MyBatis、缓存、安全认证/拒绝/限流、消息发布/消费/重试/DEAD/重复/过期，以及 Outbox/Inbox 状态和最老积压。动态 ID、邮箱、IP、SQL、异常正文和原始 URI均禁止作为标签；HTTP `uri` 使用路由模板并设置 100 个标签值上限。

Outbox/Inbox 每 15 秒执行固定聚合查询并写入内存快照，Prometheus scrape 只读取 Gauge，不访问数据库。`liveness` 不探测依赖；`readiness` 包含数据库和安全 Redis；业务缓存 Redis、RabbitMQ 和异步积压只影响受保护的 `dependencies`，不错误阻断可回源或可积压的业务能力。

## Prometheus 与 Grafana

使用 Prometheus 3.13.2 的 `promtool` 得到：

- `prometheus.yml.template` 配置校验成功；
- `cc4c-alerts.yml` 共 20 条规则，语法校验成功；
- `cc4c-alerts.test.yml` 规则单元测试成功。

告警覆盖后端不可抓取、5xx/p95、Hikari 等待与利用率、MyBatis 时延/错误、缓存命中/旁路、认证/限流、Outbox 积压/失败、采样过期、Rabbit 积压/无消费者/DLQ、JVM 内存/CPU/GC。方面六不引入 Alertmanager 或外发通知。

Grafana 13.1.0 Provisioning 提供三个固定 UID Dashboard：

1. `cc4c-api-jvm`：API 吞吐、错误、分位数、JVM、GC、CPU、线程、Tomcat 和 Hikari。
2. `cc4c-db-cache-security`：MyBatis、连接池、缓存命中/回源/旁路、Redis 错误、认证失败、授权拒绝和限流。
3. `cc4c-messaging`：Outbox/Inbox、最老积压、发布/消费时延、重试/DEAD 与 Rabbit ready/unacked/consumer/DLQ。

用户浏览器验收确认三个 Dashboard 均正常，业务请求关联、Swagger 契约和管理指标认证均符合设计。

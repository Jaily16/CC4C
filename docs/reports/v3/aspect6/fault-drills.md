# 方面六故障演练证据

## 安全边界

每项演练前均精确识别 CC4C 使用的端口、PID、容器或 Windows 服务；恢复逻辑使用 `try/finally`，不按进程名批量终止，不执行 Redis `FLUSHDB/FLUSHALL`、Rabbit purge/vhost 删除、Flyway `clean/repair`，也不停止无关 Java、MySQL、Redis 或 RabbitMQ 进程。

本机业务缓存与安全 Redis 使用同一精确 Redis 实例，因此按计划跳过“仅停止业务缓存 Redis”的独立演练，改为一次共享实例中断同时验证公开回源与安全失败。生产仍要求两者使用独立实例。

## 结果

| 场景 | 故障行为 | 恢复证据 |
| --- | --- | --- |
| 共享 Redis 中断 | 公开课程查询回源 MySQL 并返回 200；readiness 返回 503；无效登录返回统一 JSON HTTP 503、业务码 50300，未泄露 Lettuce/Redis 细节 | 精确容器恢复后 readiness 200；无效登录恢复正常 401 语义 |
| MySQL 应用连接中断 | 为避免影响其他本机客户端，只将 CC4C 临时连接到 `127.0.0.1:13307` 透明代理并切断代理；readiness 503，未缓存查询在 3,149 ms 返回脱敏 JSON HTTP 500、业务码 50000 和原请求 ID | 代理恢复后 readiness 200；`finally` 停止代理并恢复普通 3306 直连后端 |
| RabbitMQ 中断 | 验证码、博客提交和审核仍由 MySQL Outbox 可靠受理；readiness 不因可恢复消息依赖错误失败，积压与发布失败指标可见 | Broker 恢复后 Dispatcher 自动发布，Inbox 保持幂等，无重复业务状态 |
| 消费者暂停 | Publisher 保持运行，博客通知在 quorum 主队列积压，消费者和 ready 指标反映故障 | 恢复指定 Listener 后积压自动消费并进入 DELIVERED |
| 安全 Redis | 与共享 Redis 场景一并验证；登录和私有身份操作安全失败，不降级为内存 Session | 恢复后重新登录和会话流程正常 |
| SMTP 永久失败 | 审核事务正常完成，通知按有限重试进入 DEAD，管理页面只显示安全摘要与错误枚举 | 恢复邮件配置后管理员增加 generation 重试，同一业务通知成功送达 |

## 修复后的回归点

首次演练发现两个行为缺口并在当前工作区修复：

1. Redis/Lettuce 连接异常被外层运行时异常包装时曾落入 500。现使用有深度和循环保护的 cause-chain 分类器，Servlet 外层过滤器与全局异常处理器统一返回 503；非 Redis 异常仍保持原 500 路径。
2. Hikari 默认 30 秒获取连接使 MySQL 故障请求超出验收时间。脱敏配置现在默认 `connectionTimeout=3000 ms`、`validationTimeout=1000 ms`，启动脚本校验范围和大小关系；健康数据库路径不受影响。

修复后新增/扩展测试并完成 150 项后端全量回归；当前构建缓存基准和 Gatling smoke 继续通过。

演练结束时 13307 和临时 SMTP 2526 均未监听，普通前端、后端、Prometheus 和 Grafana 已恢复。Rabbit 最终 DLQ 的保留消息按可靠性设计继续保留，未执行 purge。

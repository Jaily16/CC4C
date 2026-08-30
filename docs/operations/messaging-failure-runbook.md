# CC4C 异步消息故障手册

## 1. 适用范围与安全边界

本文适用于 V3 方面五的验证码邮件、博客待审核通知和博客审核结果通知。MySQL `async_outbox` 是可靠受理与人工恢复的事实来源，RabbitMQ 负责至少一次投递，`async_inbox` 负责同一消费者、eventId 和 generation 的幂等处理。

任何操作前先确认精确环境、vhost、Rabbit namespace 和数据库名称。本文不授权以下操作：

- RabbitMQ `purge`、删除生产队列、删除 vhost 或无前缀清理；
- Flyway `clean`、`repair`、伪造 down migration 或删除 Outbox/Inbox；
- Redis `FLUSHDB`、`FLUSHALL` 或删除 Session、限流和其他 namespace；
- 输出或复制消息密文、邮箱、验证码、Cookie、Session ID、AES 密钥、SMTP 授权码或 RabbitMQ URL；
- 修改、读取或打包本机 `application.yml`。

日志与工单只允许记录 eventId、eventType、generation、状态、尝试次数、受控 errorCode 和时间。不要粘贴请求体、邮件正文、异常正文或连接字符串。

## 2. 数据流与状态含义

```text
业务事务
  └─ 写业务数据 + async_outbox(PENDING)
       └─ Dispatcher 领取租约
            └─ RabbitMQ Publisher Confirm
                 └─ PUBLISHED
                      └─ Consumer + async_inbox 幂等
                           ├─ 成功：DELIVERED + Inbox DONE + ACK
                           ├─ 临时失败：30s → 5m → 30m retry queue
                           └─ 永久失败/耗尽：DEAD + DLQ
```

关键状态：

| 状态 | 含义 | 默认动作 |
| --- | --- | --- |
| `PENDING` | 业务事务已提交，等待发布或退避到期 | 等待 Dispatcher |
| `PUBLISHING` | 某实例持有 30 秒发布租约 | 等待 Confirm；租约过期可接管 |
| `PUBLISHED` | Broker 已确认接管，不代表邮件成功 | 等待消费者 |
| `DELIVERED` | 消费者已完成并写 Inbox DONE | 无需处理 |
| `PUBLISH_FAILED` | 发布有限重试耗尽 | 修复 Broker/拓扑后由管理员重试 |
| `DEAD` | 永久消费错误或三段重试耗尽 | 修复外部依赖后由管理员重试或忽略 |
| `EXPIRED` | 验证码事件超过 10 分钟 | 禁止重试 |
| `IGNORED` | 管理员明确停止恢复 | 不再自动投递 |

`PUBLISHED` 只证明 Publisher Confirm；RabbitMQ Confirm 和消费者 ACK 是两个独立阶段。系统不宣称 SMTP 端到端 exactly-once。

## 3. 正常检查

1. 确认 Web 应用、MySQL、安全 Redis 和 RabbitMQ 均可连接。
2. 在 RabbitMQ 管理页确认当前 namespace 下三个主队列各有预期消费者，且没有持续增长的 `messages_ready`。
3. 管理员访问 `/admin/messaging`，默认只看待发送与失败消息；页面不应展示邮箱、验证码或载荷。
4. 检查日志是否存在相同 eventId 的 `confirmed` 和 `delivered`。不要用日志正文判断邮件内容。
5. 启用 OpenAPI 时确认 `/admin/messaging/messages` 三个接口仅描述安全摘要 DTO。

RabbitMQ 4.3.5 本地验收拓扑为 durable topic exchange、durable quorum 主队列、三段 retry quorum queue 和最终 DLQ。队列参数或 TTL 变化必须使用新版本 namespace，不能原地删除或重建生产队列。

## 4. RabbitMQ 不可用

预期行为：

- `POST /users/email` 仍返回 202；博客提交和审核事务仍成功。
- 新事件保留在 MySQL，状态为 `PENDING` 或短暂 `PUBLISHING`，发布失败按有限退避处理。
- Listener 连接失败日志可以出现，但不得包含 Rabbit URL、凭据或载荷。
- 安全 Redis 的会话、验证码摘要和限流不因 RabbitMQ 故障降级。

恢复步骤：

1. 修复并启动精确的 RabbitMQ 节点，不要新建同名空 vhost 替代原数据。
2. 确认 AMQP 端口和目标 vhost 可用，拓扑没有 `PRECONDITION_FAILED` 或不可路由错误。
3. 保持 Dispatcher 开启，等待现有 `next_attempt_at` 到期；不要为缩短等待直接改数据库时间或尝试次数。
4. 核对同一 eventId 最终出现 `confirmed`，随后由消费者进入 `delivered`。
5. 验证码超过 10 分钟会转为 `EXPIRED`，不得发送或人工重试；应让用户重新申请。

只有得到单独授权后才可在本地验收中暂停精确 RabbitMQ 节点。恢复后必须确认服务状态和端口，不能把 Broker 留在停止状态。

## 5. 暂停发布或消费

两个运行开关只通过本机环境或一次性启动参数使用：

```dotenv
CC4C_OUTBOX_DISPATCHER_ENABLED=false
CC4C_MESSAGE_CONSUMERS_ENABLED=false
```

- 暂停 Dispatcher：业务事件继续写入 MySQL `PENDING`，RabbitMQ 不新增消息。
- 暂停 Consumer：Dispatcher 继续发布并获得 Confirm，主队列 `messages_ready` 增长，Outbox 停留在 `PUBLISHED`。

恢复时将开关改回 `true` 并重启精确 CC4C 后端。Redis Session 会保留浏览器身份。恢复消费者后应观察 `consumers > 0`、`messages_ready` 下降和事件进入 `DELIVERED`。不要通过 purge 队列模拟消费完成。

## 6. SMTP 失败、DEAD 与人工恢复

临时错误包括 SMTP 4xx、连接超时和未知网络错误，按 `30s、5m、30m` 进入对应 retry queue。永久错误包括非法地址、明确 SMTP 5xx、未知事件版本、解密失败和非法载荷，直接进入 `DEAD`；未知异常在有限重试耗尽后使用受控错误码结束。

恢复步骤：

1. 根据 `errorCode` 修复 SMTP、地址、密钥环或事件处理器。不得把异常正文写回数据库。
2. 恢复正常配置并重启后端，先用新的健康事件证明外部依赖可用。
3. 管理员打开 `/admin/messaging`，筛选 `PUBLISH_FAILED` 或 `DEAD`。
4. 核对 eventType、aggregateId、时间和错误码，不查看或导出载荷。
5. 对确需恢复的单条消息点击“重试”并二次确认。重试会把 generation 加一、清除租约和错误状态并回到 `PENDING`。
6. 等待新 generation 完成 `confirmed → delivered`。重复点击已完成消息应返回 409。
7. 若业务明确不应再投递，可点击“忽略”；该动作会记录管理员 actor ID 和时间。`IGNORED` 不得再次重试。

SMTP 可能已接收邮件，但消费者在写 Inbox DONE 前崩溃。此时恢复可能产生内容相同的重复邮件；确定性 `Message-ID` 和 `X-CC4C-Event-Id` 只能帮助识别，不能把外部 SMTP 宣称为 exactly-once。

## 7. 验证码专项处理

- 验证码有效期从 HTTP 202 受理时开始，为 10 分钟，不从 SMTP 实际发送时重新计时。
- Consumer 发信前通过 Redis Lua 原子激活验证码，记录 eventId、issuedAt 和摘要；Redis 不保存邮箱原文或验证码。
- 延迟到达的旧事件不能覆盖更新 eventId 的验证码。
- 最终失败或过期时，只能在 Redis eventId 仍匹配时删除对应记录，不能误删新验证码。
- `EXPIRED` 验证码事件返回 422 且不可恢复；让用户重新申请，不要修改数据库过期时间。

## 8. 消息密钥轮换

1. 生成新的独立 32 字节 AES 密钥，不复用 `CC4C_SECURITY_PEPPER`。
2. 先把新 key ID 和密钥加入 `CC4C_MESSAGING_PAYLOAD_KEYS`，保留全部仍可能被读取的旧密钥。
3. 启动并确认旧消息仍可解密，再把 `CC4C_MESSAGING_ACTIVE_KEY_ID` 切换为新 key ID。
4. 观察新 Outbox 使用新 key ID，旧消息仍能重试和消费。
5. 只有旧 Outbox、Inbox 和 DLQ 全部超过保留期且完成审计后，才能从可读 key ring 移除旧密钥。

未知 key ID 或 GCM AAD 校验失败属于永久错误。不得用 `repair`、改密文或替换 eventType 绕过校验。

## 9. 保留与清理

- `DELIVERED`、`EXPIRED`、`IGNORED` Outbox 保留 31 天后分批清理。
- `DONE` Inbox 保留 31 天，必须长于 RabbitMQ DLQ 的 30 天保留期。
- 每批最多 500 条；`PUBLISH_FAILED`、`DEAD` 和所有待处理状态不自动删除。
- RabbitMQ 测试只清理本轮随机 test namespace 的精确交换机、绑定和队列。生产 namespace 不执行自动删除。

如果管理页积压异常增长，应先暂停新发布、保留数据库和 Broker 证据，再定位根因；不要以删表、purge 或缩短 TTL 作为恢复方式。

## 10. 代码回滚

方面五代码基线为 `bc7dcf8`。回滚前：

1. 设置 Dispatcher 和 Consumer 为关闭并停止精确 CC4C 后端。
2. 等待验证码最长 10 分钟过期，记录所有未完成 eventId 和状态。
3. 保留 V6 的 `async_outbox`、`async_inbox` 和 RabbitMQ durable 队列；旧代码可以忽略附加表，但不能消费未完成消息。
4. 回滚期间不得把未完成 Outbox 标记为 DELIVERED，也不得删除 DLQ。
5. 恢复方面五代码和正确密钥环后，重新开启 Dispatcher/Consumer，让原消息继续处理。

V6 不提供 down migration。不得执行 Git `reset --hard`、`checkout --`、`clean`、`stash` 或 Flyway `clean/repair` 代替受控恢复。

## 11. 关闭故障单前的证据

- 业务接口状态和 `code/data/msg` 契约正常；
- 目标 eventId 的 generation、Confirm、消费和最终状态完整；
- Rabbit 主队列消费者恢复，积压不再增长；
- 管理页面没有暴露敏感载荷；
- 验证码未过期且只能消费一次，或已按规则 EXPIRED；
- 日志没有邮箱、验证码、Cookie、Session ID、密钥或连接凭据；
- 未执行 purge、vhost 删除、数据库破坏性清理或 Flyway repair；
- 本机 `.env.*.local`、日志、Rabbit 导出和 `temp/` 未进入 Git。

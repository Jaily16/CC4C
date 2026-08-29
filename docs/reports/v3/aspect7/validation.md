# 方面七自动验证与故障证据

## 环境

| 项目 | 实际值 |
| --- | --- |
| 基线 | `f0f6fa1` |
| 操作系统 | Windows 11 专业版 10.0.26200 |
| CPU / 内存 | AMD Ryzen 7 9700X；16 逻辑处理器；31.1 GiB |
| Java | Eclipse Temurin 21.0.12.1 |
| Docker / Compose | Engine 28.0.4；Compose 2.34.0 |
| Node / npm | 24.18.0；11.16.0 |
| Testcontainers | 1.21.4 |

## 后端与迁移

- `back-end/CC4C/run-tests.ps1 clean verify`：154 项测试、0 失败、0 错误、0 跳过，共 56 个 Surefire 报告，构建成功。
- 单 JVM Testcontainers 启动 MySQL 8.4.11、两个 Redis 7.4.10 和 RabbitMQ 4.3.5；reuse 禁用，完成后 Ryuk 清理本轮资源，宿主服务未被使用或修改。
- Flyway V1–V7 覆盖空库、模拟 V1 已有库升级、第二次 migrate 零新增和 validate；Spring Modulith 仍恰好识别六个模块且无内部越界。
- JAR 包含一份脱敏 `BOOT-INF/classes/application-example.yml`，不含 `BOOT-INF/classes/application.yml`。

## 前端

- Node/npm 版本与 package engines 一致。
- `npm ci`、四项 Markdown 净化安全测试、完整及生产依赖 audit、Vite 生产构建均通过。
- 完整和生产依赖 High/Critical 漏洞均为 0。
- 六个 Markdown 展示/编辑入口统一调用 sanitize-html，标题 ID 经受控归一化；无源码引用的 editor.md 已移除。

## Compose、镜像与配置

- `docker compose config --quiet` 与外部 SMTP 覆盖配置均通过。
- 九个长期服务全部健康；MySQL、两个 Redis、RabbitMQ、Prometheus、Grafana、博客上传和头像上传共八个卷。
- MySQL/Redis 没有宿主端口，AMQP 和 Rabbit 指标端口没有发布；所有公开端口只绑定 `127.0.0.1`。
- 前后端运行用户分别为 UID 10001 和非 root Nginx；根文件系统只读，tmpfs、capability drop 和 `no-new-privileges` 生效。
- Compose 展开结果未包含 13 个本机 secret 的值；错误项目名的重置脚本拒绝操作，容器和卷数量不变。
- Rabbit 初次初始化后只保留应用与只读监控账号；Rabbit 容器重启后 `guest` 和临时 bootstrap 账号没有重新出现。
- 后端本地镜像 ID：`sha256:0bb96580862614650503e58165a44c08c5585cb6267a8d6dfbb1010f25eeb72d`。
- 前端本地镜像 ID：`sha256:103fc30773dd24efe68e6b09162f920724508279da128aaa762999d0ae121f94`。
- 两个本地镜像的 `RepoDigests` 均为空，符合尚未推送 registry 的事实。

## API 与观测

- OpenAPI 快照与运行接口一致，所有 `$ref` 可解析，密码字段保持 write-only，Swagger UI 无 Resolver 错误。
- Prometheus 配置、20 条告警规则和规则单测通过；三份 Grafana Dashboard JSON 与 Provisioning 静态校验通过。
- readiness、Mailpit、Prometheus、Grafana、前端深层路由和持久上传标记均返回成功。
- 匿名 Prometheus actuator 请求返回 401，独立管理 Basic 身份可访问；业务 Session 不能代替观测身份。

## 故障演练

| 场景 | 结果 |
| --- | --- |
| 业务缓存 Redis 中断 | 公开读取回源 MySQL，缓存依赖降级；恢复后重新建缓存 |
| RabbitMQ 中断 | 业务事务继续写 Outbox；恢复后积压自动发布 |
| 消费者暂停 | quorum queue 积压；恢复后 Inbox 幂等消费 |
| MySQL 中断 | readiness 503，业务错误保持脱敏；恢复后连接池和 readiness 自动恢复 |
| 安全 Redis 中断 | 登录/私有操作安全失败，不退化到内存会话；恢复后重新登录正常 |
| SMTP 永久失败 | 消息进入 DEAD；恢复邮件配置后管理员新 generation 重试并送达 |

所有演练只操作精确 Compose 项目服务并恢复；未 purge Rabbit、未执行 Flyway clean/repair、未删除卷。

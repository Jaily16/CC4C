# 方面七浏览器与持久化验收

2026-08-29，用户在默认 Compose + Mailpit 环境逐项确认：

- Vue 深层路由直接刷新正常。
- Mailpit 注册验证码到达，验证码不在页面或 API 响应中暴露。
- 登录、刷新恢复 Session、CSRF 初始化与收藏写操作正常。
- Mailpit 找回密码成功，旧密码失效，同一验证码不能再次消费。
- 课程/博客收藏、评论、一级回复和二级回复正常。
- 博客草稿保存与恢复、图片上传、提交后清除草稿和待审核邮件正常。
- 一次性管理员账号可登录，审核通过/驳回通知和异步消息管理页正常。
- 匿名/普通用户不能访问管理员异步消息页面。
- 前后端容器重启后上传图片仍存在。
- Actuator readiness、Prometheus targets 和 API/JVM、DB/缓存/安全、Messaging 三个 Grafana 面板正常。
- 完整 `docker compose down` 后重新 `up`，用户、收藏、评论、博客、上传文件、管理员和 Grafana 数据保持。
- Swagger 无悬空 Schema 或 Resolver 错误；浏览器控制台、网络响应、Mailpit、Prometheus、Grafana 与容器日志未发现密码、验证码、Cookie、Session ID、数据库或 RabbitMQ 凭据。
- 业务 URL、响应 DTO、状态码、Cookie、CSRF 和前端路由保持方面六契约。

验收过程中修正了 Grafana 持久卷中管理员用户名识别：Compose 用户名为 `cc4c_grafana_admin`。密码通过容器内 secret 和 Grafana CLI 标准输入同步，未写入命令、日志或本文档；Grafana 暴力破解保护保持启用。

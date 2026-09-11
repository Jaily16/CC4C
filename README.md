<div align="center">
  <img src="frontend/src/assets/logo/Logo_part1.png" alt="CC4C Logo" width="112" />
  <h1>CC4C · Course and Community for Coding</h1>
  <p>连接编程课程、技术创作与社区互动的学习平台</p>
  <p>
    <a href="https://github.com/Jaily16/CC4C/actions/workflows/build.yml"><img alt="Build" src="https://github.com/Jaily16/CC4C/actions/workflows/build.yml/badge.svg?branch=main" /></a>
    <img alt="Version" src="https://img.shields.io/badge/version-6.0.0--SNAPSHOT-2563eb" />
    <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&amp;logoColor=white" />
    <img alt="Spring Boot 3.5" src="https://img.shields.io/badge/Spring_Boot-3.5-6db33f?logo=springboot&amp;logoColor=white" />
    <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&amp;logoColor=white" />
  </p>
</div>

[项目介绍](#项目介绍) · [界面展示](#项目界面展示) · [项目架构](#项目架构) · [技术栈](#技术栈) · [目录结构](#项目目录结构) · [环境要求](#环境要求) · [启动项目](#启动项目) · [性能简介](#性能简介)

## 项目介绍

CC4C 是面向编程学习者的课程与技术社区。用户可以沿着 Java、C++、Python、C 的课程目录学习，撰写 Markdown 博客，将课程和文章加入收藏，并通过评论与回复参与讨论。管理员负责课程发布与博客审核；独立观测端帮助维护者了解接口、数据库、缓存和异步消息的运行状态。

项目由 **一个 Spring Boot 后端、一个业务 Vue 应用和一个独立观测 Vue 应用**组成。后端采用清晰的技术分层，把身份认证、事务、缓存和可靠消息落实在同一应用中。

| 模块 | 主要功能 |
| --- | --- |
| 课程学习 | 按语言浏览和搜索课程，阅读课程详情、模块与 Markdown 内容，查看推荐课程 |
| 博客社区 | 博客列表与详情、Markdown 编辑和预览、草稿恢复、图片上传、文章审核状态管理 |
| 个人空间 | 查看与编辑资料、头像、修改密码、管理课程与博客收藏 |
| 互动交流 | 课程与博客收藏、评论、回复，以及对应的权限检查 |
| 内容管理 | 管理员独立入口、课程发布、内容概览、博客审核；业务操作受角色约束 |
| 身份安全 | Spring Security、Redis Session、CSRF 防护、验证码、限流、密码摘要与身份撤销 |
| 可靠消息 | 事务内 Outbox、RabbitMQ 投递、Inbox 幂等消费、失败重试和邮件通知；提供管理查询与处置入口 |
| 运行观测 | 独立登录、8 项总览、3 个 Dashboard、20 个面板、39 条固定查询、20 条告警及依赖状态 |

一次博客审核会串起多个模块：管理员作出审核决定，业务事务保存结果与 Outbox，发布器把事件送入 RabbitMQ，消费者通过 Inbox 控制重复处理，再发送通知邮件。观测端通过指标和消息查询帮助定位各阶段的问题。

## 项目界面展示

以下为仓库保存的功能示意截图，展示数据不代表新安装后的账户或内容。点击图片可查看原图；其余截图保留在 [screenshots](frontend/screenshots) 中。

<table>
  <tr>
    <td width="50%"><strong>01 · 学习首页</strong><br /><a href="frontend/screenshots/01-home.png"><img src="frontend/screenshots/01-home.png" alt="CC4C 学习首页" width="100%" /></a><br />统一的课程、博客和个人空间入口，让学习与创作共享一套导航。</td>
    <td width="50%"><strong>02 · 课程发现</strong><br /><a href="frontend/screenshots/02-courses.png"><img src="frontend/screenshots/02-courses.png" alt="按语言筛选和搜索课程" width="100%" /></a><br />按编程语言筛选课程，通过关键词搜索主题，浏览课程难度与入口。</td>
  </tr>
  <tr>
    <td><strong>03 · 课程阅读</strong><br /><a href="frontend/screenshots/03-course-detail.png"><img src="frontend/screenshots/03-course-detail.png" alt="课程详情与文章目录" width="100%" /></a><br />Markdown 正文配合文章目录，收藏和评论入口与课程内容放在同一页面。</td>
    <td><strong>04 · 博客详情</strong><br /><a href="frontend/screenshots/05-blog-detail.png"><img src="frontend/screenshots/05-blog-detail.png" alt="博客正文、阅读量和互动入口" width="100%" /></a><br />展示作者、发布时间和阅读信息，支持目录定位、收藏与评论。</td>
  </tr>
  <tr>
    <td><strong>05 · 个人空间</strong><br /><a href="frontend/screenshots/06-profile.png"><img src="frontend/screenshots/06-profile.png" alt="个人资料与账户管理" width="100%" /></a><br />集中查看学习资料与偏好，并提供编辑资料和修改密码入口。</td>
    <td><strong>06 · 我的收藏</strong><br /><a href="frontend/screenshots/07-favorites.png"><img src="frontend/screenshots/07-favorites.png" alt="课程与博客分类收藏" width="100%" /></a><br />课程和博客分栏管理，便于回到尚未完成的学习或阅读内容。</td>
  </tr>
  <tr>
    <td><strong>07 · Markdown 创作</strong><br /><a href="frontend/screenshots/08-blog-write.png"><img src="frontend/screenshots/08-blog-write.png" alt="博客编辑、草稿恢复与实时预览" width="100%" /></a><br />标题、语言标签、正文编辑与预览集中展示；编辑流程支持草稿恢复和图片上传。</td>
    <td><strong>08 · 文章管理</strong><br /><a href="frontend/screenshots/09-blog-manage.png"><img src="frontend/screenshots/09-blog-manage.png" alt="已发布、待审核及未通过的博客" width="100%" /></a><br />作者按审核状态查看自己的文章，区分已发布、待审核和未通过内容。</td>
  </tr>
  <tr>
    <td><strong>09 · 管理概览</strong><br /><a href="frontend/screenshots/10-admin-overview.png"><img src="frontend/screenshots/10-admin-overview.png" alt="管理员内容数据概览" width="100%" /></a><br />汇总课程、公开博客与待处理事项，并展示对应内容列表。</td>
    <td><strong>10 · 博客审核</strong><br /><a href="frontend/screenshots/12-admin-review.png"><img src="frontend/screenshots/12-admin-review.png" alt="管理员博客审核队列" width="100%" /></a><br />从待审核队列选择文章，再阅读正文并作出审核决定；结果进入通知流程。</td>
  </tr>
</table>

## 项目架构

[![CC4C 架构：三端访问、后端技术分层、数据与可靠消息、独立观测链路](frontend/screenshots/architecture.svg)](frontend/screenshots/architecture.svg)

- **业务请求**：业务前端携带会话访问 HTTP 接口；安全链完成认证与授权，Service 编排业务与事务，Mapper／Repository 访问 MySQL。
- **缓存与会话**：同一个 Redis 服务承载业务 Session、业务缓存和观测 Session，使用不同 namespace；缓存保持显式数据类型和失效规则。
- **异步通知**：业务事务与 Outbox 同步落库，发布器负责确认投递；Inbox、租约与重试流程共同约束重复消费和失败恢复。
- **观测查询**：Prometheus 抓取后端 Actuator 与 RabbitMQ 指标；观测前端通过后端执行固定、有界的查询，不直接访问 Prometheus 或持有抓取密码。
- **身份隔离**：业务 USER／ADMIN、观测门户会话和管理端 Basic 认证各自承担不同职责；管理端口默认只监听本机。

V6 可读取两个明确的 V5 会话类型别名，兼容范围受限，不开放整个应用包的反序列化权限。方向为 **V6 读取 V5 会话**，不承诺 V5 能读取 V6 新写入的类型。详细边界见[项目指南](docs/project-guide.md#业务契约与安全)。

## 技术栈

| 技术 | 在项目中的用途 |
| --- | --- |
| Java 21 · Spring Boot 3.5 | 后端应用、依赖注入、配置装配、业务服务与事务边界 |
| Spring Security · Spring Session | 身份与角色授权、CSRF、安全过滤器、Redis 会话及身份隔离 |
| MyBatis-Plus · JDBC · MySQL 8.4 | 业务持久化、显式 SQL、分页查询，以及 Outbox／Inbox 数据访问 |
| Flyway | V1–V7 数据库版本迁移，统一新环境结构与公开课程初始化 |
| Redis | 业务缓存、Session、限流及验证码等共享状态 |
| RabbitMQ | 可靠事件投递、消费确认、失败重试，与 Outbox／Inbox 配合处理幂等 |
| Micrometer · Actuator · Prometheus | API／JVM／数据库／缓存／消息指标、健康探测、告警与观测查询 |
| Vue 3 · Vue Router · Vite · Element Plus | 两个前端的页面、路由、开发与构建；业务端使用 Vuex，观测端使用 Pinia |
| ECharts | 独立观测端的时序图表与仪表盘 |

GitHub Actions 执行 Java 注释、格式、前端 lint、文档链接、观测契约和生产构建检查。完整版本以 [versions.yml](versions.yml) 为准，质量边界见[项目指南](docs/project-guide.md#代码质量)。

## 项目目录结构

```text
CC4C/
├─ backend/                   后端、维护工具、OpenAPI 契约快照
├─ frontend/                  业务前端：课程、博客、个人空间、管理页面
│  ├─ src/                    api、components、composables、layout、store、views
│  ├─ scripts/                Windows 可选启停入口
│  └─ screenshots/            文档截图与架构图
├─ observability/             独立观测前端
│  ├─ src/                    api、components、composables、stores、views
│  └─ scripts/                Windows 可选启停入口
├─ infrastructure/
│  ├─ database/               数据库维护工具与历史资料
│  ├─ host/                   环境加载、前台包裹、预检和可选整栈启停
│  ├─ prometheus/             公开配置模板、规则与检查入口
│  ├─ rabbitmq/               公开 RabbitMQ 配置
│  └─ quality/                注释、源码、文档和观测契约检查
├─ .github/                   Actions 与 Dependabot
├─ docs/
│  ├─ project-guide.md        当前实现与维护指南
│  ├─ iteration-summary.md    迭代结果与验证限制
│  └─ performance-history.md  历史性能证据
├─ README.md
├─ versions.yml
├─ .editorconfig
└─ .gitignore
```

后端按技术职责分层；业务含义由类名表达，只有 `support` 保留三个子包：

```text
backend/
├─ pom.xml
├─ .env.example
├─ openapi.json
├─ scripts/
│  ├─ bootstrap-admin.ps1              首个管理员引导
│  ├─ hash-observability-password.ps1  管理／观测密码摘要
│  ├─ start-backend.ps1
│  └─ stop-backend.ps1
└─ src/main/
   ├─ java/com/
   │  ├─ cc4c/
   │  │  ├─ CC4CApplication.java       主应用扫描根
   │  │  ├─ controller/               HTTP 参数、校验与响应
   │  │  ├─ service/                  业务规则、事务、用例与转换辅助
   │  │  ├─ mapper/                   五个 MyBatis Mapper，直接承担 DAO
   │  │  ├─ repository/               Inbox／Outbox 的 JDBC 数据访问
   │  │  ├─ entity/                   持久化实体与表记录
   │  │  ├─ dto/                      请求、响应、快照与查询结果
   │  │  ├─ config/                   Spring 装配和类型化配置
   │  │  ├─ common/                   错误码、异常、校验与通用工具
   │  │  ├─ security/                 认证、Session／CSRF、限流与安全实现
   │  │  └─ support/
   │  │     ├─ cache/                 键规则、存取和编解码
   │  │     ├─ messaging/             事件、发布消费、重试与可靠投递
   │  │     ├─ monitoring/            指标、健康和 Prometheus 查询
   │  │     ├─ FileStorage.java
   │  │     └─ OutboundMailSender.java
   │  └─ cc4ctools/                   独立工具扫描根
   │     ├─ PasswordMigrationApplication.java
   │     ├─ bootstrap/                管理员引导工具
   │     └─ observability/            密码摘要工具
   └─ resources/
      ├─ application.yml              受控配置占位模板
      ├─ META-INF/spring/             管理上下文配置导入
      ├─ db/migration/                Flyway V1–V7
      └─ observability/catalog.json   固定观测查询与面板目录
```

Mapper 与 Repository 处于同一数据访问层，不增加转发式 DAO 包装。独立工具不会被主应用扫描。`node_modules`、`target`、`dist`、`temp` 和本机配置属于依赖、产物或运行资料，不是上面源码树的组成部分。

## 环境要求

先准备好下列工具和服务，无需额外安装 Python。当前版本是项目的验证基线；推荐范围是保守的兼容预期，并未逐个组合实测。项目依赖版本由 POM 和 lock 文件管理，不需要手动安装 Spring Boot、Vue 等库。

| 工具／服务 | 当前使用版本 | 推荐版本范围 | 用途 |
| --- | --- | --- | --- |
| Java JDK | 21 | 21.x | 后端构建与运行 |
| Maven | 3.9.16 | ≥3.9.16、<4 | 后端依赖与打包 |
| Node.js | 24.18.0 | 24.x | 两端构建、跨平台启动辅助 |
| npm | 11.16.0 | 11.x | 按 lock 文件准备前端依赖 |
| MySQL | 8.4.11 | 8.4.x | 业务数据、Outbox 与 Inbox |
| Redis | 8.2.9 | 8.2.x | Session、验证码、限流和缓存 |
| RabbitMQ | 4.3.5 | 4.3.x | 可靠异步消息 |
| Erlang/OTP | RabbitMQ 配套要求 | 27.x | RabbitMQ 运行环境 |
| Prometheus | 3.13.2 | 3.13.x | 指标抓取、查询和告警 |
| PowerShell，可选 | 7.6.5 | 7.6.x | 旧 Windows 入口及完整质量检查 |

新启动入口在 Windows 和 Linux 上使用同一组 Node 命令。Node 24 满足 [Vite 的要求](https://vite.dev/guide/)，RabbitMQ 与 Erlang 的组合以[官方兼容矩阵](https://www.rabbitmq.com/docs/which-erlang)为准。CI 继续使用 [versions.yml](versions.yml) 中的精确版本。

| 本机端口 | 服务 |
| --- | --- |
| 3306 / 6379 | MySQL / Redis |
| 5672 / 15672 / 15692 | RabbitMQ 消息 / 管理页面 / 指标 |
| 9090 | Prometheus |
| 4080 / 4081 | 后端业务 API / 本机管理指标 |
| 5173 / 5174 | 业务前端 / 观测前端 |

## 启动项目

### 1. 确认工具与基础设施已就绪

~~~bash
java -version
mvn --version
node --version
npm --version
~~~

确认 Maven 显示 Java 21。若终端仍选择其他 Java，只需将 `JAVA_HOME` 指向已有 JDK，并把其 bin 加到 PATH：

~~~powershell
# Windows；替换为自己的安装目录。
$env:JAVA_HOME = 'C:\tools\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
~~~

~~~bash
# Linux；替换为自己的安装目录。
export JAVA_HOME=/opt/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
~~~

已安装的服务按自己的服务名启动；不要重复启动已有实例。Windows 服务安装方式与 Linux 示例分别如下：

~~~powershell
# Windows 服务名以实际安装为准。
Start-Service MySQL84
Start-Service RabbitMQ
# Redis 使用已有实例的启动入口，例如在它的目录运行：
.\redis-server.exe .\redis.conf
~~~

~~~bash
# Linux，Redis 服务名可能为 redis 或 redis-server。
sudo systemctl start mysql redis-server rabbitmq-server
~~~

确认结果即可，不需要读取数据库内容：

~~~bash
mysqladmin -h 127.0.0.1 -u root -p ping
redis-cli -h 127.0.0.1 -p 6379 ping
rabbitmq-diagnostics ping
~~~

预期分别看到 MySQL 存活、`PONG` 和 RabbitMQ 节点正常。Windows RabbitMQ 命令使用 `.bat` 后缀；Linux 按安装方式使用服务账号或 `sudo`。Redis 如启用认证，使用已有认证方式，不把密码写在命令参数中。SMTP 需另行准备可用的发件账户和授权码。

### 2. 获取源码与构建

~~~bash
git clone https://github.com/Jaily16/CC4C.git
cd CC4C
~~~

以下命令在各自目录执行，Windows、Linux 相同。首次准备需要下载锁定依赖；命令失败时先处理原因，不继续启动。

~~~bash
# backend 目录
mvn clean package -DskipTests
~~~

~~~bash
# frontend 目录与 observability 目录，各执行一次
npm ci
npm run build
~~~

后端生成主应用、管理员引导和密码辅助三种 JAR；两端各生成 `dist`。构建无需 `.env.local`。已有完整 Maven 缓存时，可使用 `mvn -o clean package -DskipTests` 离线构建。

### 3. 准备专用数据库和 RabbitMQ 资源

使用 MySQL 管理员登录后，在**新环境**执行以下 SQL，替换密码占位符。同名库或账号已存在时先核对，不删除重建。

~~~sql
CREATE DATABASE cc4c_runtime CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'cc4c_runtime_user'@'127.0.0.1' IDENTIFIED BY 'REPLACE_WITH_DATABASE_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON cc4c_runtime.* TO 'cc4c_runtime_user'@'127.0.0.1';
~~~

空库由首次后端启动时的 **Flyway V1–V7** 自动建表，导入 **4 种语言、61 门公开课程、9 个模块及 61 条模块课程关联**，不需要手动导入课程 SQL。项目没有默认用户或管理员。

RabbitMQ 只需一个专用 vhost、一个业务账号和一个监控账号。以下命令在 RabbitMQ CLI 可用的终端执行；Windows 为命令加 `.bat`，Linux 通常加 `sudo`。`add_user` 会提示输入密码，两个账号分别设置自己的密码。

~~~bash
rabbitmq-plugins enable rabbitmq_management rabbitmq_prometheus
rabbitmqctl add_vhost cc4c
rabbitmqctl add_user cc4c_user
rabbitmqctl set_permissions -p cc4c cc4c_user '.*' '.*' '.*'
rabbitmqctl add_user cc4c_monitor
rabbitmqctl set_user_tags cc4c_monitor monitoring
rabbitmqctl set_permissions -p cc4c cc4c_monitor '^$' '^$' '^$'
~~~

业务账号只访问自己的 vhost；监控账号没有消息读写权限。将 [rabbitmq.conf](infrastructure/rabbitmq/rabbitmq.conf) 中的必要项合入实例配置，保留 `prometheus.authentication.enabled = true`，按服务管理方式重启生效。已有实例不能直接覆盖配置或重新创建账号。

### 4. 填写自己的配置

分别将 `backend`、`frontend`、`observability` 中的 `.env.example` **复制为同目录的 `.env.local`**；已有本机配置直接保留。文件使用 UTF-8，每行 `NAME=value`，不要添加 `export`、外层引号或行尾注释。后端中的 `$`、`#`、空格和后续 `=` 均按字面保存。

**新环境的后端只需重点填写以下内容，其余预填值可保留：**

~~~dotenv
# MySQL：如库名和用户名采用上述示例，只需填写密码。
CC4C_DB_PASSWORD=你的数据库密码

# SMTP：587 示例使用 STARTTLS；用户名通常也是发件邮箱。
CC4C_MAIL_HOST=你的SMTP服务器
CC4C_MAIL_USERNAME=你的发件邮箱
CC4C_MAIL_PASSWORD=你的SMTP密码或授权码
CC4C_MODERATION_NOTIFICATION_RECIPIENTS=审核通知收件邮箱

# RabbitMQ：对应刚创建的业务账号和独立监控账号。
CC4C_RABBITMQ_URL=amqp://cc4c_user:你的URL编码密码@127.0.0.1:5672/cc4c
CC4C_RABBITMQ_MONITOR_PASSWORD=你的监控账号密码

# 观测门户：12–64 个字符，UTF-8 不超过 72 字节，无需特定字符组合。
CC4C_OBSERVABILITY_PASSWORD=你设置的观测登录密码
~~~

- 数据库名称或地址不同：修改 `CC4C_DB_URL`、`CC4C_DB_USERNAME`；启动时确认同一个库名。
- Redis 地址或认证不同：修改 `CC4C_REDIS_URL`；保留三个互不相同的 Session／缓存 namespace。
- SMTP 使用 465：按服务商要求设置端口、`SSL=true`、`STARTTLS=false`；两种 TLS 方式不能同时启用。
- URL 中密码包含 `@`、`:`、`/`、`#`、`%` 等字符时，需要对用户名／密码部分进行 URL 编码；其他独立密码字段不编码。
- 两端 `.env.local` 默认都是公开 API 地址 `http://localhost:4080`，本机复现可直接保留，不能填写后端秘密。
- 上传默认保存在项目 `temp/uploads`。磁盘位置与浏览器 URL 分开配置；改变磁盘路径时修改后端的两个 `CC4C_SAVE_…` 字段。新入口会把两个非 `VITE_` 根变量传给业务 Vite，观测端不接收它们。

回到**仓库根目录**，新环境执行一次：

~~~bash
node infrastructure/host/cc4c.mjs setup --new-environment
~~~

程序在被 Git 忽略的 `temp/local-runtime` 中生成独立的 Pepper、消息密钥、指标抓取密码及 Prometheus 私有配置。**不需要手工生成密钥或粘贴 BCrypt**：两套摘要由启动入口计算，观测门户使用你填写的密码，指标抓取使用程序生成的独立密码。

这份私有目录需要随自己的运行环境保留，不能作为缓存删除或上传。重复 setup 会保留随机材料，只更新自己生成的抓取配置。普通启动不会自动重建丢失的密钥。已有完整旧配置继续原样启动，不执行 setup；不要把新旧安全字段混填。兼容、恢复及密钥轮换见[项目指南](docs/project-guide.md#自动安全材料与旧配置兼容)。

### 5. 启动 Prometheus 和三端

**新环境 Prometheus** 在仓库根目录启动；可执行文件不在 PATH 时换成它的绝对路径：

~~~bash
prometheus --config.file=temp/local-runtime/prometheus.yml --storage.tsdb.path=temp/prometheus-data --web.listen-address=127.0.0.1:9090
~~~

生成的 YAML 已填好端口、用户名、环境标签、规则和 vhost／队列过滤，密码通过私有文件引用。打开 `http://localhost:9090/targets` 查看抓取状态；后端尚未启动时其 target 为 DOWN 属于正常现象。已有环境继续使用原 Prometheus 私有配置及数据目录，不替换或重载。

在工具链就绪的**三个独立终端**中进入仓库根目录，依次执行。Windows 与 Linux 命令相同：

~~~bash
# 终端一：后端，库名必须与自己的配置精确一致。
node infrastructure/host/cc4c.mjs backend --database cc4c_runtime
~~~

~~~bash
# 终端二：业务前端。
node infrastructure/host/cc4c.mjs frontend
~~~

~~~bash
# 终端三：观测前端。
node infrastructure/host/cc4c.mjs observability
~~~

入口负责配置加载、正确的执行目录、环境隔离和前台运行；不会安装依赖、启动中间件或占用其他端口。后端运行本次构建的 JAR，修改 Java 后需重新构建。两端使用 Vite 开发服务器及既有上传映射，**dist 本身不是完整的本机部署入口**。

| 检查入口 | 预期结果 |
| --- | --- |
| `http://localhost:5173` | 业务首页，可以注册自己的用户 |
| `http://localhost:5174` | 独立观测登录；默认用户名 `cc4c_observer` |
| `http://127.0.0.1:4081/actuator/health` | 后端公开健康状态 |
| `http://127.0.0.1:4081/actuator/health/readiness` | 数据库与安全 Redis 就绪 |
| `http://localhost:9090/targets` | 后端、RabbitMQ 的抓取结果 |

停止时按**观测端 → 业务前端 → 后端**顺序，在各自终端按 `Ctrl+C`。Prometheus 使用自己的终端停止；应用入口不会停止其他基础设施。Linux 步骤与跨平台辅助依据源码编写，不代表经过完整 Linux 服务部署验证。

### 6. 首次账号与自己的数据恢复

普通用户通过注册页面及邮件验证码创建。观测账号和业务管理员不同，登录观测端不会取得业务管理权限。

新空库的七份 Flyway 迁移完成后，若要创建**首个管理员**，先停止后端，把自己的管理员密码保存到仓库外的一个普通 UTF-8 文件中，再显式运行现有引导工具的短入口：

~~~bash
# 根目录；替换为自己的仓库外绝对路径，Windows 可使用 C:/Users/自己的用户名/CC4C-local/admin-password.txt。
node infrastructure/host/cc4c.mjs bootstrap-admin --database cc4c_runtime --id 1000001 --password-file /absolute/private/admin-password.txt
~~~

密码为 8–64 个 Unicode 字符、UTF-8 不超过 72 字节。管理员以七位 ID 登录；该命令会写入管理员账户，已有管理员的环境不重复执行，不覆盖现有账户。完成后用前面的 backend 命令重新启动。

如果是在**恢复自己的当前版本备份**，先另建一个空目标库，再在 MySQL 客户端中导入自己的文件；这条路径不用于首次课程初始化：

~~~bash
mysql -h 127.0.0.1 -u root -p cc4c_restore
~~~

~~~sql
SOURCE /absolute/path/to/your-current-backup.sql;
~~~

Windows 可使用 `C:/backups/your-current-backup.sql`。恢复后核对 Flyway 历史，再把连接地址和启动确认名一起改为恢复库。不要关闭校验或对非空历史库自动 baseline；详细边界见[数据库维护](docs/project-guide.md#数据库维护)。

### 常见问题与可选入口

| 现象 | 首先检查 |
| --- | --- |
| Java 版本错误 | `mvn --version` 是否显示 Java 21；检查本终端 JAVA_HOME |
| Redis 连接拒绝 | 现有 Redis 是否启动、6379 是否可达；不清空 Redis |
| RabbitMQ 认证失败 | URL 编码、vhost 及业务账号权限是否对应 |
| 收不到验证码／审核邮件 | SMTP 授权码、端口、SSL／STARTTLS、发件人与收件人 |
| 页面 Network Error | 后端 readiness、公开 API 地址和精确 Origin |
| 端口被占用 | 核实已有进程身份，不自动换端口或批量结束进程 |
| 本机材料缺失或损坏 | 恢复原私有材料，不删除后重新生成消息密钥 |
| 图片不显示 | 使用新启动入口传递上传根，检查磁盘路径与 URL 的对应 |
| Prometheus 无数据 | targets、指标凭据及环境／vhost／namespace；后端摘要不是抓取密码 |

旧 PowerShell 包裹和整栈脚本继续支持原有完整环境配置，属于可选兼容入口；新自动配置使用上面的 Node 命令。旧脚本的前提、预检、进程记录和停止范围见[项目指南](docs/project-guide.md#可选旧入口预检启动与健康)。

## 性能简介

当前实现保留 Redis 业务缓存、细粒度失效及 MyBatis 查询指标。缓存命中时，公开读取可以直接复用结果，减少重复 SELECT 和请求延迟。下面展示该缓存优化的受控对照：

| 指标 | 优化前：无缓存 | 优化后：热缓存 |
| --- | ---: | ---: |
| p95 | 182.514 ms | 5.177 ms |
| 吞吐 | 464.458 req/s | 4,633.079 req/s |
| 测量阶段 MyBatis SELECT | 10,995 | 0 |

条件为同机九类公开读取、每轮 3,000 请求、并发上限 16，延迟与吞吐取三轮中位数；HTTP 错误为 0，热缓存命中率为 100%。这些数字来自该优化方案已有的对照记录，本次复现流程整理未重新压测。实验条件、其他测量及证据留存情况见[性能记录](docs/performance-history.md#3-缓存基准)。

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

本地复现按下表准备；Linux 示例针对 Ubuntu 24.04 / Bash。Windows 脚本要求 **PowerShell 7**，系统自带 Windows PowerShell 5.1 无法执行这些脚本。

| 组件 | 仓库基线 | 用途／默认端口 |
| --- | --- | --- |
| JDK | 21 | 后端与独立 Java 工具 |
| Maven | 3.9.16 | 后端依赖与打包 |
| Node.js / npm | 24.18.0 / 11.16.0 | 两端依赖与 Vite |
| PowerShell | 7.6.5 | Windows 脚本；Linux 下文使用 Bash |
| MySQL | 8.4.11 | 业务与消息表，3306 |
| Redis | 8.2.9 | Session、缓存与安全状态，6379 |
| RabbitMQ | 4.3.5，配 Erlang 27.x | AMQP 5672；管理 15672；指标 15692 |
| Prometheus | 3.13.2 | 指标查询，9090 |
| SMTP | 服务商提供 | 验证码与通知；通常 587 STARTTLS 或 465 SSL |
| Git | 可用的 Git CLI | 获取源码 |

应用端口为 **4080**（业务及观测 API）、**4081**（本机管理端）、**5173**（业务页面）、**5174**（观测页面）。本机示例统一使用 `localhost` 访问两个前端，避免与 `127.0.0.1` 混用导致 Origin 或 Cookie 行为不同。

Python、Docker、Grafana 都不是当前三端运行的必需组件。Python 如用于个人辅助工作，可通过 [Python 官方安装入口](https://www.python.org/downloads/) 安装，Ubuntu 可使用 `sudo apt install python3`；项目启动不调用 Python。

## 启动项目

以下命令供读者在自己的开发环境执行。Windows 入口沿用已记录的本机运行方式；Linux 命令依据当前源码与官方说明编写，**未进行 Linux 实机运行验证**。首次启动会执行 Flyway 校验／迁移，正常运行也会写入 Session、消息和业务数据。

### 1. 安装工具链与基础设施

安装包可选择下表中的一种方式。优先使用上节锁定版本；软件源的默认最新版不等于项目版本，安装前查看可用版本。已有兼容环境直接复用，不重复创建账号或覆盖配置。

| 组件 | Windows | Ubuntu 24.04 |
| --- | --- | --- |
| Java 21 | [Temurin 安装包](https://adoptium.net/installation/)；或 `winget install EclipseAdoptium.Temurin.21.JDK` | 按 [Adoptium Linux 安装说明](https://adoptium.net/installation/linux/) 配置软件源后安装 `temurin-21-jdk` |
| Maven | 下载 [3.9.16 二进制 ZIP](https://maven.apache.org/install.html)，解压并将 `bin` 加入 PATH | 同页下载二进制 tar.gz，解压，将 `bin` 加入 PATH |
| Node.js | [24.18.0 MSI／ZIP](https://nodejs.org/en/download/archive/v24.18.0)，安装后核对 npm | 同页选择 Linux 对应架构 tar.xz，解压并将 `bin` 加入 PATH |
| PowerShell | [官方安装说明](https://learn.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-windows)；或 `winget install Microsoft.PowerShell`，打开 `pwsh` | 下文启动方式不要求安装 |
| MySQL | [8.4 MSI 与 Configurator](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html)，安装 Server 与命令行客户端 | [MySQL APT 源](https://dev.mysql.com/doc/refman/8.4/en/linux-installation-apt-repo.html)，选择 8.4 LTS 后安装 Server／Client |
| Redis | 在 [WSL2](https://learn.microsoft.com/en-us/windows/wsl/install) 的 Ubuntu 中按右侧方式安装；Windows 应用连接本机转发端口 | 使用 [Redis 官方安装入口](https://redis.io/docs/latest/operate/oss_and_stack/install/) 的 APT 说明，选择 8.2.9 |
| RabbitMQ | 先装兼容 Erlang，再运行 [RabbitMQ 安装包](https://www.rabbitmq.com/docs/install-windows) | 按 [官方 APT 说明](https://www.rabbitmq.com/docs/install-debian) 配置 Erlang／RabbitMQ 软件源并选定版本 |
| Prometheus | [官方二进制下载](https://prometheus.io/download/)，选择 3.13.2 windows-amd64 | 同页选择 3.13.2 linux-amd64 或适合设备的架构 |
| Git | [Git for Windows](https://git-scm.com/install/windows) | `sudo apt update && sudo apt install git` |

RabbitMQ 4.3.5 使用官方兼容矩阵支持的 Erlang 27.x，不直接追随 Erlang 最新大版本。[兼容矩阵](https://www.rabbitmq.com/docs/which-erlang)

**Windows：**建议安装路径不包含特殊字符。设置自己的 JDK 与 Maven 路径后，重新打开 PowerShell 7：

```powershell
# 临时选择本次终端的工具链；路径按实际安装位置修改。
$env:JAVA_HOME = 'C:\tools\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;C:\tools\apache-maven-3.9.16\bin;$env:PATH"
$PSVersionTable.PSVersion
java -version
mvn --version
node --version
npm --version
```

**Linux：**解压工具的位置按实际安装路径调整：

```bash
export JAVA_HOME=/opt/jdk-21
export PATH="$JAVA_HOME/bin:/opt/apache-maven-3.9.16/bin:/opt/node-v24.18.0-linux-x64/bin:$PATH"
java -version
mvn --version
node --version
npm --version
```

预期 Java 为 21，Maven 为 3.9.16，Node 为 v24.18.0，npm 为 11.16.0。两端 engines 已固定；版本不匹配应先修正工具链。

通过系统安装器／软件源安装的服务可使用以下命令启动；Windows MySQL 服务名以 Configurator 的实际设置为准：

```powershell
# 管理员 PowerShell；Redis 命令在已安装 Redis 的 WSL Ubuntu 内运行。
Start-Service MySQL84
Start-Service RabbitMQ
wsl -d Ubuntu -- sudo service redis-server start
```

```bash
# Ubuntu；只针对刚配置好的本机服务。
sudo systemctl start mysql
sudo systemctl start redis-server
sudo systemctl start rabbitmq-server
redis-cli ping
```

Redis 返回 `PONG` 表示连接可用。WSL 的 Redis 只用于本机开发，保持 loopback／protected-mode；不要为解决连接问题直接开放公网访问。[WSL 数据库安装与启动](https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-database)

### 2. 获取源码与准备构建产物

在存放项目的父目录执行：

```bash
git clone https://github.com/Jaily16/CC4C.git
cd CC4C
```

下面两段均从仓库根目录开始。首次构建允许 Maven 下载固定依赖；`npm ci` 按锁文件安装，不使用 `npm install` 重写锁文件。

**Windows / PowerShell 7：**

```powershell
$ErrorActionPreference = 'Stop'
Push-Location backend
try {
    mvn -B -ntp clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
} finally { Pop-Location }
foreach ($app in @('frontend', 'observability')) {
    Push-Location $app
    try {
        npm ci --ignore-scripts --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed' }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    } finally { Pop-Location }
}
```

**Linux / Bash：**

```bash
(
  set -e
  (cd backend && mvn -B -ntp clean package -DskipTests)
  for app in frontend observability; do
    (cd "$app" && npm ci --ignore-scripts --no-audit --no-fund && npm run build)
  done
)
```

后端生成主应用 JAR、`-admin-bootstrap.jar` 和 `-observability-password.jar`；两个前端生成各自的 `dist`。构建不需要读取本机环境文件。依赖已经齐备时，可以在 Maven 命令中加入 `-o` 离线运行。

### 3. 准备 MySQL、Redis、RabbitMQ 与 SMTP

**MySQL：**先以数据库管理员连接，`-p` 后不写密码，由客户端提示输入：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p
```

在 MySQL 客户端执行，替换密码占位符；示例只面向新环境，同名数据库或账号已存在时先核对，不能删掉重建：

```sql
CREATE DATABASE cc4c_runtime
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'cc4c_runtime_user'@'127.0.0.1'
  IDENTIFIED BY 'REPLACE_WITH_DATABASE_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON cc4c_runtime.* TO 'cc4c_runtime_user'@'127.0.0.1';
```

运行账户只获得专用库的业务与迁移权限，不使用 root。首次后端启动由 **Flyway V1–V7** 自动创建表，导入 **4 种语言、61 门公开课程、9 个模块及 61 条模块课程关联**。数据库必须为空，不能手工导入 `infrastructure/database/legacy/cc4c.sql` 再让 Flyway 接管；该文件属于历史资料。

**Redis：**本机示例连接 `redis://127.0.0.1:6379/0`。如实例使用 ACL／密码，按自己的凭据填写连接 URL。业务 Session、缓存、观测 Session 必须使用不同 namespace；重启时保留原 namespace 和数据，不能通过清库排查登录问题。

**RabbitMQ：**在 RabbitMQ CLI 可用的终端执行。Windows 命令带 `.bat`；Linux 使用同名无后缀命令，通常通过 `sudo` 以服务权限执行：

```powershell
# Windows：RabbitMQ sbin 已加入 PATH。
rabbitmq-plugins.bat enable rabbitmq_management rabbitmq_prometheus
rabbitmqctl.bat add_vhost cc4c
rabbitmqctl.bat add_user cc4c_user
rabbitmqctl.bat set_permissions -p cc4c cc4c_user '.*' '.*' '.*'
rabbitmqctl.bat add_user cc4c_monitor
rabbitmqctl.bat set_user_tags cc4c_monitor monitoring
rabbitmqctl.bat set_permissions -p cc4c cc4c_monitor '^$' '^$' '^$'
```

```bash
sudo rabbitmq-plugins enable rabbitmq_management rabbitmq_prometheus
sudo rabbitmqctl add_vhost cc4c
sudo rabbitmqctl add_user cc4c_user
sudo rabbitmqctl set_permissions -p cc4c cc4c_user '.*' '.*' '.*'
sudo rabbitmqctl add_user cc4c_monitor
sudo rabbitmqctl set_user_tags cc4c_monitor monitoring
sudo rabbitmqctl set_permissions -p cc4c cc4c_monitor '^$' '^$' '^$'
```

`add_user` 在未提供密码参数时交互提示输入；为应用账号和监控账号选择不同密码。应用权限只属于 `cc4c` 这个专用 vhost，不授予管理标签。将公开的 [rabbitmq.conf](infrastructure/rabbitmq/rabbitmq.conf) 配置项合入自己实例的配置，启用 `prometheus.authentication.enabled = true`；配置位置及修改后的服务重启按[官方配置说明](https://www.rabbitmq.com/docs/configure)处理，不覆盖已有实例配置。指标端口为 15692。

**SMTP：**准备服务商的主机、端口、发件账户和密码／授权码。587 通常配 `STARTTLS=true、SSL=false`；465 通常配 `SSL=true、STARTTLS=false`，具体按服务商要求设置，不能同时启用。`CC4C_MODERATION_NOTIFICATION_RECIPIENTS` 填写真实审核通知收件人。注册验证码与审核邮件需要可用 SMTP，不会随数据库初始化创建。

### 4. 创建三端环境文件

只从公开模板创建**尚不存在**的本机文件。下面命令从根目录执行，任一文件已存在便停止，避免覆盖配置。

```powershell
$ErrorActionPreference = 'Stop'
foreach ($app in @('backend', 'frontend', 'observability')) {
    if (Test-Path -LiteralPath "$app/.env.local") { throw "$app/.env.local already exists" }
}
foreach ($app in @('backend', 'frontend', 'observability')) {
    [IO.File]::Copy(
        (Join-Path $PWD "$app/.env.example"),
        (Join-Path $PWD "$app/.env.local"), $false)
}
git check-ignore -- backend/.env.local frontend/.env.local observability/.env.local
```

```bash
(
  set -e
  for app in backend frontend observability; do
    [[ ! -e "$app/.env.local" && ! -L "$app/.env.local" ]] || {
      printf '%s\n' "$app/.env.local already exists" >&2; exit 1;
    }
  done
  umask 077
  set -o noclobber
  for app in backend frontend observability; do
    cat "$app/.env.example" > "$app/.env.local"
  done
  git check-ignore -- backend/.env.local frontend/.env.local observability/.env.local
)
```

配置文件使用 UTF-8、无 BOM、每行 `NAME=value`。后端值按字面读取，不加 `export`、外层引号或行尾注释；密码中的 `$`、`#`、空格及 `=` 都是值的一部分。布尔值写小写 `true／false`。不要新建其他 Vite 模式环境文件。

#### backend/.env.local：完整 50 项示例

以下 `REPLACE_…` 和 `example.com` 都必须按自己的环境替换；密码摘要和密钥的生成见下一节。

```dotenv
# MySQL
CC4C_DB_URL=jdbc:mysql://127.0.0.1:3306/cc4c_runtime
CC4C_DB_USERNAME=cc4c_runtime_user
CC4C_DB_PASSWORD=REPLACE_WITH_DATABASE_PASSWORD
CC4C_DB_CONNECTION_TIMEOUT_MS=3000
CC4C_DB_VALIDATION_TIMEOUT_MS=1000

# Redis：三个 namespace 必须不同
CC4C_REDIS_URL=redis://127.0.0.1:6379/0
CC4C_SESSION_NAMESPACE=cc4c:session
CC4C_BUSINESS_CACHE_ENABLED=true
CC4C_CACHE_NAMESPACE=cc4c:v3:cache:local
CC4C_SECURITY_PEPPER=REPLACE_WITH_RANDOM_PEPPER_AT_LEAST_32_CHARACTERS
CC4C_SESSION_COOKIE_SECURE=false
CC4C_ALLOWED_ORIGINS=http://localhost:5173

# SMTP
CC4C_MAIL_HOST=smtp.example.com
CC4C_MAIL_PORT=587
CC4C_MAIL_AUTH=true
CC4C_MAIL_SSL_ENABLED=false
CC4C_MAIL_STARTTLS_ENABLED=true
CC4C_MAIL_USERNAME=REPLACE_WITH_SMTP_ACCOUNT
CC4C_MAIL_PASSWORD=REPLACE_WITH_SMTP_PASSWORD

# RabbitMQ 与可靠消息
CC4C_RABBITMQ_URL=amqp://cc4c_user:REPLACE_WITH_URL_ENCODED_PASSWORD@127.0.0.1:5672/cc4c
CC4C_RABBITMQ_NAMESPACE=cc4c.v3.messaging.local
CC4C_MODERATION_NOTIFICATION_RECIPIENTS=reviewer@example.com
CC4C_MESSAGING_ACTIVE_KEY_ID=local-v1
CC4C_MESSAGING_PAYLOAD_KEYS=local-v1=REPLACE_WITH_BASE64_32_BYTE_KEY
CC4C_MESSAGING_CONFIRM_TIMEOUT=5s
CC4C_MESSAGING_CONSUMER_RETRY_DELAYS=30s,5m,30m
CC4C_OUTBOX_DISPATCHER_ENABLED=true
CC4C_MESSAGE_CONSUMERS_ENABLED=true

# 管理指标：仅监听本机；摘要对应 Prometheus 抓取时使用的密码
CC4C_API_DOCS_ENABLED=false
CC4C_OBSERVABILITY_ENABLED=true
CC4C_MANAGEMENT_ADDRESS=127.0.0.1
CC4C_MANAGEMENT_PORT=4081
CC4C_MANAGEMENT_USERNAME=cc4c_observer
CC4C_MANAGEMENT_PASSWORD_HASH=REPLACE_WITH_MANAGEMENT_BCRYPT_COST12

# 观测门户：另一套密码与独立 Session
CC4C_OBSERVABILITY_USERNAME=cc4c_observer
CC4C_OBSERVABILITY_PASSWORD_HASH=REPLACE_WITH_PORTAL_BCRYPT_COST12
CC4C_OBSERVABILITY_SESSION_NAMESPACE=cc4c:observability
CC4C_OBSERVABILITY_COOKIE_SECURE=false
CC4C_OBSERVABILITY_ALLOWED_ORIGIN=http://localhost:5174
CC4C_PROMETHEUS_URL=http://127.0.0.1:9090
CC4C_PROMETHEUS_USERNAME=
CC4C_PROMETHEUS_PASSWORD=
CC4C_OBSERVABILITY_ENVIRONMENT=local
CC4C_LOG_FORMAT=ecs
CC4C_MESSAGING_SAMPLE_INTERVAL=15s
CC4C_MAX_HTTP_URI_TAGS=100

# 上传：磁盘路径相对 backend；浏览器 URL 由业务前端映射
CC4C_SAVE_IMG_PATH=../temp/uploads/blogImg/
CC4C_REQUEST_IMG_PATH=http://localhost:5173/blogImg/
CC4C_SAVE_AVATAR_PATH=../temp/uploads/avatar/
CC4C_REQUEST_AVATAR_PATH=http://localhost:5173/avatar/
```

两个前端的 `.env.local` **分别只填一行**，不放入数据库、SMTP、观测密码或消息密钥：

```dotenv
VITE_API_BASE_URL=http://localhost:4080
```

`CC4C_PROMETHEUS_USERNAME/PASSWORD` 是后端访问 **Prometheus 自身 API** 的可选认证；本机 Prometheus 未启用 API 认证时两项都留空，不能误填成管理端抓取密码。两项如需配置必须同时填写。

Redis／RabbitMQ URL 中的用户名与密码需要 URL 编码，例如 `@ → %40`、`: → %3A`、`/ → %2F`；普通 `CC4C_DB_PASSWORD` 和 SMTP 密码字段不做 URL 编码。示例 Cookie 的 `secure=false` 仅用于本机 HTTP；HTTPS 部署需相应调整 Origin、Cookie 和资源 URL。

#### Pepper、消息密钥与两套 BCrypt 摘要

下列命令在自己的终端生成随机值，Windows 和 Linux 通用；不要把输出提交到 Git：

```bash
# 第一项写入 CC4C_SECURITY_PEPPER。
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
# 第二项放到 CC4C_MESSAGING_PAYLOAD_KEYS 的 local-v1= 后面。
node -e "console.log(require('node:crypto').randomBytes(32).toString('base64'))"
```

已有环境的 Pepper 和消息密钥不能随重启重新生成，否则既有安全数据或加密消息可能无法继续使用；轮换规则见[异步消息维护](docs/project-guide.md#异步消息维护)。

准备一个仓库外的私有目录，例如 Windows 的 `$HOME\CC4C-local`，Linux 的 `$HOME/.config/cc4c`。手工创建 `management-password.txt` 与 `portal-password.txt`，各含一行不同密码，无 BOM；限制为当前用户可读。密码为 12–64 个字符，且 UTF-8 不超过 72 字节。

在 **Java 21 已生效、后端已打包** 的终端执行：

```powershell
# 仓库根目录；输出两个不同的 cost-12 BCrypt 摘要。
.\backend\scripts\hash-observability-password.ps1 -PasswordFile "$HOME\CC4C-local\management-password.txt"
if ($LASTEXITCODE -ne 0) { throw 'Management password hashing failed' }
.\backend\scripts\hash-observability-password.ps1 -PasswordFile "$HOME\CC4C-local\portal-password.txt"
if ($LASTEXITCODE -ne 0) { throw 'Portal password hashing failed' }
```

```bash
# backend 目录；密码文件仅保存在仓库外。
chmod 700 "$HOME/.config/cc4c"
chmod 600 "$HOME/.config/cc4c/management-password.txt" "$HOME/.config/cc4c/portal-password.txt"
CC4C_OBSERVABILITY_PASSWORD_FILE="$HOME/.config/cc4c/management-password.txt" \
  java -jar target/cc4c-6.0.0-SNAPSHOT-observability-password.jar
CC4C_OBSERVABILITY_PASSWORD_FILE="$HOME/.config/cc4c/portal-password.txt" \
  java -jar target/cc4c-6.0.0-SNAPSHOT-observability-password.jar
```

第一个摘要填 `CC4C_MANAGEMENT_PASSWORD_HASH`，第二个填 `CC4C_OBSERVABILITY_PASSWORD_HASH`；保留完整 `$2…$12$…` 字符串，不加引号。Prometheus 使用第一个文件对应的**原密码**抓取后端；浏览器观测登录使用第二个文件对应的原密码。

### 5. 配置与启动 Prometheus

将 [Prometheus 公开模板](infrastructure/prometheus/prometheus.yml.template) 和 [告警规则](infrastructure/prometheus/rules/cc4c-alerts.yml) 复制到仓库外自己的目录。模板没有真实密码，不能直接作为已完成配置使用。

**Windows，仓库根目录：**

```powershell
$ErrorActionPreference = 'Stop'
$promConfigDir = Join-Path $HOME 'CC4C-local\prometheus'
foreach ($file in @('prometheus.yml', 'rules\cc4c-alerts.yml')) {
    if (Test-Path -LiteralPath (Join-Path $promConfigDir $file)) { throw 'Prometheus target already exists' }
}
[IO.Directory]::CreateDirectory((Join-Path $promConfigDir 'rules')) | Out-Null
[IO.File]::Copy((Join-Path $PWD 'infrastructure\prometheus\prometheus.yml.template'),
    (Join-Path $promConfigDir 'prometheus.yml'), $false)
[IO.File]::Copy((Join-Path $PWD 'infrastructure\prometheus\rules\cc4c-alerts.yml'),
    (Join-Path $promConfigDir 'rules\cc4c-alerts.yml'), $false)
```

**Linux，仓库根目录：**

```bash
(
  set -e
  umask 077
  config_dir="$HOME/.config/cc4c/prometheus"
  mkdir -p "$config_dir/rules"
  set -o noclobber
  cat infrastructure/prometheus/prometheus.yml.template > "$config_dir/prometheus.yml"
  cat infrastructure/prometheus/rules/cc4c-alerts.yml > "$config_dir/rules/cc4c-alerts.yml"
)
```

在自己的 `prometheus.yml` 中替换以下全部占位符，保留相对规则路径 `rules/cc4c-alerts.yml`：

| 占位符 | 本机示例／填写来源 |
| --- | --- |
| `__CC4C_MANAGEMENT_USERNAME__` | `cc4c_observer`，与后端管理用户名相同 |
| `__CC4C_MANAGEMENT_PASSWORD__` | `management-password.txt` 中的原密码，不是 BCrypt 摘要 |
| `__CC4C_ENVIRONMENT__` | `local`，与后端环境标签相同 |
| `__CC4C_RABBITMQ_MONITOR_USERNAME__` | `cc4c_monitor` |
| `__CC4C_RABBITMQ_MONITOR_PASSWORD__` | RabbitMQ 监控账号密码 |
| `__CC4C_RABBITMQ_VHOST_REGEX__` | `cc4c` |
| `__CC4C_RABBITMQ_NAMESPACE_REGEX__` | `cc4c\.v3\.messaging\.local`，将 namespace 中的点转义 |

模板使用 YAML 单引号；密码含单引号时，在 YAML 内写成两个连续单引号。保留配置中的原始反斜杠。私有文件只授予当前运行用户读取权限，不提交到项目；Windows 可在目录“属性 → 安全”中设置，Linux 使用前述 `umask 077`。

在独立终端检查配置并前台启动，工具路径按实际安装位置修改：

```powershell
$promDir = 'C:\tools\prometheus-3.13.2.windows-amd64'
$promConfigDir = Join-Path $HOME 'CC4C-local\prometheus'
& "$promDir\promtool.exe" check config "$promConfigDir\prometheus.yml"
if ($LASTEXITCODE -ne 0) { throw 'Prometheus config is invalid' }
& "$promDir\promtool.exe" check rules "$promConfigDir\rules\cc4c-alerts.yml"
if ($LASTEXITCODE -ne 0) { throw 'Prometheus rules are invalid' }
& "$promDir\prometheus.exe" `
    --config.file="$promConfigDir\prometheus.yml" `
    --storage.tsdb.path="$HOME\CC4C-local\prometheus-data" `
    --web.listen-address=127.0.0.1:9090
```

```bash
(
  set -e
  prom_dir=/opt/prometheus-3.13.2.linux-amd64
  config_dir="$HOME/.config/cc4c/prometheus"
  "$prom_dir/promtool" check config "$config_dir/prometheus.yml"
  "$prom_dir/promtool" check rules "$config_dir/rules/cc4c-alerts.yml"
  "$prom_dir/prometheus" \
    --config.file="$config_dir/prometheus.yml" \
    --storage.tsdb.path="$HOME/.local/share/cc4c/prometheus-data" \
    --web.listen-address=127.0.0.1:9090
)
```

访问 `http://127.0.0.1:9090`。后端尚未启动时 backend target 暂时 DOWN 属于预期，三端启动后再核对抓取。生产暴露、HTTPS 和 Prometheus 自身 API 认证需要单独设计，不在本机教程中放开管理端监听。

### 6. Windows：三个前台终端启动

打开三个独立 **PowerShell 7** 终端。下例仓库位于 `C:\projects\CC4C`，请统一替换为自己的 clone 路径。每个包裹命令只接收一个前台 Maven、Java 或 npm 命令，不支持把多条命令、管道或重定向塞入 `-Command`。

**终端一：后端。**替换 JDK 路径，确保前述全部配置已经填写；`ConfirmDatabase` 必须精确等于 JDBC URL 中的库名。

```powershell
Set-Location -LiteralPath 'C:\projects\CC4C\backend'
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$previousMavenArgs = $env:MAVEN_ARGS
try {
    $env:JAVA_HOME = 'C:\tools\jdk-21'
    $env:PATH = "$env:JAVA_HOME\bin;$previousPath"
    $env:MAVEN_ARGS = ''
    & ..\infrastructure\host\with-app-environment.ps1 `
        -Application Backend -ConfirmDatabase cc4c_runtime `
        -Command { mvn spring-boot:run }
    if ($LASTEXITCODE -ne 0) { throw 'Backend command failed' }
}
finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    $env:MAVEN_ARGS = $previousMavenArgs
}
```

需要使用已构建 JAR 时，在上面同一包裹调用中把 `-Command { mvn spring-boot:run }` 换成 `-Command { java -jar target/cc4c-6.0.0-SNAPSHOT.jar }`，不同时运行两个后端入口。

已有 Maven 运行依赖时可改为 `-Command { mvn -o spring-boot:run }`。生产打包缓存不保证包含 `spring-boot:run` 所需的全部插件依赖，离线缺失时应明确补齐依赖或使用已构建 JAR。

**终端二：业务前端。**

```powershell
Set-Location -LiteralPath 'C:\projects\CC4C\frontend'
& ..\infrastructure\host\with-app-environment.ps1 `
    -Application Frontend `
    -Command { npm run dev -- --host localhost --port 5173 --strictPort }
if ($LASTEXITCODE -ne 0) { throw 'Frontend command failed' }
```

**终端三：观测前端。**

```powershell
Set-Location -LiteralPath 'C:\projects\CC4C\observability'
& ..\infrastructure\host\with-app-environment.ps1 `
    -Application Observability `
    -Command { npm run dev -- --host localhost --port 5174 --strictPort }
if ($LASTEXITCODE -ne 0) { throw 'Observability command failed' }
```

包裹脚本会检查应用目录、配置键和数据库确认值，在退出时恢复其调整过的环境变量。它不负责启动 MySQL、Redis、RabbitMQ 或 Prometheus，也不写入后台 PID 记录。

### 7. Linux：Bash 加载配置与前台启动

现有 PowerShell 主机脚本使用 Windows 绝对路径和进程检查，**不能直接在 Linux 复用**。下面函数仅是 README 中的 Bash 示例，不需要修改项目文件。

每个应用终端先进入仓库根目录，执行以下命令进入干净的临时 Bash；退出该 shell 即返回原来的终端环境。`JAVA_HOME`、PATH 应已按安装步骤设置，仓库路径按实际修改：

```bash
cd "$HOME/projects/CC4C"
env -i HOME="$HOME" PATH="$PATH" JAVA_HOME="$JAVA_HOME" \
  LANG=C.UTF-8 bash --noprofile --norc
```

在**每个临时 Bash** 中粘贴一次以下函数。它用公开模板限定变量名，按第一个 `=` 拆分，拒绝缺项、重复项、占位符及文件链接，不执行配置里的表达式。后端业务规则仍由应用检查，不能将此函数视为 Windows 全部预检的替代品。

```bash
CC4C_REPO="$(pwd -P)"

cc4c_read_env() {
  local app="$1" file line key value expected
  local -A allowed=() seen=()
  CC4C_VALUES=()
  for file in "$CC4C_REPO/$app/.env.example" "$CC4C_REPO/$app/.env.local"; do
    [[ -f "$file" && ! -L "$file" ]] || { echo "Missing or linked environment file" >&2; return 1; }
    [[ "$(realpath -e -- "$file")" == "$(realpath -ms -- "$file")" ]] || return 1
    [[ "$(stat -c %h -- "$file")" == 1 ]] || return 1
  done
  while IFS= read -r line || [[ -n "$line" ]]; do
    line=${line%$'\r'}
    [[ -z ${line//[[:space:]]/} || "$line" =~ ^[[:space:]]*"#" ]] && continue
    key=${line%%=*}
    [[ "$line" == *=* && "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || return 1
    allowed["$key"]=1
  done < "$CC4C_REPO/$app/.env.example"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line=${line%$'\r'}
    [[ -z ${line//[[:space:]]/} || "$line" =~ ^[[:space:]]*"#" ]] && continue
    key=${line%%=*}
    value=${line#*=}
    [[ "$line" == *=* && "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || return 1
    [[ ${allowed[$key]+present} && ! ${seen[$key]+present} ]] || {
      echo "Unknown or duplicate environment key" >&2; return 1;
    }
    [[ "$value" != *REPLACE_* ]] || { echo "Replace all placeholders first" >&2; return 1; }
    seen["$key"]=1
    CC4C_VALUES["$key"]="$value"
  done < "$CC4C_REPO/$app/.env.local"
  for expected in "${!allowed[@]}"; do
    [[ ${seen[$expected]+present} ]] || { echo "Missing environment key" >&2; return 1; }
  done
}

cc4c_run() (
  set -e
  local app="$1" confirm key blog_root avatar_root extra
  local db_pattern='^jdbc:mysql://[^/]+/([A-Za-z0-9_]+)(\?.*)?$'
  local -A CC4C_VALUES=()
  shift
  case "$app" in
    backend)
      confirm="$1"; shift
      cc4c_read_env backend
      [[ "${CC4C_VALUES[CC4C_DB_URL]}" =~ $db_pattern ]] || exit 1
      [[ "${BASH_REMATCH[1]}" == "$confirm" ]] || {
        echo "Database confirmation does not match" >&2; exit 1;
      }
      for key in "${!CC4C_VALUES[@]}"; do export "$key=${CC4C_VALUES[$key]}"; done
      export SPRING_CONFIG_NAME=application SPRING_APPLICATION_NAME=CC4C
      export SPRING_CONFIG_LOCATION=classpath:/application.yml
      unset SPRING_CONFIG_ADDITIONAL_LOCATION SPRING_CONFIG_IMPORT
      ;;
    frontend|observability)
      for extra in .env .env.development .env.development.local .env.production .env.production.local; do
        [[ ! -e "$CC4C_REPO/$app/$extra" && ! -L "$CC4C_REPO/$app/$extra" ]] || {
          echo "Keep only the documented .env.local entry" >&2; exit 1;
        }
      done
      if [[ "$app" == frontend ]]; then
        cc4c_read_env backend
        cd "$CC4C_REPO/backend"
        [[ -n "${CC4C_VALUES[CC4C_SAVE_IMG_PATH]}" && -n "${CC4C_VALUES[CC4C_SAVE_AVATAR_PATH]}" ]] || exit 1
        blog_root="$(realpath -m -- "${CC4C_VALUES[CC4C_SAVE_IMG_PATH]}")"
        avatar_root="$(realpath -m -- "${CC4C_VALUES[CC4C_SAVE_AVATAR_PATH]}")"
        [[ "$blog_root" != / && "$avatar_root" != / && "$blog_root" != "$avatar_root" ]] || exit 1
        export CC4C_HOST_BLOG_IMG_ROOT="$blog_root" CC4C_HOST_AVATAR_ROOT="$avatar_root"
      fi
      cc4c_read_env "$app"
      [[ "${CC4C_VALUES[VITE_API_BASE_URL]}" == http://* || "${CC4C_VALUES[VITE_API_BASE_URL]}" == https://* ]] || exit 1
      export VITE_API_BASE_URL="${CC4C_VALUES[VITE_API_BASE_URL]}"
      ;;
    *) echo "Use backend, frontend or observability" >&2; exit 1 ;;
  esac
  unset CC4C_VALUES
  cd "$CC4C_REPO/$app"
  "$@"
)
```

函数中的环境赋值发生在子 shell；不要向该临时 Bash 另外导入后端秘密或其他 `VITE_` 变量。业务前端只收到公开 API 和两个非 `VITE_` 上传根变量，观测端只收到公开 API；两端不会接收解析过程中读取的后端配置值。

**终端一：**先启动后端，确认数据库名与配置相同：

```bash
cc4c_run backend cc4c_runtime mvn spring-boot:run
```

使用已构建 JAR 时替换为以下命令，不与 Maven 入口同时运行：

```bash
cc4c_run backend cc4c_runtime java -jar target/cc4c-6.0.0-SNAPSHOT.jar
```

**终端二、三：**各自完成前述临时 Bash 与函数准备，再分别执行：

```bash
# 终端二
cc4c_run frontend npm run dev -- --host localhost --port 5173 --strictPort
```

```bash
# 终端三
cc4c_run observability npm run dev -- --host localhost --port 5174 --strictPort
```

上传磁盘路径按 backend 目录解析。上传目录由应用正常使用时创建，不能将其指向文件系统根目录或链接；图片通过业务 Vite 的 `/blogImg/`、`/avatar/` 映射访问。该插件仅在开发服务启用，`npm run preview` 或直接托管 `dist` 不会自动提供这套上传映射。

### 8. 初始化完成后：管理员与普通用户

首次后端启动后，确认 Flyway 已完成 V1–V7、公开健康接口正常。此时课程可以直接浏览；博客和用户资料不会凭空生成，也没有默认管理员密码。

**只在新环境且尚无有效管理员时**引导首个管理员。手工准备仓库外的 `admin-password.txt`，密码为 8–64 个字符且 UTF-8 不超过 72 字节。示例 `1000001` 是可自行选择的七位管理员 ID，不是预置账号。

Windows：在 Java 21 环境、仓库根目录执行：

```powershell
.\backend\scripts\bootstrap-admin.ps1 `
    -AdminId 1000001 -ConfirmDatabase cc4c_runtime `
    -PasswordFile "$HOME\CC4C-local\admin-password.txt"
if ($LASTEXITCODE -ne 0) { throw 'Administrator bootstrap failed' }
```

Linux：在另一个按第 7 节准备好函数的临时 Bash 中执行；密码文件先限定为当前用户可读：

```bash
chmod 600 "$HOME/.config/cc4c/admin-password.txt"
CC4C_ADMIN_BOOTSTRAP_ID=1000001 \
CC4C_ADMIN_BOOTSTRAP_CONFIRM_DATABASE=cc4c_runtime \
CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE="$HOME/.config/cc4c/admin-password.txt" \
  cc4c_run backend cc4c_runtime java -jar target/cc4c-6.0.0-SNAPSHOT-admin-bootstrap.jar
```

工具只处理明确确认的数据库中的首个管理员，不是重置密码入口。已有其他有效管理员或账号冲突时应停止排查，不删除历史账户重来。

普通用户在业务站点注册，使用自己的邮箱收取验证码；管理员通过管理员入口登录。观测端使用 `CC4C_OBSERVABILITY_USERNAME` 和 `portal-password.txt` 对应的原密码，不使用管理员账号。

#### 恢复自己的当前版本数据（可选）

全新复现无需导入 SQL 文件，Flyway 已完成初始化。只有迁移自己的现有环境时，才使用可信的**当前版本备份**恢复到另一个新建数据库。先在数据库管理员客户端执行；同名目标已存在时停止，不覆盖：

```sql
CREATE DATABASE cc4c_restore
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON cc4c_restore.* TO 'cc4c_runtime_user'@'127.0.0.1';
```

先确认备份包含当前表结构、数据与 `flyway_schema_history`，且不含切换回原库的 `USE`、`CREATE DATABASE` 或删库命令；若来源、版本不明，先按[数据库维护指南](docs/project-guide.md#数据库维护)处理。不要将旧版本的历史 SQL 与当前迁移混合。

Windows 使用 MySQL 客户端的 `SOURCE`，避免 PowerShell 文本管道改变 SQL 编码：

```powershell
mysql -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 cc4c_restore
```

进入该客户端后执行，替换为自己的备份路径：

```sql
SOURCE C:/backups/cc4c-current.sql;
```

Linux 可使用标准输入重定向：

```bash
mysql -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 cc4c_restore \
  < "$HOME/backups/cc4c-current.sql"
```

恢复完成后给目标库配置专用应用权限，修改后端 JDBC URL，并将启动确认参数同时改为 `cc4c_restore`。SQL 备份不包含上传文件、消息密钥或 Redis 会话；相关资料需按自己的迁移方案保留，不能把导入 SQL 等同于完整环境恢复。

### 9. 访问、检查与停止

| 入口 | 地址／预期 |
| --- | --- |
| 业务前端 | `http://localhost:5173`，可浏览课程、注册和登录 |
| 观测前端 | `http://localhost:5174`，独立登录后查看总览和 Dashboard |
| API | `http://localhost:4080`，不要求根路径返回网页 |
| 公开健康 | 管理端的 `/actuator/health`、`/actuator/health/liveness`、`/actuator/health/readiness` 均返回 `UP` |
| Prometheus | `http://127.0.0.1:9090`，Targets 中 backend／rabbitmq 抓取成功 |
| RabbitMQ 管理 | `http://localhost:15672`，仅使用为管理目的单独授权的账户 |

Windows 在单独终端检查三个健康接口：

```powershell
foreach ($endpoint in @('health', 'health/liveness', 'health/readiness')) {
    $result = Invoke-RestMethod "http://127.0.0.1:4081/actuator/$endpoint"
    if ($result.status -cne 'UP') { throw "$endpoint is not UP" }
}
```

Linux 使用：

```bash
for endpoint in health health/liveness health/readiness; do
  curl --fail --silent --show-error "http://127.0.0.1:4081/actuator/$endpoint" || break
  printf '\n'
done
```

Prometheus 页面查询 `up{job="cc4c-backend"}` 和 `up{job="cc4c-rabbitmq"}`，预期为 1；`up=1` 只说明目标抓取成功，部分面板仍需要对应业务活动才有样本。Windows 还可从仓库根目录运行现有只读检查：

```powershell
.\infrastructure\prometheus\check-prometheus.ps1 `
    -PromtoolPath 'C:\tools\prometheus-3.13.2.windows-amd64\promtool.exe' `
    -RequireBackendScrape
```

前台模式按 **观测端 → 业务前端 → 后端** 顺序，在各自终端按 `Ctrl+C`。Linux 再执行 `exit` 离开临时 Bash。Prometheus 如也需要停止，在它自己的终端按 `Ctrl+C`；三个应用的停止操作不会停止其他中间件。

#### Windows 可选整栈脚本

现有脚本适合工具链、配置、JAR、两端依赖及外部服务全部准备完毕的环境。先确保没有前台应用占用端口，在 **Java 21 / PowerShell 7、仓库根目录**运行：

```powershell
.\infrastructure\host\start-host-stack.ps1 -ConfirmDatabase cc4c_runtime
if ($LASTEXITCODE -ne 0) { throw 'Host stack start failed' }
.\infrastructure\host\health-host-stack.ps1
```

对应停止入口：

```powershell
.\infrastructure\host\stop-host-stack.ps1
```

这些脚本进行预检、后台启动和进程身份记录，在项目忽略的 `temp` 内保存状态及运行输出；停止仅针对记录匹配的本次应用，不按进程名批量结束，也不管理中间件。前台包裹命令不生成这些记录，不能用整栈停止脚本代替前台终端的 `Ctrl+C`。完整行为见[配置与本机运行](docs/project-guide.md#配置与本机运行)。

#### 常见问题

| 现象 | 首先检查 |
| --- | --- |
| `#requires` 提示版本不匹配 | 运行 `pwsh` 并检查版本；Windows PowerShell 5.1 不满足要求 |
| Maven 使用 Java 17 或 enforcer 失败 | 查看 `mvn --version`，校正当前终端 JAVA_HOME 与 PATH，而不仅是系统安装列表 |
| Redis `Connection refused` | 检查 Redis／WSL 服务是否启动、6379 是否可达、连接地址是否正确；不要清空 Redis |
| RabbitMQ 认证／权限失败 | 核对应用账号、URL 编码、vhost 名称和该 vhost 的权限；不使用 guest 代替专用账号 |
| 无法收取验证码／审核邮件 | 核对 SMTP 授权码、SSL／STARTTLS、端口与收件人；观察自己的服务商投递结果 |
| 页面 `Network Error` | 先检查后端健康，再检查公开 API 地址、端口和精确 Origin；不要把前端页面可打开等同于 API 已启动 |
| `strictPort` 失败 | 查清占用来源；停止自己确认的旧进程，不自动换端口或按名称杀进程 |
| 上传图片无法显示 | 磁盘路径必须相对 backend 正确解析；通过包裹／Bash 入口注入两个上传根，使用业务 Vite 开发服务 |
| Prometheus target DOWN | 核对 4081／15692、管理密码与监控密码、配置占位符；BCrypt 摘要不能作为抓取原密码 |
| 面板显示无数据或时间范围截断 | 核对 target 和环境／vhost／namespace 标签，再查看业务是否产生样本；界面有查询范围限制 |
| 管理员引导拒绝执行 | 确认七位 ID、目标数据库和已有管理员状态；该工具不覆盖现有账号 |

更多实现与维护说明见[项目指南](docs/project-guide.md)，历次验证结果与限制见[迭代总结](docs/iteration-summary.md)。

## 性能简介

项目保留了缓存对照、Gatling 负载、分页查询与故障演练的历史证据。以下只摘录 **V3 原始缓存实验（`bc7dcf8`）**的同机受控结果：九类公开读取目标，三轮热路径测量，每轮 3,000 请求、并发上限 16，延迟和吞吐取三轮中位数。

| 指标 | 无缓存基线 | 热缓存 |
| --- | ---: | ---: |
| p95 | 182.514 ms | 5.177 ms |
| 吞吐 | 464.458 req/s | 4,633.079 req/s |
| 测量阶段 MyBatis SELECT | 10,995 | 0 |

该组实验 HTTP 错误为 0，热缓存命中率为 100%；它说明特定请求组合下缓存对数据库访问和延迟的影响。**这不是 V6 本轮压测或生产容量承诺**，不同主机、数据、负载与冷缓存条件不能直接类比。原始逐轮文件未提交，当前仓库也不再包含历史性能执行工具。

实验方法、环境、其他重跑结果与证据缺失说明见[历史性能汇总](docs/performance-history.md#3-缓存基准)。

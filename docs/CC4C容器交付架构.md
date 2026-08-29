# CC4C 容器交付架构

## 运行拓扑

```mermaid
flowchart LR
    Browser[浏览器] -->|127.0.0.1:5173| Frontend[非 root Nginx / Vue SPA]
    Browser -->|127.0.0.1:4080| Backend[非 root Spring Boot]
    Browser -->|127.0.0.1:8025| Mailpit[Mailpit UI]
    Browser -->|127.0.0.1:3000| Grafana[Grafana]
    Browser -->|127.0.0.1:9090| Prometheus[Prometheus]

    subgraph AppNet[internal app network]
        Backend --> MySQL[(MySQL 8.4)]
        Backend --> SecurityRedis[(安全 Redis)]
        Backend --> CacheRedis[(缓存 Redis)]
        Backend --> Rabbit[(RabbitMQ quorum queues)]
        Backend --> Mailpit
        RabbitInit[rabbit-init] --> Rabbit
    end

    subgraph ObsNet[internal observability network]
        Prometheus -->|Basic / actuator| Backend
        Prometheus -->|只读监控账号| Rabbit
        Grafana --> Prometheus
    end

    Secrets[Compose secrets] -.只读挂载.-> Backend
    Secrets -.只读挂载.-> MySQL
    Secrets -.只读挂载.-> SecurityRedis
    Secrets -.只读挂载.-> CacheRedis
    Secrets -.只读挂载.-> Rabbit
    Secrets -.只读挂载.-> Grafana

    DBVol[(mysql_data)] --- MySQL
    RabbitVol[(rabbitmq_data)] --- Rabbit
    UploadVol[(blog/avatar uploads)] --- Backend
    UploadVol -.只读.-> Frontend
    PromVol[(prometheus_data)] --- Prometheus
    GrafanaVol[(grafana_data)] --- Grafana
```

宿主访问服务各自连接一个关闭 masquerade 的独占桥接网络；MySQL、Redis、AMQP 和 Rabbit 指标端口没有宿主映射。可选外部 SMTP 覆盖只向后端附加 egress 网络。

## 测试与交付拓扑

```mermaid
flowchart LR
    Source[Git checkout] --> BackendGate[Java 21 / Maven verify]
    BackendGate --> TC[Testcontainers: MySQL + 2 Redis + RabbitMQ]
    Source --> FrontGate[Node 24 / npm ci / audit / build]
    Source --> ConfigGate[Compose + Prometheus + Grafana + OpenAPI]
    Source --> Scan[Trivy / Dependency Review]

    BackendGate --> Smoke[amd64 Compose smoke]
    FrontGate --> Smoke
    ConfigGate --> Smoke
    Scan --> Smoke

    Tag[SemVer tag] --> Quality[重跑全部质量门禁]
    Quality --> Perf[隔离容器性能 profile]
    Perf --> Buildx[Buildx amd64 + arm64]
    Buildx --> GHCR[GHCR 不可变 SemVer/SHA 标签]
    Buildx --> Evidence[SBOM + provenance + attestation]
```

标准测试不连接本机数据库或 Compose 卷。GitHub Actions 的发布定义不代表已经发布；只有经授权推送严格 SemVer 标签并获得成功的远程工作流和 registry digest，才构成发布事实。

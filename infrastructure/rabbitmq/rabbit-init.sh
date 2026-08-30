#!/usr/bin/env bash
# 运行前提：仅在隔离 RabbitMQ 初始化容器中运行，凭据来自 /run/secrets。
# 破坏性边界：只初始化显式目标用户和权限，不删除队列、卷、数据库或上传文件。
# 失败恢复：任一初始化命令失败立即退出，保留输出并由隔离编排器处理。
# 退出码：初始化成功返回 0，凭据或 RabbitMQ 操作失败返回非零码。

set -euo pipefail
umask 077

read_secret() {
  local file_path="$1"
  if [[ ! -s "$file_path" ]]; then
    echo "Required RabbitMQ initialization secret is missing: $file_path" >&2
    exit 78
  fi
  tr -d '\r\n' < "$file_path"
}

export RABBITMQADMIN_NON_INTERACTIVE_MODE=true

mkdir -p /run/cc4c
bootstrap_password="$(read_secret /run/secrets/rabbit_bootstrap_password)"
app_password="$(read_secret /run/secrets/rabbit_app_password)"
monitor_password="$(read_secret /run/secrets/rabbit_monitor_password)"
cat > /run/cc4c/rabbitmqadmin.toml <<EOF
[bootstrap]
hostname = "rabbitmq"
port = 15672
username = "cc4c_bootstrap"
password = "$bootstrap_password"
vhost = "cc4c"

[application]
hostname = "rabbitmq"
port = 15672
username = "cc4c_app"
password = "$app_password"
vhost = "cc4c"

[monitor]
hostname = "rabbitmq"
port = 15672
username = "cc4c_monitor"
password = "$monitor_password"
vhost = "cc4c"
EOF
chmod 0600 /run/cc4c/rabbitmqadmin.toml
unset bootstrap_password app_password monitor_password

for attempt in $(seq 1 10); do
  if rabbitmqadmin --config /run/cc4c/rabbitmqadmin.toml --node bootstrap --quiet vhosts list >/dev/null; then
    app_password_hash="$(escript /opt/cc4c/rabbit-password-hash.escript /run/secrets/rabbit_app_password)"
    monitor_password_hash="$(escript /opt/cc4c/rabbit-password-hash.escript /run/secrets/rabbit_monitor_password)"
    cat > /run/cc4c/rabbit-application-definitions.json <<EOF
{
  "users": [
    {
      "name": "cc4c_app",
      "password_hash": "$app_password_hash",
      "hashing_algorithm": "rabbit_password_hashing_sha256",
      "tags": []
    },
    {
      "name": "cc4c_monitor",
      "password_hash": "$monitor_password_hash",
      "hashing_algorithm": "rabbit_password_hashing_sha256",
      "tags": ["monitoring"]
    }
  ],
  "permissions": [
    {
      "user": "cc4c_app",
      "vhost": "cc4c",
      "configure": "^cc4c\\\\.v3\\\\.messaging\\\\.(local|perf)\\\\..*$",
      "write": "^cc4c\\\\.v3\\\\.messaging\\\\.(local|perf)\\\\..*$",
      "read": "^cc4c\\\\.v3\\\\.messaging\\\\.(local|perf)\\\\..*$"
    },
    {
      "user": "cc4c_monitor",
      "vhost": "cc4c",
      "configure": "^$",
      "write": "^$",
      "read": ".*"
    }
  ]
}
EOF
    unset app_password_hash monitor_password_hash
    rabbitmqadmin --config /run/cc4c/rabbitmqadmin.toml --node bootstrap --quiet \
      definitions import --file /run/cc4c/rabbit-application-definitions.json >/dev/null

    if ! rabbitmqadmin --config /run/cc4c/rabbitmqadmin.toml --node monitor --quiet \
      vhosts list >/dev/null; then
      echo 'RabbitMQ monitoring account verification failed.' >&2
      exit 1
    fi

    rabbitmqadmin --config /run/cc4c/rabbitmqadmin.toml --node bootstrap --quiet \
      users delete --name guest --idempotently >/dev/null 2>&1 || true
    rabbitmqadmin --config /run/cc4c/rabbitmqadmin.toml --node bootstrap --quiet \
      users delete --name cc4c_bootstrap --idempotently >/dev/null
    exit 0
  fi
  if rabbitmqadmin --config /run/cc4c/rabbitmqadmin.toml --node monitor --quiet \
    vhosts list >/dev/null 2>&1; then
    exit 0
  fi
  sleep 1
done

if rabbitmqadmin --config /run/cc4c/rabbitmqadmin.toml --node monitor --quiet \
  vhosts list >/dev/null 2>&1; then
  exit 0
fi

echo 'RabbitMQ initialization could not authenticate with bootstrap or application credentials.' >&2
exit 1

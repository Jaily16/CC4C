#!/usr/bin/env bash
# 运行前提：仅由隔离的 RabbitMQ 容器调用，凭据只能来自容器 secret 挂载。
# 破坏性边界：只配置当前 RabbitMQ 实例，不清空队列、不删除卷、不触碰其他项目数据。
# 失败恢复：配置或凭据失败立即退出，保留容器日志供编排器按项目回滚。
# 退出码：成功透传 RabbitMQ 进程退出码；初始化失败返回非零码。

set -euo pipefail
umask 077

read_secret() {
  local file_path="$1"
  if [[ ! -s "$file_path" ]]; then
    echo "Required RabbitMQ secret is missing: $file_path" >&2
    exit 78
  fi
  tr -d '\r\n' < "$file_path"
}

export RABBITMQ_ERLANG_COOKIE="$(read_secret /run/secrets/rabbit_erlang_cookie)"
bootstrap_password_hash="$(escript /opt/cc4c/rabbit-password-hash.escript /run/secrets/rabbit_bootstrap_password)"

mkdir -p /run/cc4c
cp /opt/cc4c/rabbitmq.conf.template /run/cc4c/rabbitmq.conf
export RABBITMQ_CONFIG_FILE=/run/cc4c/rabbitmq.conf

if ! find /var/lib/rabbitmq/mnesia -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null | grep -q .; then
  cat > /run/cc4c/rabbit-definitions.json <<EOF
{
  "users": [
    {
      "name": "cc4c_bootstrap",
      "password_hash": "$bootstrap_password_hash",
      "hashing_algorithm": "rabbit_password_hashing_sha256",
      "tags": ["administrator"]
    }
  ],
  "vhosts": [{"name": "cc4c"}],
  "permissions": [
    {
      "user": "cc4c_bootstrap",
      "vhost": "cc4c",
      "configure": ".*",
      "write": ".*",
      "read": ".*"
    }
  ]
}
EOF
  cat >> /run/cc4c/rabbitmq.conf <<'EOF'
definitions.import_backend = local_filesystem
definitions.local.path = /run/cc4c/rabbit-definitions.json
EOF
  chown rabbitmq:rabbitmq /run/cc4c/rabbit-definitions.json
  chmod 0400 /run/cc4c/rabbit-definitions.json
fi

chown rabbitmq:rabbitmq /run/cc4c /run/cc4c/rabbitmq.conf
chmod 0750 /run/cc4c
chmod 0440 /run/cc4c/rabbitmq.conf
unset bootstrap_password_hash

rabbitmq-plugins enable --offline rabbitmq_prometheus >/dev/null
chown rabbitmq:rabbitmq /etc/rabbitmq/enabled_plugins
chmod 0440 /etc/rabbitmq/enabled_plugins
exec docker-entrypoint.sh rabbitmq-server

#!/usr/bin/env bash
set -euo pipefail

read_required_secret() {
  local variable_name="$1"
  local file_path="$2"
  if [[ ! -s "$file_path" ]]; then
    echo "Required secret file is missing: $file_path" >&2
    exit 78
  fi
  printf -v "$variable_name" '%s' "$(tr -d '\r\n' < "$file_path")"
  export "$variable_name"
}

read_optional_secret() {
  local variable_name="$1"
  local file_path="$2"
  if [[ -s "$file_path" ]]; then
    printf -v "$variable_name" '%s' "$(tr -d '\r\n' < "$file_path")"
    export "$variable_name"
  fi
}

read_required_secret CC4C_DB_PASSWORD /run/secrets/mysql_app_password

if [[ "${CC4C_LAUNCH_MODE:-web}" == "admin-bootstrap" ]]; then
  export CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE=/run/secrets/admin_bootstrap_password
  exec java -jar /app/cc4c-admin-bootstrap.jar \
    --spring.config.name=application-example
fi

read_required_secret CC4C_REDIS_SECURITY_PASSWORD /run/secrets/redis_security_password
read_required_secret CC4C_REDIS_CACHE_PASSWORD /run/secrets/redis_cache_password
read_required_secret CC4C_RABBITMQ_PASSWORD /run/secrets/rabbit_app_password
read_required_secret CC4C_SECURITY_PEPPER /run/secrets/security_pepper
read_required_secret CC4C_MESSAGING_PAYLOAD_KEY /run/secrets/messaging_payload_key
read_required_secret CC4C_MANAGEMENT_PASSWORD /run/secrets/management_password

export CC4C_REDIS_URL="redis://:${CC4C_REDIS_SECURITY_PASSWORD}@redis-security:6379"
export CC4C_CACHE_REDIS_URL="redis://:${CC4C_REDIS_CACHE_PASSWORD}@redis-cache:6379"
export CC4C_RABBITMQ_URL="amqp://cc4c_app:${CC4C_RABBITMQ_PASSWORD}@rabbitmq:5672/cc4c"
export CC4C_MESSAGING_PAYLOAD_KEYS="local-v1=${CC4C_MESSAGING_PAYLOAD_KEY}"

read_optional_secret CC4C_MAIL_USERNAME /run/secrets/smtp_username
read_optional_secret CC4C_MAIL_PASSWORD /run/secrets/smtp_password

exec java -jar /app/cc4c.jar \
  --spring.config.name=application-example

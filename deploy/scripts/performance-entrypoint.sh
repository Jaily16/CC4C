#!/usr/bin/env bash
set -euo pipefail

read_secret() {
  local file_path="$1"
  if [[ ! -s "$file_path" ]]; then
    echo "Required performance secret is missing" >&2
    exit 78
  fi
  tr -d '\r\n' < "$file_path"
}

export CC4C_PERF_DB_PASSWORD="$(read_secret /run/secrets/mysql_app_password)"
export CC4C_PERF_USER_PASSWORD="$(read_secret /run/secrets/admin_bootstrap_password)"
cache_password="$(read_secret /run/secrets/redis_cache_password)"
export CC4C_PERF_CACHE_REDIS_URL="redis://:${cache_password}@redis-cache:6379"
unset cache_password

exec "$@"

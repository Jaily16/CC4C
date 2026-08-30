#!/usr/bin/env bash
# 运行前提：仅由隔离性能容器调用，所有凭据从 /run/secrets 读取且不打印内容。
# 破坏性边界：只运行性能工具进程，不操作宿主机卷、业务数据库、Redis 或 RabbitMQ。
# 失败恢复：启动或配置失败立即退出，保留容器输出供隔离任务回收。
# 退出码：透传性能工具退出码；缺少秘密或参数错误返回非零码。

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

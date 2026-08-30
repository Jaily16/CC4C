#!/usr/bin/env bash
# 运行前提：仅由隔离性能容器调用，秘密只能来自容器 secret 挂载。
# 破坏性边界：只初始化本次性能资源，不清空现有数据库、卷、队列或上传文件。
# 失败恢复：任一前置条件失败立即退出，保留容器日志并由隔离编排器处理回滚。
# 退出码：成功返回 0，缺少秘密或初始化失败返回非零码。

set -euo pipefail

root_password="$(tr -d '\r\n' < /run/secrets/mysql_root_password)"
if [[ -z "$root_password" ]]; then
  echo 'Required MySQL root secret is missing' >&2
  exit 78
fi

export MYSQL_PWD="$root_password"
for attempt in $(seq 1 30); do
  if mysqladmin --protocol=tcp -h mysql -uroot ping --silent >/dev/null 2>&1; then
    mysql --protocol=tcp -h mysql -uroot <<'SQL'
CREATE DATABASE IF NOT EXISTS cc4c_perf_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON cc4c_perf_test.* TO 'cc4c'@'%';
SQL
    unset MYSQL_PWD root_password
    exit 0
  fi
  sleep 2
done

echo 'Performance database initialization timed out' >&2
exit 1

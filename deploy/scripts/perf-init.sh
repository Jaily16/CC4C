#!/usr/bin/env bash
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

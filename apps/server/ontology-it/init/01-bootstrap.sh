#!/usr/bin/env bash
set -euo pipefail

# 仅为一次性测试容器创建隔离 schema 和非超级用户运行账号。
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=runtime_password="$ONTOLOGY_IT_RUNTIME_PASSWORD" <<'SQL'
CREATE SCHEMA ontology;
CREATE ROLE ontology_it_runtime
  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
  PASSWORD :'runtime_password';
GRANT CONNECT, CREATE ON DATABASE ontology_multiuser_dev_20261020 TO ontology_it_runtime;
SQL

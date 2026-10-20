#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yaml"
PROJECT_NAME="ontology-app-it"

# 每次测试使用随机临时凭据；只绑定回环地址，不复用任何本机业务库凭据。
export ONTOLOGY_IT_ADMIN_PASSWORD="$(openssl rand -hex 32)"
export ONTOLOGY_IT_RUNTIME_PASSWORD="$(openssl rand -hex 32)"
export ONTOLOGY_IT_POSTGRES_PORT="${ONTOLOGY_IT_POSTGRES_PORT:-25433}"

if ! docker image inspect ontology-postgres-age-local:16 >/dev/null 2>&1; then
  echo "缺少本机 AGE 测试镜像 ontology-postgres-age-local:16" >&2
  exit 1
fi

cleanup() {
  local result=$?
  if [[ $result -ne 0 ]]; then
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --no-color || true
  fi
  docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down --volumes --remove-orphans || true
  return "$result"
}
trap cleanup EXIT

docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --wait

# 数据库用户名和密码仅传给本次测试进程，不写入仓库或日志。
export ONTOLOGY_IT_JDBC_URL="jdbc:postgresql://127.0.0.1:${ONTOLOGY_IT_POSTGRES_PORT}/ontology_multiuser_dev_20261020"
export JDBC_USER="ontology_it_runtime"
export JDBC_PASSWORD="$ONTOLOGY_IT_RUNTIME_PASSWORD"

mvn -f "$SERVER_DIR/pom.xml" -pl ontology-core -am \
  -Dtest=MultiuserDatabaseIntegrationTest,AgentOAuthAuthorizationIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

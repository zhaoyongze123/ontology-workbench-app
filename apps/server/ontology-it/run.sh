#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yaml"
PROJECT_NAME="ontology-app-it"
export ONTOLOGY_IT_IMAGE="${ONTOLOGY_IT_IMAGE:-ontology-postgres-age-it:16}"

# 每次测试使用随机临时凭据；只绑定回环地址，不复用任何本机业务库凭据。
export ONTOLOGY_IT_ADMIN_PASSWORD="$(openssl rand -hex 32)"
export ONTOLOGY_IT_RUNTIME_PASSWORD="$(openssl rand -hex 32)"
export ONTOLOGY_IT_MIGRATOR_PASSWORD="$(openssl rand -hex 32)"
export ONTOLOGY_IT_BOOTSTRAP_PASSWORD="$(openssl rand -hex 32)"
export ONTOLOGY_IT_POSTGRES_PORT="${ONTOLOGY_IT_POSTGRES_PORT:-25433}"

if ! docker image inspect "$ONTOLOGY_IT_IMAGE" >/dev/null 2>&1; then
  echo "正在构建固定 PostgreSQL/AGE/pgvector 测试镜像..."
  docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" build postgres
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

# 先以数据库管理员准备角色和对象 owner，再用独立迁移账号执行 Flyway。
export ONTOLOGY_IT_JDBC_URL="jdbc:postgresql://127.0.0.1:${ONTOLOGY_IT_POSTGRES_PORT}/ontology_multiuser_dev_20261020"
export ONTOLOGY_DB_USER="ontology_it_runtime"
export ONTOLOGY_DB_PASSWORD="$ONTOLOGY_IT_RUNTIME_PASSWORD"
export ONTOLOGY_MIGRATOR_DB_URL="$ONTOLOGY_IT_JDBC_URL"
export ONTOLOGY_MIGRATOR_USER="ontology_migrator"
export ONTOLOGY_MIGRATOR_PASSWORD="$ONTOLOGY_IT_MIGRATOR_PASSWORD"
export ONTOLOGY_BOOTSTRAP_DB_USER="ontology_bootstrap"
export ONTOLOGY_BOOTSTRAP_DB_PASSWORD="$ONTOLOGY_IT_BOOTSTRAP_PASSWORD"
export ONTOLOGY_IT_BOOTSTRAP_USER="$ONTOLOGY_BOOTSTRAP_DB_USER"
export ONTOLOGY_IT_BOOTSTRAP_PASSWORD="$ONTOLOGY_IT_BOOTSTRAP_PASSWORD"
IT_TEST_CLASSES="${ONTOLOGY_IT_TESTS:-MultiuserDatabaseIntegrationTest,AgentOAuthAuthorizationIntegrationTest,AccountLifecycleDatabaseIntegrationTest,SecurityBoundaryDatabaseIntegrationTest,DatabasePrivilegesIntegrationTest,BootstrapDatabaseIntegrationTest}"
PGPASSWORD="$ONTOLOGY_IT_ADMIN_PASSWORD" psql \
  -h 127.0.0.1 -p "$ONTOLOGY_IT_POSTGRES_PORT" -U ontology_it_admin \
  -d ontology_multiuser_dev_20261020 -X -v ON_ERROR_STOP=1 \
  -f "$SERVER_DIR/../../deploy/local/prepare-db-roles.sql"

# Flyway 插件只需要迁移资源；先把同一 Maven reactor 的模块安装到本地仓库，
# 避免单独调用插件时把尚未安装的 ontology-common 错误地当成中央仓库依赖。
mvn -f "$SERVER_DIR/pom.xml" -pl ontology-core -am -DskipTests install

mvn -f "$SERVER_DIR/ontology-core/pom.xml" flyway:migrate

PGPASSWORD="$ONTOLOGY_IT_ADMIN_PASSWORD" psql \
  -h 127.0.0.1 -p "$ONTOLOGY_IT_POSTGRES_PORT" -U ontology_it_admin \
  -d ontology_multiuser_dev_20261020 -X -v ON_ERROR_STOP=1 \
  -f "$SERVER_DIR/../../deploy/local/finalize-db-permissions.sql"

# 凭据只传给本次测试进程，不写入仓库或容器长期环境。
export JDBC_USER="ontology_it_runtime"
export JDBC_PASSWORD="$ONTOLOGY_IT_RUNTIME_PASSWORD"

mvn -f "$SERVER_DIR/pom.xml" -pl ontology-core -am \
  -Dtest="$IT_TEST_CLASSES" \
  -Dsurefire.failIfNoSpecifiedTests=false test

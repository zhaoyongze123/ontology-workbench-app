#!/bin/sh
set -eu

secret() {
  file=$1
  [ -s "$file" ] || { echo "required migration secret is missing: $file" >&2; exit 1; }
  cat "$file"
}

database=${DATABASE_NAME:-ontology}
case "$database" in
  ''|*[!A-Za-z0-9_]*) echo 'DATABASE_NAME 仅允许字母、数字和下划线' >&2; exit 2 ;;
esac
[ "${#database}" -le 63 ] || { echo 'DATABASE_NAME 不能超过 63 个字符' >&2; exit 2; }

export PGPASSWORD="$(secret /run/secrets/db_admin_password)"
export ONTOLOGY_DB_USER=ontology_runtime
export ONTOLOGY_DB_PASSWORD="$(secret /run/secrets/runtime_db_password)"
export ONTOLOGY_MIGRATOR_PASSWORD="$(secret /run/secrets/migrator_db_password)"
export ONTOLOGY_BOOTSTRAP_DB_USER=ontology_bootstrap
export ONTOLOGY_BOOTSTRAP_DB_PASSWORD="$(secret /run/secrets/bootstrap_db_password)"

psql_admin() {
  psql -h db -p 5432 -v ON_ERROR_STOP=1 -U ontology_admin -d "$database" "$@"
}

# prepare 脚本会临时授予迁移角色权限；无论迁移是否成功，都尝试撤回这些权限。
finish_migration() {
  status=$?
  trap - EXIT
  if [ "$cleanup_needed" -eq 1 ] && ! psql_admin --file /opt/ontology/migration/finalize-db-permissions.sql; then
    echo '数据库迁移权限收敛失败；请检查数据库角色状态后再启动 API。' >&2
    [ "$status" -ne 0 ] || status=1
  fi
  unset PGPASSWORD ONTOLOGY_DB_PASSWORD ONTOLOGY_MIGRATOR_PASSWORD ONTOLOGY_BOOTSTRAP_DB_PASSWORD
  exit "$status"
}

psql_admin --command 'SELECT 1' >/dev/null
cleanup_needed=1
trap finish_migration EXIT
psql_admin --file /opt/ontology/migration/prepare-db-roles.sql

echo 'Applying database migrations.'
FLYWAY_URL="jdbc:postgresql://db:5432/${database}?currentSchema=ontology" \
FLYWAY_USER=ontology_migrator \
FLYWAY_PASSWORD="$(secret /run/secrets/migrator_db_password)" \
  flyway \
    -locations=filesystem:/opt/ontology/flyway/sql \
    -schemas=ontology \
    -defaultSchema=ontology \
    -baselineOnMigrate=false \
    -validateOnMigrate=true \
    -cleanDisabled=true \
    migrate

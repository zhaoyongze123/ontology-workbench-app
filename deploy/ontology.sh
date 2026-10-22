#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEPLOY=$ROOT/deploy
PRIVATE=$DEPLOY/private
compose() {
  docker compose --project-directory "$DEPLOY" --env-file "$DEPLOY/.env" -f "$DEPLOY/compose.yaml" "$@"
}

usage() {
  cat <<'EOF'
用法: deploy/ontology.sh <init-config|pull|up|migrate|init-admin|status|logs|backup|restore|down|package-app>
EOF
}
need_config() {
  [ -f "$DEPLOY/.env" ] || { echo '请先运行 init-config 并编辑 deploy/.env' >&2; exit 2; }
  image_tag=$(sed -n 's/^IMAGE_TAG=//p' "$DEPLOY/.env" | tail -n 1)
  case "$image_tag" in ''|latest|replace-with-a-release-tag|*[!A-Za-z0-9_.-]*)
    echo '请在 deploy/.env 设置固定版本 IMAGE_TAG，不要使用 latest' >&2; exit 2;;
  esac
  origin=$(sed -n 's/^PUBLIC_ORIGIN=//p' "$DEPLOY/.env" | tail -n 1)
  case "$origin" in https://*) :;; *) echo 'PUBLIC_ORIGIN 必须是公开 HTTPS origin' >&2; exit 2;; esac
  if [ -f "$PRIVATE/api-config/ontology.security.public-origin" ] \
    && [ "$(cat "$PRIVATE/api-config/ontology.security.public-origin")" != "$origin" ]; then
    echo 'PUBLIC_ORIGIN 已变化；请重新运行 init-config 同步运行配置' >&2
    exit 2
  fi
}
db_admin_psql() {
  compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)" ONTOLOGY_DB_USER=ontology_runtime ONTOLOGY_DB_PASSWORD="$(cat /run/secrets/runtime_db_password)" ONTOLOGY_MIGRATOR_PASSWORD="$(cat /run/secrets/migrator_db_password)" ONTOLOGY_BOOTSTRAP_DB_USER=ontology_bootstrap ONTOLOGY_BOOTSTRAP_DB_PASSWORD="$(cat /run/secrets/bootstrap_db_password)"; exec psql -h 127.0.0.1 -v ON_ERROR_STOP=1 -U ontology_admin -d ontology' < "$1"
}
command=${1:-}
shift || true
case "$command" in
  init-config)
    mkdir -p "$PRIVATE/api-config" "$PRIVATE/bootstrap-config" "$PRIVATE/backups" "$PRIVATE/package-output"
    chmod 700 "$PRIVATE" "$PRIVATE/api-config" "$PRIVATE/bootstrap-config" "$PRIVATE/backups" "$PRIVATE/package-output"
    [ -f "$DEPLOY/.env" ] || cp "$DEPLOY/.env.example" "$DEPLOY/.env"
    chmod 600 "$DEPLOY/.env"
    origin=$(sed -n 's/^PUBLIC_ORIGIN=//p' "$DEPLOY/.env" | tail -n 1)
    case "$origin" in https://?*) :;; *) echo 'PUBLIC_ORIGIN 必须是公开 HTTPS origin' >&2; exit 2;; esac
    origin_host=${origin#https://}
    case "$origin_host" in ''|*/*|*\?*|*#*) echo 'PUBLIC_ORIGIN 不能包含路径、查询或片段' >&2; exit 2;; esac
    for name in db-admin-password runtime-db-password migrator-db-password bootstrap-db-password; do
      if [ ! -s "$PRIVATE/$name" ]; then
        value=$(docker run --rm busybox:1.36 sh -c 'head -c 32 /dev/urandom | od -An -tx1 | tr -d " \n"')
        umask 077; printf '%s' "$value" > "$PRIVATE/$name"
      fi
      chmod 600 "$PRIVATE/$name"
    done
    umask 077
    printf '%s' "jdbc:postgresql://db:5432/ontology?currentSchema=ontology" > "$PRIVATE/api-config/spring.datasource.url"
    printf '%s' ontology_runtime > "$PRIVATE/api-config/spring.datasource.username"
    cp "$PRIVATE/runtime-db-password" "$PRIVATE/api-config/spring.datasource.password"
    printf '%s' "$origin" > "$PRIVATE/api-config/ontology.security.public-origin"
    printf '%s' "$origin" > "$PRIVATE/api-config/ontology.security.trusted-origins"
    printf '%s' '172.30.0.10/32' > "$PRIVATE/api-config/ontology.security.trusted-proxies"
    printf '%s' 'false' > "$PRIVATE/api-config/spring.flyway.enabled"
    printf '%s' 'jdbc:postgresql://db:5432/ontology?currentSchema=ontology' > "$PRIVATE/bootstrap-config/spring.datasource.url"
    printf '%s' ontology_bootstrap > "$PRIVATE/bootstrap-config/spring.datasource.username"
    cp "$PRIVATE/bootstrap-db-password" "$PRIVATE/bootstrap-config/spring.datasource.password"
    chmod 600 "$PRIVATE"/api-config/* "$PRIVATE"/bootstrap-config/*
    printf '%s\n' '配置目录已初始化；请编辑 deploy/.env 的 PUBLIC_ORIGIN，并妥善保管 deploy/private/。'
    ;;
  pull) need_config; compose --profile tools pull ;;
  up) need_config; compose up -d db api web ;;
  migrate)
    need_config
    compose up -d db
    db_admin_psql "$DEPLOY/local/prepare-db-roles.sql"
    compose --profile tools run --rm migrate
    db_admin_psql "$DEPLOY/local/finalize-db-permissions.sql"
    ;;
  init-admin)
    need_config
    [ -s "$PRIVATE/bootstrap-admin.json" ] || { echo '请先按 bootstrap-admin.json 示例创建私密输入' >&2; exit 2; }
    compose --profile tools run --rm bootstrap
    ;;
  status) need_config; compose ps ;;
  logs) need_config; compose logs --tail=200 -f "${1:-web}" ;;
  backup)
    need_config
    destination=${1:-$PRIVATE/backups/ontology-$(date -u +%Y%m%dT%H%M%SZ).dump}
    umask 077
    compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; exec pg_dump -h 127.0.0.1 -Fc -U ontology_admin -d ontology' > "$destination"
    chmod 600 "$destination"
    printf '备份完成: %s\n' "$destination"
    ;;
  restore)
    need_config
    [ "$#" -eq 2 ] || { echo '用法: restore <备份文件> <全新目标数据库名>' >&2; exit 2; }
    backup=$1 target=$2
    case "$target" in ontology|postgres|template0|template1|'') echo '目标必须是独立的新数据库名' >&2; exit 2;; esac
    case "$target" in *[!a-zA-Z0-9_]* ) echo '目标数据库名只允许字母、数字和下划线' >&2; exit 2;; esac
    [ -f "$backup" ] || { echo '备份文件不存在' >&2; exit 2; }
    exists=$(compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; psql -h 127.0.0.1 -At -U ontology_admin -d postgres -c "SELECT 1 FROM pg_database WHERE datname = '\''$1'\''"' sh "$target")
    [ -z "$exists" ] || { echo '目标数据库已存在；恢复只允许新建独立目标' >&2; exit 2; }
    compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; createdb -h 127.0.0.1 -U ontology_admin "$1"' sh "$target"
    compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; exec pg_restore --exit-on-error -h 127.0.0.1 -U ontology_admin -d "$1"' sh "$target" < "$backup"
    ;;
  down) need_config; compose down ;;
  package-app)
    need_config
    [ -s "$PRIVATE/package.json" ] || { echo '缺少私密 App 配置 deploy/private/package.json' >&2; exit 2; }
    compose --profile tools run --rm package
    ;;
  *) usage; exit 2 ;;
esac

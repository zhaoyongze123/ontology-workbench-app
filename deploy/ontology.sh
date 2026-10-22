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
configured_database() {
  database=$(sed -n 's/^DATABASE_NAME=//p' "$DEPLOY/.env" | tail -n 1)
  database=${database:-ontology}
  case "$database" in ''|*[!A-Za-z0-9_]*)
    echo 'DATABASE_NAME 仅允许字母、数字和下划线' >&2; exit 2;;
  esac
  [ "${#database}" -le 63 ] || { echo 'DATABASE_NAME 不能超过 63 个字符' >&2; exit 2; }
  printf '%s' "$database"
}
configured_web_ip() {
  web_ip=$(sed -n 's/^WEB_BACKEND_IP=//p' "$DEPLOY/.env" | tail -n 1)
  web_ip=${web_ip:-10.203.35.10}
  if ! printf '%s\n' "$web_ip" | awk -F. 'NF == 4 { for (i=1; i<=4; i++) if ($i !~ /^[0-9]+$/ || $i < 0 || $i > 255) exit 1; exit 0 } { exit 1 }'; then
    echo 'WEB_BACKEND_IP 必须是有效 IPv4 地址' >&2; exit 2;
  fi
  printf '%s' "$web_ip"
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
  web_ip=$(configured_web_ip)
  if [ -f "$PRIVATE/api-config/ontology.security.trusted-proxies" ] \
    && [ "$(cat "$PRIVATE/api-config/ontology.security.trusted-proxies")" != "$web_ip/32" ]; then
    echo 'WEB_BACKEND_IP 已变化；请重新运行 init-config 同步代理信任配置' >&2
    exit 2
  fi
  database=$(configured_database)
  expected_jdbc="jdbc:postgresql://db:5432/$database?currentSchema=ontology"
  for config in "$PRIVATE/api-config/spring.datasource.url" "$PRIVATE/bootstrap-config/spring.datasource.url"; do
    if [ -f "$config" ] && [ "$(cat "$config")" != "$expected_jdbc" ]; then
      echo 'DATABASE_NAME 已变化；请运行 init-config 同步 JDBC 配置' >&2
      exit 2
    fi
  done
}
ensure_oidc_config() {
  oidc_enabled=$(cat "$PRIVATE/api-config/ontology.workbench.enabled" 2>/dev/null || printf 'false')
  case "$oidc_enabled" in
    true)
      [ -s "$PRIVATE/api-config/ontology.workbench.client-id" ] \
        && [ -s "$PRIVATE/api-config/ontology.workbench.client-secret" ] \
        || { echo 'Workbench OIDC 启用前，请在 deploy/private/api-config 中配置 client-id 和 client-secret' >&2; exit 2; }
      ;;
    false) : ;;
    *) echo 'ontology.workbench.enabled 必须为 true 或 false' >&2; exit 2 ;;
  esac
}
db_admin_psql() {
  database=$1 sql_file=$2
  compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)" ONTOLOGY_DB_USER=ontology_runtime ONTOLOGY_DB_PASSWORD="$(cat /run/secrets/runtime_db_password)" ONTOLOGY_MIGRATOR_PASSWORD="$(cat /run/secrets/migrator_db_password)" ONTOLOGY_BOOTSTRAP_DB_USER=ontology_bootstrap ONTOLOGY_BOOTSTRAP_DB_PASSWORD="$(cat /run/secrets/bootstrap_db_password)"; exec psql -h 127.0.0.1 -v ON_ERROR_STOP=1 -U ontology_admin -d "$1"' sh "$database" < "$sql_file"
}
command=${1:-}
shift || true
case "$command" in
  init-config)
    mkdir -p "$PRIVATE/api-config" "$PRIVATE/bootstrap-config" "$PRIVATE/bootstrap-input" "$PRIVATE/backups" "$PRIVATE/package-output"
    chmod 700 "$PRIVATE" "$PRIVATE/api-config" "$PRIVATE/bootstrap-config" "$PRIVATE/bootstrap-input" "$PRIVATE/backups" "$PRIVATE/package-output"
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
    database=$(configured_database)
    web_ip=$(configured_web_ip)
    printf '%s' "jdbc:postgresql://db:5432/$database?currentSchema=ontology" > "$PRIVATE/api-config/spring.datasource.url"
    printf '%s' ontology_runtime > "$PRIVATE/api-config/spring.datasource.username"
    cp "$PRIVATE/runtime-db-password" "$PRIVATE/api-config/spring.datasource.password"
    printf '%s' "$origin" > "$PRIVATE/api-config/ontology.security.public-origin"
    printf '%s' "$origin" > "$PRIVATE/api-config/ontology.security.trusted-origins"
    printf '%s/32' "$web_ip" > "$PRIVATE/api-config/ontology.security.trusted-proxies"
    printf '%s' 'false' > "$PRIVATE/api-config/spring.flyway.enabled"
    printf '%s' age > "$PRIVATE/api-config/graph.database.type"
    printf '%s' true > "$PRIVATE/api-config/graph.database.age.preloaded"
    oidc_enabled=$(sed -n 's/^WORKBENCH_OIDC_ENABLED=//p' "$DEPLOY/.env" | tail -n 1)
    oidc_enabled=${oidc_enabled:-false}
    case "$oidc_enabled" in true|false) :;; *) echo 'WORKBENCH_OIDC_ENABLED 必须为 true 或 false' >&2; exit 2;; esac
    printf '%s' "$oidc_enabled" > "$PRIVATE/api-config/ontology.workbench.enabled"
    printf '%s' 'https://app.workbench.aos.work' > "$PRIVATE/api-config/ontology.workbench.issuer"
    printf '%s' 'https://app.workbench.aos.work/oidc/authorize' > "$PRIVATE/api-config/ontology.workbench.authorization-endpoint"
    printf '%s' 'https://app.workbench.aos.work/basilcraw/api/oidc/token' > "$PRIVATE/api-config/ontology.workbench.token-endpoint"
    printf '%s' 'https://app.workbench.aos.work/basilcraw/api/oidc/jwks' > "$PRIVATE/api-config/ontology.workbench.jwks-uri"
    printf '%s' post > "$PRIVATE/api-config/ontology.workbench.client-auth-method"
    printf '%s/oidc/callback' "$origin" > "$PRIVATE/api-config/ontology.workbench.redirect-uri"
    printf '%s' "jdbc:postgresql://db:5432/$database?currentSchema=ontology" > "$PRIVATE/bootstrap-config/spring.datasource.url"
    printf '%s' ontology_bootstrap > "$PRIVATE/bootstrap-config/spring.datasource.username"
    cp "$PRIVATE/bootstrap-db-password" "$PRIVATE/bootstrap-config/spring.datasource.password"
    printf '%s' false > "$PRIVATE/bootstrap-config/spring.flyway.enabled"
    printf '%s' age > "$PRIVATE/bootstrap-config/graph.database.type"
    printf '%s' true > "$PRIVATE/bootstrap-config/graph.database.age.preloaded"
    chmod 600 "$PRIVATE"/api-config/* "$PRIVATE"/bootstrap-config/*
    printf '%s\n' '配置目录已初始化；请编辑 deploy/.env 的 PUBLIC_ORIGIN，并妥善保管 deploy/private/。'
    ;;
  pull) need_config; compose --profile tools pull ;;
  up)
    need_config
    ensure_oidc_config
    compose up -d db
    # init-config 会重写私有 JDBC 文件；重建 API 才会重新读取并切换目标库。
    compose up -d --force-recreate api web
    ;;
  migrate)
    need_config
    compose up -d --wait --wait-timeout 120 db
    database=$(configured_database)
    db_admin_psql "$database" "$DEPLOY/local/prepare-db-roles.sql"
    compose --profile tools run --rm migrate
    db_admin_psql "$database" "$DEPLOY/local/finalize-db-permissions.sql"
    ;;
  init-admin)
    need_config
    bootstrap_file=$PRIVATE/bootstrap-input/bootstrap-admin.json
    [ ! -L "$PRIVATE/bootstrap-input" ] \
      && [ -f "$bootstrap_file" ] && [ ! -L "$bootstrap_file" ] \
      || { echo '请将私密 bootstrap JSON 放入 deploy/private/bootstrap-input/bootstrap-admin.json' >&2; exit 2; }
    chmod 700 "$PRIVATE/bootstrap-input"
    chmod 600 "$bootstrap_file"
    compose --profile tools run --rm bootstrap
    ;;
  status) need_config; compose ps ;;
  logs) need_config; compose logs --tail=200 -f "${1:-web}" ;;
  backup)
    need_config
    destination=${1:-$PRIVATE/backups/ontology-$(date -u +%Y%m%dT%H%M%SZ).dump}
    [ ! -e "$destination" ] || { echo '目标备份文件已存在' >&2; exit 2; }
    destination_dir=$(dirname "$destination")
    [ -d "$destination_dir" ] || { echo '备份目标目录不存在' >&2; exit 2; }
    temporary="$destination.tmp.$$"
    [ ! -e "$temporary" ] || { echo '备份临时文件已存在' >&2; exit 2; }
    umask 077
    trap 'unlink "$temporary" 2>/dev/null || true' EXIT HUP INT TERM
    database=$(configured_database)
    compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; exec pg_dump -h 127.0.0.1 -Fc -U ontology_admin -d "$1"' sh "$database" > "$temporary"
    chmod 600 "$temporary"
    mv "$temporary" "$destination"
    trap - EXIT HUP INT TERM
    printf '备份完成: %s\n' "$destination"
    ;;
  restore)
    need_config
    [ "$#" -eq 2 ] || { echo '用法: restore <备份文件> <全新目标数据库名>' >&2; exit 2; }
    backup=$1 target=$2
    source_database=$(configured_database)
    case "$target" in "$source_database"|postgres|template0|template1|'') echo '目标必须是当前源库以外的独立新数据库' >&2; exit 2;; esac
    case "$target" in *[!a-zA-Z0-9_]* ) echo '目标数据库名只允许字母、数字和下划线' >&2; exit 2;; esac
    [ -f "$backup" ] || { echo '备份文件不存在' >&2; exit 2; }
    compose up -d --wait --wait-timeout 120 db
    exists=$(compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; psql -h 127.0.0.1 -At -U ontology_admin -d postgres -c "SELECT 1 FROM pg_database WHERE datname = '\''$1'\''"' sh "$target")
    [ -z "$exists" ] || { echo '目标数据库已存在；恢复只允许新建独立目标' >&2; exit 2; }
    compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; createdb -h 127.0.0.1 -U ontology_admin "$1"' sh "$target"
    compose exec --user 0 -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; exec pg_restore --no-owner --no-acl --exit-on-error -h 127.0.0.1 -U ontology_admin -d "$1"' sh "$target" < "$backup"
    db_admin_psql "$target" "$DEPLOY/local/repair-restored-age-oids.sql"
    db_admin_psql "$target" "$DEPLOY/local/prepare-db-roles.sql"
    db_admin_psql "$target" "$DEPLOY/local/finalize-db-permissions.sql"
    ;;
  down) need_config; compose down ;;
  package-app)
    need_config
    [ -s "$PRIVATE/package.json" ] || { echo '缺少私密 App 配置 deploy/private/package.json' >&2; exit 2; }
    compose --profile tools run --rm package
    ;;
  *) usage; exit 2 ;;
esac

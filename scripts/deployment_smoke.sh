#!/bin/sh
set -eu

if [ "${ONTOLOGY_DEPLOY_SMOKE:-}" != "1" ]; then
  echo '仅允许通过 ONTOLOGY_DEPLOY_SMOKE=1 在隔离 CI 环境运行部署 smoke' >&2
  exit 2
fi

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEPLOY=$ROOT/deploy
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-ontology-deploy-smoke-${GITHUB_RUN_ID:-local}}
export COMPOSE_PROJECT_NAME
compose() {
  docker compose --project-directory "$DEPLOY" --env-file "$DEPLOY/.env" -f "$DEPLOY/compose.yaml" "$@"
}
cleanup() {
  if [ -f "$DEPLOY/.env" ]; then
    compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  rm -rf "$DEPLOY/private"
  rm -f "$DEPLOY/.env"
}
trap cleanup EXIT HUP INT TERM

cp "$DEPLOY/.env.example" "$DEPLOY/.env"
sed -i 's|^PUBLIC_ORIGIN=.*|PUBLIC_ORIGIN=https://127.0.0.1:18083|' "$DEPLOY/.env"
sed -i 's|^IMAGE_PREFIX=.*|IMAGE_PREFIX=ontology-smoke|' "$DEPLOY/.env"
sed -i 's|^IMAGE_TAG=.*|IMAGE_TAG=ci|' "$DEPLOY/.env"
sed -i 's|^API_TRUSTED_PROXY_REGEX=.*|API_TRUSTED_PROXY_REGEX=172\\.30\\.0\\.10|' "$DEPLOY/.env"
sed -i 's|^EDGE_TRUSTED_PROXY_CIDRS=.*|EDGE_TRUSTED_PROXY_CIDRS=172.17.0.1/32|' "$DEPLOY/.env"

deploy_script=$DEPLOY/ontology.sh
"$deploy_script" init-config
umask 077
printf '%s' false > "$DEPLOY/private/api-config/ontology.security.cookie-secure"
printf '%s' Lax > "$DEPLOY/private/api-config/ontology.security.cookie-same-site"
bootstrap_password="CI-smoke-${GITHUB_RUN_ID:-local}-OnlyStrong1"
cat > "$DEPLOY/private/bootstrap-admin.json" <<JSON
{"schemaVersion":1,"username":"smoke-owner","displayName":"CI Smoke Owner","password":"$bootstrap_password"}
JSON
chmod 600 "$DEPLOY/private/bootstrap-admin.json"

# 真实构建五个交付镜像，再通过正式部署命令启动空卷并完成迁移和管理员初始化。
compose --profile tools build api web db migrate package
"$deploy_script" migrate
"$deploy_script" init-admin
# 同一私密输入重复执行必须幂等；后续仍用原密码登录确认没有重置。
"$deploy_script" init-admin
"$deploy_script" up

wait_for_web() {
  ready=0
  attempt=0
  while [ "$attempt" -lt 30 ]; do
    if curl --fail --silent --show-error http://127.0.0.1:18083/health >/dev/null 2>&1; then
      ready=1
      break
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  [ "$ready" -eq 1 ] || { echo 'web 健康检查超时' >&2; compose ps; exit 1; }
}
wait_for_service_health() {
  attempt=0
  while [ "$attempt" -lt 45 ]; do
    healthy=1
    for service in db api web; do
      container_id=$(compose ps -q "$service")
      [ -n "$container_id" ] || { healthy=0; break; }
      health=$(docker inspect --format '{{.State.Health.Status}}' "$container_id")
      [ "$health" = healthy ] || { healthy=0; break; }
    done
    [ "$healthy" -eq 1 ] && return 0
    attempt=$((attempt + 1))
    sleep 2
  done
  echo '服务未全部进入 healthy 状态' >&2
  compose ps
  compose logs --tail=100 db api web
  exit 1
}
wait_for_web
wait_for_service_health

ONTOLOGY_SMOKE_PASSWORD="$bootstrap_password" python3 "$ROOT/scripts/deployment_http_smoke.py"

user_count=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; psql -v ON_ERROR_STOP=1 -At -U ontology_admin -d ontology -c "SELECT count(*) FROM ontology.og_user"')
[ "$user_count" = 1 ] || { echo "正式 bootstrap 应只建立一个管理员，实际用户数=$user_count" >&2; exit 1; }

# 重启三项常驻服务后再次确认新建管理员仍在空卷上持久化。
compose restart db api web
wait_for_web
wait_for_service_health
user_count=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; psql -v ON_ERROR_STOP=1 -At -U ontology_admin -d ontology -c "SELECT count(*) FROM ontology.og_user"')
[ "$user_count" = 1 ] || { echo '服务重启后管理员数据未持久化' >&2; exit 1; }
ONTOLOGY_SMOKE_PASSWORD="$bootstrap_password" ONTOLOGY_SMOKE_MODE=verify python3 "$ROOT/scripts/deployment_http_smoke.py"

backup=$DEPLOY/private/smoke.dump
"$deploy_script" backup "$backup"
"$deploy_script" restore "$backup" ontology_smoke_restore

restore_counts=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; exec psql -q -v ON_ERROR_STOP=1 -At -F: -U ontology_admin -d "$1"' sh ontology_smoke_restore <<'SQL'
SELECT
  (SELECT count(*) FROM ontology.og_user WHERE username = 'smoke-owner') || ':' ||
  (SELECT count(*) FROM ontology.og_user) || ':' ||
  (SELECT count(*) FROM ontology.og_organization WHERE name = 'ci_smoke_project') || ':' ||
  (SELECT count(*) FROM ontology.og_graph g JOIN ontology.og_organization o ON o.id = g.organization_id
    WHERE g.name = 'ci_smoke_graph' AND o.name = 'ci_smoke_project') || ':' ||
  (SELECT count(*) FROM ontology.og_ontology n JOIN ontology.og_graph g ON g.id = n.graph_id
    WHERE g.name = 'ci_smoke_graph' AND n.name = 'CiSmokeNode');
SQL
)
restore_graph_id=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; exec psql -q -v ON_ERROR_STOP=1 -At -U ontology_admin -d "$1" -c "SELECT id FROM ontology.og_graph WHERE name = '\''ci_smoke_graph'\''"' sh ontology_smoke_restore)
restore_age_count=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; exec psql -q -v ON_ERROR_STOP=1 -At -U ontology_admin -d "$1" -v graph_id="$2"' sh ontology_smoke_restore "$restore_graph_id" <<'SQL'
LOAD 'age';
SET search_path = ag_catalog, "$user", public;
SELECT total FROM cypher('ontology' || :'graph_id', 'MATCH (n) RETURN count(n) AS total') AS (total agtype);
SQL
)
[ "$restore_counts" = '1:1:1:1:1' ] && [ "$restore_age_count" = 1 ] \
  || { echo '恢复库管理员、项目、图谱、业务ID或 AGE 节点数据不完整' >&2; exit 1; }
printf '空卷部署、bootstrap 幂等与登录、项目/图谱/AGE 写入读取、服务重启持久化及隔离恢复通过\n'

#!/bin/sh
set -eu

if [ "${ONTOLOGY_DEPLOY_SMOKE:-}" != "1" ]; then
  echo '仅允许通过 ONTOLOGY_DEPLOY_SMOKE=1 在隔离 CI 环境运行部署 smoke' >&2
  exit 2
fi

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
RUN_ID=${GITHUB_RUN_ID:-$$}
RUN_ATTEMPT=${GITHUB_RUN_ATTEMPT:-1}
SMOKE_HOME=${RUNNER_TEMP:-/tmp}/ontology-delivery-smoke-${RUN_ID}-${RUN_ATTEMPT}
case "$SMOKE_HOME" in
  */ontology-delivery-smoke-*) ;;
  *) echo '拒绝使用非 smoke 专属临时目录' >&2; exit 2 ;;
esac
[ ! -e "$SMOKE_HOME" ] || { echo "smoke 临时目录已存在，拒绝覆盖：$SMOKE_HOME" >&2; exit 2; }
mkdir -m 700 "$SMOKE_HOME"
DEPLOY=$SMOKE_HOME/deploy
mkdir -m 700 "$DEPLOY"
# 只复制受版本控制的部署入口；任何现存 .env/private 配置都不会进入或被清理。
cp "$ROOT/deploy/compose.yaml" "$ROOT/deploy/ontology.sh" "$ROOT/deploy/.env.example" "$DEPLOY/"
cp -R "$ROOT/deploy/local" "$DEPLOY/local"
COMPOSE_PROJECT_NAME=ontology-deploy-smoke-${RUN_ID}-${RUN_ATTEMPT}
export COMPOSE_PROJECT_NAME
compose() {
  docker compose --project-directory "$DEPLOY" --env-file "$DEPLOY/.env" -f "$DEPLOY/compose.yaml" "$@"
}
cleanup() {
  if [ -f "$DEPLOY/.env" ]; then
    compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  rm -rf "$SMOKE_HOME"
}
trap cleanup EXIT HUP INT TERM

cp "$DEPLOY/.env.example" "$DEPLOY/.env"
# Python 仅用于 CI smoke 的跨平台模板替换；正式交付命令仍使用 POSIX shell。
python3 - "$DEPLOY/.env" <<'PY'
from pathlib import Path
import sys

env_path = Path(sys.argv[1])
replacements = {
    "PUBLIC_ORIGIN": "https://127.0.0.1:28083",
    "HTTP_BIND_PORT": "28083",
    "IMAGE_PREFIX": "ontology-smoke",
    "IMAGE_TAG": "ci",
    "API_TRUSTED_PROXY_REGEX": r"10\.203\.35\.10",
    "BACKEND_SUBNET": "10.203.35.0/24",
    "WEB_BACKEND_IP": "10.203.35.10",
    "EDGE_TRUSTED_PROXY_CIDRS": "172.17.0.1/32",
}
lines = env_path.read_text().splitlines()
seen = set()
for index, line in enumerate(lines):
    key, separator, _ = line.partition("=")
    if separator and key in replacements:
        lines[index] = f"{key}={replacements[key]}"
        seen.add(key)
missing = replacements.keys() - seen
if missing:
    raise SystemExit(f".env.example 缺少 smoke 配置项: {', '.join(sorted(missing))}")
env_path.write_text("\n".join(lines) + "\n")
PY

deploy_script=$DEPLOY/ontology.sh
"$deploy_script" init-config
umask 077
printf '%s' false > "$DEPLOY/private/api-config/ontology.security.cookie-secure"
printf '%s' Lax > "$DEPLOY/private/api-config/ontology.security.cookie-same-site"
bootstrap_password="CI-smoke-${RUN_ID}-OnlyStrong1"
cat > "$DEPLOY/private/bootstrap-input/bootstrap-admin.json" <<JSON
{"schemaVersion":1,"username":"smoke-owner","displayName":"CI Smoke Owner","password":"$bootstrap_password"}
JSON
chmod 600 "$DEPLOY/private/bootstrap-input/bootstrap-admin.json"

# Compose 不含 build 声明；在原始源码上下文中显式构建五个固定候选镜像。
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  for image in api web db migrate package; do
    docker build -f "$ROOT/deploy/Dockerfile.$image" -t "ontology-smoke/ontology-$image:ci" "$ROOT"
  done
fi
"$deploy_script" migrate
"$deploy_script" init-admin
# 同一私密输入重复执行必须幂等；后续仍用原密码登录确认没有重置。
"$deploy_script" init-admin
"$deploy_script" up

wait_for_web() {
  ready=0
  attempt=0
  while [ "$attempt" -lt 30 ]; do
    if curl --fail --silent --show-error http://127.0.0.1:28083/health >/dev/null 2>&1; then
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

ONTOLOGY_SMOKE_PASSWORD="$bootstrap_password" \
ONTOLOGY_SMOKE_TRANSPORT=http://127.0.0.1:28083 \
ONTOLOGY_SMOKE_ORIGIN=https://127.0.0.1:28083 \
  python3 "$ROOT/scripts/deployment_http_smoke.py"

user_count=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; psql -v ON_ERROR_STOP=1 -At -U ontology_admin -d ontology -c "SELECT count(*) FROM ontology.og_user"')
[ "$user_count" = 1 ] || { echo "正式 bootstrap 应只建立一个管理员，实际用户数=$user_count" >&2; exit 1; }

# 重启三项常驻服务后再次确认新建管理员仍在空卷上持久化。
compose restart db api web
wait_for_web
wait_for_service_health
user_count=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/db_admin_password)"; psql -v ON_ERROR_STOP=1 -At -U ontology_admin -d ontology -c "SELECT count(*) FROM ontology.og_user"')
[ "$user_count" = 1 ] || { echo '服务重启后管理员数据未持久化' >&2; exit 1; }
ONTOLOGY_SMOKE_PASSWORD="$bootstrap_password" \
ONTOLOGY_SMOKE_TRANSPORT=http://127.0.0.1:28083 \
ONTOLOGY_SMOKE_ORIGIN=https://127.0.0.1:28083 \
ONTOLOGY_SMOKE_MODE=verify python3 "$ROOT/scripts/deployment_http_smoke.py"

backup=$DEPLOY/private/backups/smoke.dump
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
# 真实切换到恢复库，验证其重新建立的 runtime 默认角色和应用层图谱读取。
python3 - "$DEPLOY/.env" <<'PY'
from pathlib import Path
import sys

env_path = Path(sys.argv[1])
lines = env_path.read_text().splitlines()
updated = False
for index, line in enumerate(lines):
    if line.startswith("DATABASE_NAME="):
        lines[index] = "DATABASE_NAME=ontology_smoke_restore"
        updated = True
if not updated:
    raise SystemExit(".env 缺少 DATABASE_NAME")
env_path.write_text("\n".join(lines) + "\n")
PY
"$deploy_script" init-config
"$deploy_script" up
wait_for_web
wait_for_service_health
runtime_role=$(compose exec -T db sh -ec 'export PGPASSWORD="$(cat /run/secrets/runtime_db_password)"; exec psql -q -v ON_ERROR_STOP=1 -At -h 127.0.0.1 -U ontology_runtime -d ontology_smoke_restore -c "SELECT current_user"')
[ "$runtime_role" = ontology_app_runtime ] || { echo "恢复库 runtime 默认角色错误：$runtime_role" >&2; exit 1; }
ONTOLOGY_SMOKE_PASSWORD="$bootstrap_password" \
ONTOLOGY_SMOKE_TRANSPORT=http://127.0.0.1:28083 \
ONTOLOGY_SMOKE_ORIGIN=https://127.0.0.1:28083 \
ONTOLOGY_SMOKE_MODE=verify python3 "$ROOT/scripts/deployment_http_smoke.py"
printf '空卷部署、bootstrap 幂等与登录、项目/图谱/AGE 写入读取、服务重启持久化及隔离恢复通过\n'

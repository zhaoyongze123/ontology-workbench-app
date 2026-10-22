"""验证生产 Compose 的镜像、网络和凭据边界。"""
from pathlib import Path
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[3]
COMPOSE = yaml.safe_load((ROOT / "deploy/compose.yaml").read_text())


class DeploymentContracts(unittest.TestCase):
    def test_deployment_images_build_from_source_and_migration_reuses_api_image(self):
        services = COMPOSE["services"]
        self.assertTrue({"db", "api", "web", "migrate"}.issubset(services))
        for name, dockerfile in (("db", "deploy/Dockerfile.db"), ("api", "deploy/Dockerfile.api"), ("web", "deploy/Dockerfile.web")):
            self.assertEqual(services[name]["build"]["context"], "..")
            self.assertEqual(services[name]["build"]["dockerfile"], dockerfile)
        self.assertEqual(services["migrate"]["image"], services["api"]["image"])
        self.assertNotIn("build", services["migrate"])
        self.assertNotIn("profiles", services["migrate"])
        self.assertEqual(services["api"]["depends_on"]["migrate"]["condition"], "service_completed_successfully")
        self.assertEqual(services["package"]["profiles"], ["tools"])

    def test_only_web_has_loopback_host_port(self):
        services = COMPOSE["services"]
        self.assertNotIn("ports", services["api"])
        self.assertNotIn("ports", services["db"])
        self.assertEqual(services["web"]["ports"], ["127.0.0.1:${HTTP_BIND_PORT:-18083}:8080"])
        self.assertEqual(services["db"]["healthcheck"]["test"], ["CMD-SHELL", "pg_isready -U ontology_admin -d ${DATABASE_NAME:-ontology}"])

    def test_api_receives_only_runtime_config_and_migrations_are_explicit(self):
        api = COMPOSE["services"]["api"]
        self.assertEqual(api["volumes"], ["./private/api-config:/run/secrets/app:ro"])
        self.assertNotIn("secrets", api)
        self.assertEqual(api["environment"]["SPRING_FLYWAY_ENABLED"], "false")
        self.assertEqual(api["networks"], ["backend", "egress"])
        self.assertTrue(COMPOSE["networks"]["backend"]["internal"])
        migrate = COMPOSE["services"]["migrate"]
        self.assertEqual(migrate["environment"]["DATABASE_NAME"], "${DATABASE_NAME:-ontology}")
        self.assertEqual(set(migrate["secrets"]), {"db_admin_password", "runtime_db_password", "migrator_db_password", "bootstrap_db_password"})
        self.assertEqual(migrate["entrypoint"], ["/usr/local/bin/ontology-migrate"])
        self.assertEqual(migrate["restart"], "no")
        migrate_script = (ROOT / "deploy/ontology-migrate-entrypoint.sh").read_text()
        self.assertIn("-baselineOnMigrate=false", migrate_script)
        self.assertIn("-validateOnMigrate=true", migrate_script)
        self.assertIn("-cleanDisabled=true", migrate_script)
        self.assertIn("trap finish_migration EXIT", migrate_script)
        self.assertIn("--wait", (ROOT / "deploy/ontology.sh").read_text())

    def test_age_and_workbench_oidc_are_configurable_without_admin_secrets(self):
        script = (ROOT / "deploy/ontology.sh").read_text()
        entrypoint = (ROOT / "deploy/api-entrypoint.sh").read_text()
        db_dockerfile = (ROOT / "deploy/Dockerfile.db").read_text()
        api_dockerfile = (ROOT / "deploy/Dockerfile.api").read_text()
        self.assertIn("graph.database.type", script)
        self.assertIn("graph.database.age.preloaded", script)
        self.assertIn("spring.flyway.enabled", script)
        self.assertIn("ontology.workbench.client-secret", entrypoint)
        self.assertIn("ontology.security.cookie-secure", entrypoint)
        self.assertIn("ontology.security.cookie-same-site", entrypoint)
        self.assertNotIn("bootstrap_db_password", entrypoint)
        self.assertIn("shared_preload_libraries=age", db_dockerfile)
        self.assertIn("FROM flyway/flyway:9.22.3 AS flyway", api_dockerfile)
        self.assertIn("COPY --from=flyway /flyway /opt/flyway", api_dockerfile)
        self.assertIn("postgresql-client", api_dockerfile)
        self.assertFalse((ROOT / "deploy/Dockerfile.migrate").exists())

    def test_bootstrap_input_is_mounted_as_private_directory(self):
        bootstrap = COMPOSE["services"]["bootstrap"]
        self.assertIn("./private/bootstrap-input:/run/bootstrap-input:ro", bootstrap["volumes"])
        self.assertTrue(any("/run/bootstrap-input/bootstrap-admin.json" in arg for arg in bootstrap["command"]))
        self.assertTrue(any("--spring.profiles.active=bootstrap,multiuser" in arg for arg in bootstrap["command"]))

    def test_nginx_overwrites_forwarding_headers_and_limits_api_to_internal_network(self):
        nginx = (ROOT / "deploy/nginx.conf").read_text()
        api_dockerfile = (ROOT / "deploy/Dockerfile.api").read_text()
        self.assertIn("proxy_pass http://api:13001", nginx)
        self.assertIn("proxy_set_header X-Forwarded-For $remote_addr", nginx)
        self.assertIn("proxy_set_header X-Forwarded-Proto https", nginx)
        self.assertNotIn("ports", COMPOSE["services"]["api"])
        self.assertTrue(COMPOSE["networks"]["backend"]["internal"])
        self.assertIn("/api/v1/auth/csrf", api_dockerfile)
        self.assertIn('"$request_method $uri"', nginx)
        self.assertNotIn("$request ", nginx)
        self.assertNotIn("$args", nginx)
        self.assertIn("location = /index.html", nginx)
        self.assertIn("internal;", nginx)
        self.assertIn('add_header Cache-Control "no-cache" always;', nginx)
        self.assertIn('add_header Content-Security-Policy "frame-ancestors', nginx)
        self.assertIn("add_header Strict-Transport-Security", nginx)

    def test_backend_network_and_api_proxy_trust_share_configurable_web_ip(self):
        env_example = (ROOT / "deploy/.env.example").read_text()
        script = (ROOT / "deploy/ontology.sh").read_text()
        backend = COMPOSE["networks"]["backend"]["ipam"]["config"][0]
        self.assertEqual(backend["subnet"], "${BACKEND_SUBNET:-10.203.35.0/24}")
        self.assertEqual(COMPOSE["services"]["web"]["networks"]["backend"]["ipv4_address"], "${WEB_BACKEND_IP:-10.203.35.10}")
        self.assertIn("BACKEND_SUBNET=10.203.35.0/24", env_example)
        self.assertIn("WEB_BACKEND_IP=10.203.35.10", env_example)
        self.assertIn("API_TRUSTED_PROXY_REGEX=10\\.203\\.35\\.10", env_example)
        self.assertIn('printf \'%s/32\' "$web_ip"', script)
        self.assertIn("WEB_BACKEND_IP 已变化", script)

    def test_build_images_use_verified_node_22_and_cached_reactor_build(self):
        web_dockerfile = (ROOT / "deploy/Dockerfile.web").read_text()
        api_dockerfile = (ROOT / "deploy/Dockerfile.api").read_text()
        db_dockerfile = (ROOT / "deploy/Dockerfile.db").read_text()
        self.assertIn("node:22.23.4-alpine3.24@sha256:ce8d97f7cfc7c5386421eb932df20a29df99b1a7f469f6157d5c9956702ffadc", web_dockerfile)
        self.assertIn("--mount=type=cache,target=/root/.m2", api_dockerfile)
        self.assertNotIn("dependency:go-offline", api_dockerfile)
        self.assertIn("https://deb.debian.org", db_dockerfile)
        self.assertIn("Acquire::Retries=3", db_dockerfile)
        self.assertIn("COPY --from=node:22.23.4-alpine3.24@sha256:ce8d97f7cfc7c5386421eb932df20a29df99b1a7f469f6157d5c9956702ffadc /etc/ssl/cert.pem", db_dockerfile)

    def test_database_name_is_shared_and_restore_reapplies_database_permissions(self):
        compose = COMPOSE["services"]
        self.assertEqual(compose["db"]["environment"]["POSTGRES_DB"], "${DATABASE_NAME:-ontology}")
        self.assertIn("${DATABASE_NAME:-ontology}", (ROOT / "deploy/ontology-migrate-entrypoint.sh").read_text())
        self.assertEqual(compose["package"]["environment"]["ONTOLOGY_PUBLIC_ORIGIN"], "${PUBLIC_ORIGIN:?请设置 PUBLIC_ORIGIN}")
        script = (ROOT / "deploy/ontology.sh").read_text()
        self.assertIn("DATABASE_NAME", (ROOT / "deploy/.env.example").read_text())
        self.assertIn('"$target" "$DEPLOY/local/prepare-db-roles.sql"', script)
        self.assertIn('"$target" "$DEPLOY/local/finalize-db-permissions.sql"', script)
        self.assertIn("pg_restore --no-owner --no-acl --exit-on-error", script)
        repair = 'db_admin_psql "$target" "$DEPLOY/local/repair-restored-age-oids.sql"'
        restore_index = script.index("pg_restore --no-owner --no-acl --exit-on-error")
        repair_index = script.index(repair)
        prepare_index = script.index('db_admin_psql "$target" "$DEPLOY/local/prepare-db-roles.sql"')
        finalize_index = script.index('db_admin_psql "$target" "$DEPLOY/local/finalize-db-permissions.sql"')
        self.assertEqual(script.count(repair), 1)
        self.assertLess(restore_index, repair_index)
        self.assertLess(repair_index, prepare_index)
        self.assertLess(prepare_index, finalize_index)
        self.assertIn("compose run --rm --no-deps migrate", script)
        self.assertIn("compose up -d --build --wait --wait-timeout 600 db api web", script)
        self.assertNotIn("IMAGE_PREFIX", script)
        self.assertNotIn("IMAGE_TAG", script)
        self.assertNotIn("pull)", script)

    def test_first_install_runs_database_migration_bootstrap_then_application(self):
        script = (ROOT / "deploy/ontology.sh").read_text()
        install = script.split("  install)\n", 1)[1].split("\n    ;;", 1)[0]
        self.assertLess(install.index("compose up -d --wait --wait-timeout 120 db"), install.index("compose run --rm --no-deps migrate"))
        self.assertLess(install.index("compose run --rm --no-deps migrate"), install.index("compose --profile tools run --rm --no-deps bootstrap"))
        self.assertLess(install.index("compose --profile tools run --rm --no-deps bootstrap"), install.index("compose up -d --no-deps --wait --wait-timeout 600 api web"))
        self.assertNotIn("package", install)

    def test_age_oid_repair_is_transactional_and_checks_catalog_associations(self):
        repair = (ROOT / "deploy/local/repair-restored-age-oids.sql").read_text()
        self.assertIn("LOCK TABLE ag_catalog.ag_graph, ag_catalog.ag_label IN ACCESS EXCLUSIVE MODE", repair)
        self.assertIn("n.nspname IS DISTINCT FROM g.name::text", repair)
        self.assertIn("n.oid IS DISTINCT FROM g.namespace::oid", repair)
        self.assertIn("graphid IS DISTINCT FROM g.namespace::oid", repair)
        self.assertIn("temporary_graphid", repair)
        self.assertIn("DROP CONSTRAINT fk_graph_oid", repair)
        self.assertIn("ADD CONSTRAINT fk_graph_oid FOREIGN KEY", repair)
        self.assertIn("BEGIN;", repair)
        self.assertIn("COMMIT;", repair)
        self.assertIn("https://github.com/apache/age/issues/2503", repair)


if __name__ == "__main__":
    unittest.main()

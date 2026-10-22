"""验证生产 Compose 的镜像、网络和凭据边界。"""
from pathlib import Path
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[3]
COMPOSE = yaml.safe_load((ROOT / "deploy/compose.yaml").read_text())


class DeploymentContracts(unittest.TestCase):
    def test_production_services_use_published_images_without_build_contexts(self):
        services = COMPOSE["services"]
        self.assertEqual({"db", "api", "web"}.issubset(services), True)
        for service in services.values():
            self.assertIn("image", service)
            self.assertNotIn("build", service)
            self.assertIn("${IMAGE_TAG:?", service["image"])

    def test_only_web_has_loopback_host_port(self):
        services = COMPOSE["services"]
        self.assertNotIn("ports", services["api"])
        self.assertNotIn("ports", services["db"])
        self.assertEqual(services["web"]["ports"], ["127.0.0.1:${HTTP_BIND_PORT:-18083}:8080"])

    def test_api_receives_only_runtime_config_and_migrations_are_explicit(self):
        api = COMPOSE["services"]["api"]
        self.assertEqual(api["volumes"], ["./private/api-config:/run/secrets/app:ro"])
        self.assertNotIn("secrets", api)
        self.assertEqual(api["environment"]["SPRING_FLYWAY_ENABLED"], "false")
        self.assertEqual(COMPOSE["services"]["migrate"]["profiles"], ["tools"])
        self.assertEqual(api["networks"], ["backend", "egress"])
        self.assertTrue(COMPOSE["networks"]["backend"]["internal"])
        migrate = COMPOSE["services"]["migrate"]
        self.assertEqual(migrate["environment"]["FLYWAY_SCHEMAS"], "ontology")
        self.assertEqual(migrate["environment"]["FLYWAY_DEFAULT_SCHEMA"], "ontology")
        self.assertEqual(migrate["environment"]["FLYWAY_BASELINE_ON_MIGRATE"], "false")
        self.assertEqual(migrate["environment"]["FLYWAY_VALIDATE_ON_MIGRATE"], "true")
        self.assertEqual(migrate["environment"]["FLYWAY_CLEAN_DISABLED"], "true")
        self.assertIn("--wait", (ROOT / "deploy/ontology.sh").read_text())

    def test_age_and_workbench_oidc_are_configurable_without_admin_secrets(self):
        script = (ROOT / "deploy/ontology.sh").read_text()
        entrypoint = (ROOT / "deploy/api-entrypoint.sh").read_text()
        db_dockerfile = (ROOT / "deploy/Dockerfile.db").read_text()
        migrate_dockerfile = (ROOT / "deploy/Dockerfile.migrate").read_text()
        self.assertIn("graph.database.type", script)
        self.assertIn("graph.database.age.preloaded", script)
        self.assertIn("spring.flyway.enabled", script)
        self.assertIn("ontology.workbench.client-secret", entrypoint)
        self.assertNotIn("bootstrap_db_password", entrypoint)
        self.assertIn("shared_preload_libraries=age", db_dockerfile)
        self.assertIn("flyway/flyway:9.22.3", migrate_dockerfile)

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
        self.assertIn("expires -1;", nginx)

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
        self.assertIn("${DATABASE_NAME:-ontology}", compose["migrate"]["command"][0])
        self.assertEqual(compose["package"]["environment"]["ONTOLOGY_PUBLIC_ORIGIN"], "${PUBLIC_ORIGIN:?请设置 PUBLIC_ORIGIN}")
        script = (ROOT / "deploy/ontology.sh").read_text()
        self.assertIn("DATABASE_NAME", (ROOT / "deploy/.env.example").read_text())
        self.assertIn('"$database" "$DEPLOY/local/prepare-db-roles.sql"', script)
        self.assertIn('"$target" "$DEPLOY/local/prepare-db-roles.sql"', script)
        self.assertIn('"$target" "$DEPLOY/local/finalize-db-permissions.sql"', script)
        self.assertIn("--force-recreate api web", script)


if __name__ == "__main__":
    unittest.main()

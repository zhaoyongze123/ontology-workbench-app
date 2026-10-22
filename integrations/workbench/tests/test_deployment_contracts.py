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

    def test_nginx_overwrites_forwarding_headers_and_limits_api_to_internal_network(self):
        nginx = (ROOT / "deploy/nginx.conf").read_text()
        api_dockerfile = (ROOT / "deploy/Dockerfile.api").read_text()
        self.assertIn("proxy_pass http://api:13001", nginx)
        self.assertIn("proxy_set_header X-Forwarded-For $remote_addr", nginx)
        self.assertIn("proxy_set_header X-Forwarded-Proto https", nginx)
        self.assertTrue(COMPOSE["networks"]["backend"]["internal"])
        self.assertIn("/api/v1/auth/csrf", api_dockerfile)


if __name__ == "__main__":
    unittest.main()

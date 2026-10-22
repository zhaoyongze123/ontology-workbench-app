"""验证 Docker 打包入口只从部署环境获取公共域名，且不回显凭据。"""
from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "package_entrypoint", ROOT / "deploy/package-entrypoint.py")
ENTRYPOINT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ENTRYPOINT)


class PackageEntrypointTest(unittest.TestCase):
    def config(self):
        return {
            "serviceOrigin": "https://stale.example.invalid",
            "redirectUri": "https://app.workbench.aos.work/basilcraw/api/apps/oauth2/callback",
            "appEntryPath": "/ontology",
            "clientId": "fixture-client-id",
            "clientSecret": "fixture-private-secret",
            "clientAuthMethod": "body",
        }

    def invoke(self, config, root, origin):
        config_path = root / "private-package.json"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        original = config_path.read_bytes()
        output = io.StringIO()
        with patch.dict("os.environ", {"ONTOLOGY_PUBLIC_ORIGIN": origin}), redirect_stdout(output):
            result = ENTRYPOINT.main(["--config", str(config_path), "--output", str(root / "out")])
        return result, output.getvalue(), config_path, original

    def test_environment_origin_overrides_private_origin_without_leaking_credentials(self):
        origin = "https://production.example.invalid"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            result, stdout, config_path, original = self.invoke(self.config(), root, origin)
            self.assertEqual(result, 0)
            payload = json.loads(stdout)
            archive = Path(payload["archive"])
            self.assertNotIn("fixture-private-secret", stdout)
            self.assertEqual(config_path.read_bytes(), original)
            with zipfile.ZipFile(archive) as package:
                manifest = package.read("manifest.yaml")
                self.assertIn((origin + "/api/v1/oidc/app/token").encode(), manifest)
                self.assertNotIn(b"stale.example.invalid", b" ".join(package.read(name) for name in package.namelist()))
                self.assertIn(b"fixture-private-secret", package.read("oauth-clients.yaml"))
                for name in package.namelist():
                    if name != "oauth-clients.yaml":
                        self.assertNotIn(b"fixture-private-secret", package.read(name))

    def test_missing_public_origin_fails_even_when_private_json_has_origin(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config_path = root / "private-package.json"
            config_path.write_text(json.dumps(self.config()), encoding="utf-8")
            output = io.StringIO()
            with patch.dict("os.environ", {"ONTOLOGY_PUBLIC_ORIGIN": ""}), redirect_stdout(output):
                result = ENTRYPOINT.main(["--config", str(config_path), "--output", str(root / "out")])
            self.assertEqual(result, 1)
            self.assertIn("必须设置 ONTOLOGY_PUBLIC_ORIGIN", output.getvalue())
            self.assertNotIn("fixture-private-secret", output.getvalue())
            self.assertFalse((root / "out").exists())

    def test_finalizer_still_rejects_credential_placeholders_without_echoing_them(self):
        config = self.config()
        config["clientSecret"] = "__CLIENT_SECRET__"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            result, stdout, _, _ = self.invoke(config, root, "https://production.example.invalid")
            self.assertEqual(result, 1)
            self.assertNotIn("__CLIENT_SECRET__", stdout)
            self.assertFalse((root / "out").exists())


if __name__ == "__main__":
    unittest.main()

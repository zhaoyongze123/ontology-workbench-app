"""Docker 一次性打包入口：公共域名只从部署环境读取。"""
import argparse
import importlib
import json
import os
from pathlib import Path
import sys
import tempfile


def _load_finalizer():
    repository_root = Path(__file__).resolve().parents[1]
    tools_path = str(repository_root / "integrations/workbench/tools")
    if tools_path not in sys.path:
        sys.path.insert(0, tools_path)
    return importlib.import_module("finalize")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)

    public_origin = os.environ.get("ONTOLOGY_PUBLIC_ORIGIN", "").strip()
    if not public_origin:
        print(json.dumps({"status": "pending_configuration",
                          "message": "必须设置 ONTOLOGY_PUBLIC_ORIGIN"}, ensure_ascii=False))
        return 1

    try:
        config = json.loads(args.config.read_text(encoding="utf-8"))
        if not isinstance(config, dict):
            raise ValueError
        # serviceOrigin 仅在内存配置中覆盖，部署方的私有凭据文件保持原样。
        config["serviceOrigin"] = public_origin
        finalizer = _load_finalizer()
        with tempfile.TemporaryDirectory(prefix="ontology-package-config-") as temporary:
            temporary_config = Path(temporary) / "package.json"
            temporary_config.write_text(json.dumps(config, ensure_ascii=False), encoding="utf-8")
            temporary_config.chmod(0o600)
            result = finalizer.assemble(temporary_config, args.output)
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except Exception:
        # 解析器或 finalizer 的底层异常可能携带本机文件内容；对外仅返回固定错误文本。
        print(json.dumps({"status": "pending_configuration",
                          "message": "配置格式无效或打包校验失败，请检查本机私有文件"}, ensure_ascii=False))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

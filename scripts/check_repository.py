#!/usr/bin/env python3
"""检查待提交文件中的常见凭据模式和不应入库的运行产物；不回显匹配内容。"""
from pathlib import Path
import argparse
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_PARTS = {"private", "node_modules", "target", "dist", "dist-multiuser", "logs", "data", "__pycache__"}
FORBIDDEN_SUFFIXES = {".dump", ".key", ".pem", ".p12", ".pfx", ".jar", ".zip", ".sqlite", ".db", ".pyc"}
PATTERNS = {
    "GitHub credential": re.compile(rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{22,})"),
    "private key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "AWS credential": re.compile(rb"\bAKIA[A-Z0-9]{16}\b"),
    "bearer literal": re.compile(rb"Bearer\s+[A-Za-z0-9_\-]{32,}(?:\.[A-Za-z0-9_\-]+){0,2}"),
}
ASSIGNMENT = re.compile(
    r"(?i)(?:client[_-]?secret|access[_-]?key[_-]?(?:id|secret)|api[_-]?key|access[_-]?token|refresh[_-]?token|(?:login[_-]?)?password)"
    r"[\"']?\s*[:=]\s*[\"']?([^\s\"',}{;]{12,})"
)


def is_placeholder(value):
    lower = value.lower()
    return (value in {"access_token", "refresh_token", "client_secret", "expires_in", "token_type"}
            or value.startswith(("${", "__", "<", "$", "process.", "os.", "self.", "properties.", "config.", "request."))
            or any(part in lower for part in ("example", "replace", "change-me", "changeme", "placeholder"))
            or "getenv(" in lower or "getproperty(" in lower or "read_text(" in lower)


def inspect_file(name, content):
    path = Path(name)
    problems = []
    if (set(path.parts) & FORBIDDEN_PARTS or path.suffix in FORBIDDEN_SUFFIXES
            or (path.name.startswith(".env") and not path.name.endswith(".example"))):
        problems.append("运行文件或私有配置不能入库")
    if len(content) > 5 * 1024 * 1024:
        problems.append("单文件超过 5 MiB，请核对是否应作为产物管理")
    for label, pattern in PATTERNS.items():
        if pattern.search(content):
            problems.append(label)
    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError:
        return problems
    # 测试夹具中的占位口令按测试路径区分，绝不把生产配置排除出检查。
    test_fixture = "src/test" in name or "/tests/" in "/" + name
    if not test_fixture:
        for match in ASSIGNMENT.finditer(text):
            if path.suffix in {".java", ".py", ".ts", ".tsx", ".js"}:
                # 代码中的变量赋值不是固定凭据，仅检查真实字符串字面量。
                before = text[match.start(1) - 1:match.start(1)]
                if before not in {"'", '"'}:
                    continue
            if not is_placeholder(match.group(1)):
                line = text.count("\n", 0, match.start()) + 1
                problems.append(f"疑似固定凭据，第 {line} 行")
    return problems


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--staged", action="store_true")
    args = parser.parse_args()
    command = (["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"] if args.staged
               else ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"])
    names = subprocess.check_output(command, cwd=ROOT).decode().split("\0")
    errors = []
    count = 0
    for name in sorted(set(filter(None, names))):
        path = ROOT / name
        if args.staged:
            content = subprocess.check_output(["git", "show", ":" + name], cwd=ROOT)
        elif path.is_symlink():
            # 源码本身必须可移植，不能提交指向仓库外的开发机软链接。
            errors.append((name, "不能提交本机软链接"))
            continue
        elif path.is_file():
            content = path.read_bytes()
        else:
            continue
        count += 1
        errors.extend((name, reason) for reason in inspect_file(name, content))
    for name, reason in errors:
        print(f"{name}: {reason}", file=sys.stderr)
    print(f"仓库检查：{count} 个文件，{len(errors)} 个待处理项。")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())


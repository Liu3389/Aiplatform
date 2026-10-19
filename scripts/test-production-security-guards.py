#!/usr/bin/env python3
"""Integration tests for secret-file, template, and production Compose gates."""

from __future__ import annotations

import os
import json
import shutil
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SYNTHETIC_VALUES = {
    "MYSQL_PASSWORD": "synthetic-mysql-passphrase-2026",
    "REDIS_PASSWORD": "synthetic-redis-passphrase-2026",
    "API_ENCRYPTION_KEY": "synthetic-api-encryption-key-for-tests-only",
    "ALLOWED_ORIGINS": "https://ai-platform.invalid",
    "SECONDARY_PASSWORD": "synthetic-secondary-passphrase-2026",
}
OPTIONAL_VALUES = {
    "RABBITMQ_ENABLED": "false",
    "RABBITMQ_HOST": "rabbit.example.invalid",
    "RABBITMQ_PORT": "5672",
    "RABBITMQ_USERNAME": "rabbit-user",
    "RABBITMQ_PASSWORD": "test-rabbit-value",
    "RABBITMQ_VHOST": "/platform",
    "ALIYUN_OSS_ENDPOINT": "https://oss.example.invalid",
    "ALIYUN_OSS_ACCESS_KEY_ID": "test-oss-id",
    "ALIYUN_OSS_ACCESS_KEY_SECRET": "test-oss-secret",
    "ALIYUN_OSS_BUCKET": "test-bucket",
    "ALIYUN_OSS_DOMAIN": "https://files.example.invalid",
    "APP_STORAGE_PREFER_LOCAL": "false",
    "SPRING_MAIL_HOST": "smtp.example.invalid",
    "SPRING_MAIL_PORT": "587",
    "SPRING_MAIL_USERNAME": "mail-user",
    "SPRING_MAIL_PASSWORD": "test-mail-value",
    "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH": "true",
    "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE": "true",
    "SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE": "false",
    "VERIFICATION_DELIVERY_MODE": "external",
    "VERIFICATION_ALLOW_LOCAL_CAPTURE": "false",
    "VERIFICATION_EMAIL_FROM": "noreply@example.invalid",
    "VERIFICATION_SMS_URL": "https://sms.example.invalid/send",
    "VERIFICATION_SMS_TOKEN": "test-sms-value",
    "VERIFICATION_SMS_TEMPLATE_ID": "template-test",
    "GOOGLE_OAUTH_CLIENT_ID": "test-client-id",
    "GOOGLE_OAUTH_CLIENT_SECRET": "test-google-secret",
    "GOOGLE_OAUTH_REDIRECT_URI": "https://example.invalid/oauth/callback",
}
REQUIRED_ENV = tuple(SYNTHETIC_VALUES)


def run(command: list[str], *, cwd: Path, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, env=env, text=True, capture_output=True, check=False)


def write_env(path: Path, values: dict[str, str]) -> None:
    path.write_text("".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8")
    path.chmod(0o600)


def start_temp_repo(root: Path) -> None:
    (root / "scripts").mkdir(parents=True, exist_ok=True)
    (root / ".gitignore").write_text(".env.*\n!.env.*.example\n", encoding="utf-8")
    for name in ("docker-compose.prod.yml", "docker-compose.server.yml"):
        shutil.copy2(ROOT / name, root / name)
    shutil.copy2(ROOT / "scripts/check-production-env.py", root / "scripts/check-production-env.py")
    subprocess.run(["git", "init", "-q"], cwd=root, check=True)

    bin_dir = root / "bin"
    bin_dir.mkdir()
    docker_stub = bin_dir / "docker"
    docker_stub.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
    docker_stub.chmod(0o700)


def checker_env(root: Path) -> dict[str, str]:
    env = os.environ.copy()
    env["PATH"] = f"{root / 'bin'}:{env.get('PATH', '')}"
    return env


def test_production_env_guard() -> None:
    cases = (
        ("valid", SYNTHETIC_VALUES.copy(), 0o600, False, 0),
        ("weak_permissions", SYNTHETIC_VALUES.copy(), 0o644, False, 1),
        ("weak_secrets", {**SYNTHETIC_VALUES, "MYSQL_PASSWORD": "short"}, 0o600, False, 1),
        ("placeholder", {**SYNTHETIC_VALUES, "SECONDARY_PASSWORD": "5215"}, 0o600, False, 1),
        ("wildcard_origin", {**SYNTHETIC_VALUES, "ALLOWED_ORIGINS": "*"}, 0o600, False, 1),
        ("tracked_env", SYNTHETIC_VALUES.copy(), 0o600, True, 1),
    )
    for name, values, mode, tracked, expected in cases:
        with tempfile.TemporaryDirectory(prefix=f"prod-env-{name}-") as temp:
            root = Path(temp)
            start_temp_repo(root)
            env_path = root / ".env.prod"
            write_env(env_path, values)
            env_path.chmod(mode)
            if tracked:
                subprocess.run(["git", "add", "-f", ".env.prod"], cwd=root, check=True)
            result = run(["python3", "scripts/check-production-env.py"], cwd=root, env=checker_env(root))
            assert (result.returncode == 0) == (expected == 0), (name, result.stdout, result.stderr)
            combined = result.stdout + result.stderr
            assert all(not value or value not in combined for value in values.values()), f"{name} leaked a value"


def test_server_production_env_guard() -> None:
    cases = (
        ("valid_server_env", SYNTHETIC_VALUES.copy(), 0o600, 0),
        ("missing_redis_password", {**SYNTHETIC_VALUES, "REDIS_PASSWORD": ""}, 0o600, 1),
        ("partial_google_oidc", {**SYNTHETIC_VALUES, "GOOGLE_OAUTH_CLIENT_ID": "test-client-id"}, 0o600, 1),
        ("weak_server_permissions", SYNTHETIC_VALUES.copy(), 0o640, 1),
    )
    with tempfile.TemporaryDirectory(prefix="remote-prod-env-") as temp:
        root = Path(temp)
        (root / "scripts").mkdir()
        shutil.copy2(ROOT / "scripts/check-production-env.py", root / "scripts/check-production-env.py")
        for name, values, mode, expected in cases:
            env_path = root / f"{name}.env"
            write_env(env_path, values)
            env_path.chmod(mode)
            result = run(
                ["python3", "scripts/check-production-env.py", "--server", "--env-file", str(env_path)],
                cwd=root,
            )
            assert (result.returncode == 0) == (expected == 0), (name, result.stdout, result.stderr)
            combined = result.stdout + result.stderr
            assert all(not value or value not in combined for value in values.values()), f"{name} leaked a value"
            if name == "missing_redis_password":
                assert "REDIS_PASSWORD" in combined, "server gate should identify the missing variable by name"
            if name == "partial_google_oidc":
                assert "GOOGLE_OAUTH_CLIENT_SECRET" in combined and "GOOGLE_OAUTH_REDIRECT_URI" in combined


def test_untracked_encryption_template_gate() -> None:
    with tempfile.TemporaryDirectory(prefix="template-secret-gate-") as temp:
        root = Path(temp)
        (root / "scripts").mkdir()
        shutil.copy2(ROOT / "scripts/check-staged-env-secrets.py", root / "scripts/check-staged-env-secrets.py")
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)
        template = root / ".env.prod.example"
        synthetic_key = "synthetic-high-entropy-encryption-key-abcdefghijklmnop"
        template.write_text(f"API_ENCRYPTION_KEY={synthetic_key}\n", encoding="utf-8")

        blocked = run(["python3", "scripts/check-staged-env-secrets.py"], cwd=root)
        assert blocked.returncode != 0 and ".env.prod.example:1" in blocked.stderr, blocked.stderr
        assert synthetic_key not in blocked.stdout + blocked.stderr

        template.write_text(
            "API_ENCRYPTION_KEY=replace_with_base64_encoded_32_byte_secret\n", encoding="utf-8"
        )
        passed = run(["python3", "scripts/check-staged-env-secrets.py"], cwd=root)
        assert passed.returncode == 0, passed.stderr


def test_compose_requires_secrets() -> None:
    clean_env = os.environ.copy()
    for key in REQUIRED_ENV:
        clean_env.pop(key, None)
    with tempfile.TemporaryDirectory(prefix="compose-secret-gate-") as temp:
        env_path = Path(temp) / "empty.env"
        env_path.write_text("", encoding="utf-8")
        for compose_file in ("docker-compose.prod.yml", "docker-compose.server.yml"):
            command = [
                "docker",
                "compose",
                "--file",
                str(ROOT / compose_file),
                "--env-file",
                str(env_path),
                "config",
                "-q",
            ]
            rejected = run(command, cwd=ROOT, env=clean_env)
            assert rejected.returncode != 0, f"{compose_file} accepted missing production secrets"

        valid_env = Path(temp) / "valid.env"
        write_env(valid_env, SYNTHETIC_VALUES | OPTIONAL_VALUES)
        for compose_file in ("docker-compose.prod.yml", "docker-compose.server.yml"):
            accepted = run(
                [
                    "docker",
                    "compose",
                    "--file",
                    str(ROOT / compose_file),
                    "--env-file",
                    str(valid_env),
                    "config",
                    "--format",
                    "json",
                ],
                cwd=ROOT,
                env=clean_env,
            )
            assert accepted.returncode == 0, f"{compose_file}: {accepted.stderr}"
            assert all(value not in accepted.stderr for value in (SYNTHETIC_VALUES | OPTIONAL_VALUES).values())
            redis = json.loads(accepted.stdout)["services"]["redis"]
            assert redis["environment"].get("REDIS_PASSWORD") == SYNTHETIC_VALUES["REDIS_PASSWORD"]
            assert redis["command"][-2:] == ["--requirepass", SYNTHETIC_VALUES["REDIS_PASSWORD"]]
            assert "REDISCLI_AUTH=$$REDIS_PASSWORD" in redis["healthcheck"]["test"][1]
            backend = json.loads(accepted.stdout)["services"]["backend"]["environment"]
            assert all(backend.get(key) == value for key, value in OPTIONAL_VALUES.items())


def main() -> int:
    test_production_env_guard()
    test_server_production_env_guard()
    test_untracked_encryption_template_gate()
    test_compose_requires_secrets()
    print("生产密钥门禁通过：权限、索引、占位配置、未跟踪模板和 Compose 必填变量均有正反向回归。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

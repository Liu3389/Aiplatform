#!/usr/bin/env python3
"""Validate local production secret injection without printing secret values."""

from __future__ import annotations

import os
import argparse
import re
import stat
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / ".env.prod"
REQUIRED = (
    "MYSQL_PASSWORD",
    "REDIS_PASSWORD",
    "API_ENCRYPTION_KEY",
    "ALLOWED_ORIGINS",
    "SECONDARY_PASSWORD",
)
MIN_LENGTHS = {
    "MYSQL_PASSWORD": 16,
    "REDIS_PASSWORD": 16,
    "API_ENCRYPTION_KEY": 32,
    "SECONDARY_PASSWORD": 16,
}
PLACEHOLDER = re.compile(
    r"(?:placeholder|example|dummy|test[-_ ]?key|local[-_ ]?only|sk-local|your[-_ ]|"
    r"(?:change|replace|set)[-_ ]?me|^5215$)",
    re.IGNORECASE,
)
ASSIGNMENT = re.compile(r"^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$")


def fail(message: str) -> int:
    print(f"生产密钥预检失败：{message}", file=sys.stderr)
    return 1


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = ASSIGNMENT.match(stripped)
        if not match:
            continue
        key, value = match.groups()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        elif " #" in value:
            value = value.split(" #", 1)[0].rstrip()
        values[key] = value
    return values


def validate_optional_services(values: dict[str, str]) -> list[str]:
    invalid: list[str] = []

    rabbit_enabled = values.get("RABBITMQ_ENABLED", "false").strip().lower()
    if rabbit_enabled not in ("true", "false"):
        invalid.append("RABBITMQ_ENABLED")
    if rabbit_enabled == "true":
        rabbit_required = (
            "RABBITMQ_HOST",
            "RABBITMQ_PORT",
            "RABBITMQ_USERNAME",
            "RABBITMQ_PASSWORD",
            "RABBITMQ_VHOST",
        )
        invalid.extend(key for key in rabbit_required if not values.get(key, "").strip())
        if values.get("RABBITMQ_HOST", "").strip().lower() in ("localhost", "127.0.0.1"):
            invalid.append("RABBITMQ_HOST")
        if values.get("RABBITMQ_USERNAME", "").strip().lower() == "guest":
            invalid.append("RABBITMQ_USERNAME")
        rabbit_password = values.get("RABBITMQ_PASSWORD", "").strip()
        if len(rabbit_password) < 16 or PLACEHOLDER.search(rabbit_password) or rabbit_password.lower() == "guest":
            invalid.append("RABBITMQ_PASSWORD")

    prefer_local = values.get("APP_STORAGE_PREFER_LOCAL", "true").strip().lower()
    if prefer_local not in ("true", "false"):
        invalid.append("APP_STORAGE_PREFER_LOCAL")
    oss_fields = (
        "ALIYUN_OSS_ENDPOINT",
        "ALIYUN_OSS_ACCESS_KEY_ID",
        "ALIYUN_OSS_ACCESS_KEY_SECRET",
        "ALIYUN_OSS_BUCKET",
        "ALIYUN_OSS_DOMAIN",
    )
    oss_configured = any(values.get(key, "").strip() for key in oss_fields)
    if prefer_local == "false" or oss_configured:
        invalid.extend(key for key in oss_fields if not values.get(key, "").strip())

    mail_fields = ("SPRING_MAIL_HOST", "SPRING_MAIL_PORT", "SPRING_MAIL_USERNAME", "SPRING_MAIL_PASSWORD")
    if any(values.get(key, "").strip() for key in mail_fields):
        invalid.extend(key for key in mail_fields if not values.get(key, "").strip())

    sms_fields = ("VERIFICATION_SMS_URL", "VERIFICATION_SMS_TOKEN", "VERIFICATION_SMS_TEMPLATE_ID")
    if any(values.get(key, "").strip() for key in sms_fields):
        invalid.extend(key for key in sms_fields if not values.get(key, "").strip())

    google_fields = ("GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_SECRET", "GOOGLE_OAUTH_REDIRECT_URI")
    if any(values.get(key, "").strip() for key in google_fields):
        invalid.extend(key for key in google_fields if not values.get(key, "").strip())
        redirect_uri = values.get("GOOGLE_OAUTH_REDIRECT_URI", "").strip()
        if redirect_uri and not redirect_uri.lower().startswith("https://"):
            invalid.append("GOOGLE_OAUTH_REDIRECT_URI")

    delivery_mode = values.get("VERIFICATION_DELIVERY_MODE", "disabled").strip().lower()
    if delivery_mode not in ("disabled", "external"):
        invalid.append("VERIFICATION_DELIVERY_MODE")

    return sorted(set(invalid))


def git(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate protected production environment configuration.")
    parser.add_argument("--server", action="store_true", help="validate a server-side env file without Git or local Compose checks")
    parser.add_argument("--env-file", type=Path, default=ENV_FILE)
    args = parser.parse_args()
    env_file = args.env_file

    if not env_file.is_file() or env_file.is_symlink():
        return fail("缺少普通文件 .env.prod；请在服务器或受控本地创建密钥文件")
    metadata = env_file.stat()
    permissions = stat.S_IMODE(metadata.st_mode)
    if metadata.st_uid != os.geteuid() or permissions & 0o077 or not permissions & 0o400:
        return fail(f"{env_file.name} 必须由当前部署用户拥有，且权限不得向组或其他用户开放")

    if not args.server:
        tracked = git("ls-files", "--error-unmatch", "--", ".env.prod")
        if tracked.returncode == 0:
            return fail(".env.prod 仍在 Git 索引中")
        ignored = git("check-ignore", "-q", "--", ".env.prod")
        if ignored.returncode != 0:
            return fail(".env.prod 尚未被 Git 忽略规则保护")

    values = parse_env(env_file)
    invalid = [
        key
        for key in REQUIRED
        if not values.get(key, "").strip()
        or PLACEHOLDER.search(values.get(key, ""))
        or len(values.get(key, "")) < MIN_LENGTHS.get(key, 1)
    ]
    if invalid:
        return fail("以下必需变量缺失或仍是占位值：" + ", ".join(invalid))
    if any(origin.strip() == "*" for origin in values["ALLOWED_ORIGINS"].split(",")):
        return fail("ALLOWED_ORIGINS 不得包含通配符")

    optional_invalid = validate_optional_services(values)
    if optional_invalid:
        return fail("可选服务配置不完整或不安全：" + ", ".join(optional_invalid))

    if not args.server:
        for compose_file in ("docker-compose.server.yml", "docker-compose.prod.yml"):
            result = subprocess.run(
                ["docker", "compose", "--file", compose_file, "--env-file", str(env_file), "config", "-q"],
                cwd=ROOT,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
            if result.returncode != 0:
                return fail(f"{compose_file} 配置校验失败；已隐藏 Compose 输出中的环境值")

    mode_label = "服务器环境" if args.server else "本地生产环境"
    print(f"{mode_label}预检通过：密钥文件受权限保护，必需及已启用服务配置齐全。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

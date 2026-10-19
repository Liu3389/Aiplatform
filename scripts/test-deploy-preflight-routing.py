#!/usr/bin/env python3
"""Guard local/remote preflight routing and production backend env pass-through."""

import json
import os
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
preflight = (ROOT / "scripts/local-release-preflight.sh").read_text(encoding="utf-8")
deploy = (ROOT / "scripts/server-deploy.sh").read_text(encoding="utf-8")
compose = ROOT / "docker-compose.server.yml"

assert 'PREFLIGHT_MODE="${1:-full}"' in preflight
assert '[[ "$PREFLIGHT_MODE" == "--local-only" ]]' in preflight
assert 'python3 "$ROOT/scripts/check-production-env.py"' in preflight
assert 'bash "$ROOT/scripts/local-release-preflight.sh" --local-only' in deploy

remote_config_check = deploy.index('docker compose -f docker-compose.server.yml --env-file .env.prod config -q')
first_transfer = deploy.index("scp ")
assert remote_config_check < first_transfer, "server Compose must be validated before image transfer"

required = {
    "MYSQL_PASSWORD": "test-mysql-value",
    "REDIS_PASSWORD": "test-redis-value",
    "API_ENCRYPTION_KEY": "test-encryption-value",
    "ALLOWED_ORIGINS": "https://example.invalid",
    "SECONDARY_PASSWORD": "test-secondary-value",
}
optional = {
    "RABBITMQ_ENABLED": "true",
    "RABBITMQ_HOST": "rabbit.example.invalid",
    "RABBITMQ_PORT": "5673",
    "RABBITMQ_USERNAME": "rabbit-user",
    "RABBITMQ_PASSWORD": "test-rabbit-value",
    "RABBITMQ_VHOST": "/platform",
    "ALIYUN_OSS_ENDPOINT": "https://oss.example.invalid",
    "ALIYUN_OSS_ACCESS_KEY_ID": "test-oss-id",
    "ALIYUN_OSS_ACCESS_KEY_SECRET": "test-oss-secret",
    "ALIYUN_OSS_BUCKET": "test-bucket",
    "ALIYUN_OSS_DOMAIN": "https://files.example.invalid",
    "SPRING_MAIL_HOST": "smtp.example.invalid",
    "SPRING_MAIL_PORT": "465",
    "SPRING_MAIL_USERNAME": "mail-user",
    "SPRING_MAIL_PASSWORD": "test-mail-value",
    "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH": "true",
    "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE": "false",
    "SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE": "true",
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


def compose_config(values: dict[str, str]) -> dict:
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=False) as env_file:
        env_path = Path(env_file.name)
        for key, value in values.items():
            env_file.write(f"{key}={value}\n")
    try:
        result = subprocess.run(
            ["docker", "compose", "-f", str(compose), "--env-file", str(env_path), "config", "--format", "json"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
    finally:
        env_path.unlink(missing_ok=True)
    if result.returncode != 0:
        raise AssertionError("Docker Compose rejected the server configuration; output suppressed to avoid exposing env values")
    return json.loads(result.stdout)


configured_backend_env = compose_config(required | optional)["services"]["backend"]["environment"]
for name, expected in optional.items():
    assert configured_backend_env.get(name) == expected, f"server Compose does not pass through {name}"

default_backend_env = compose_config(required)["services"]["backend"]["environment"]
assert default_backend_env.get("RABBITMQ_ENABLED") == "false", "RabbitMQ must remain opt-in by default"
assert default_backend_env.get("VERIFICATION_DELIVERY_MODE") == "disabled", "external delivery must remain disabled by default"
assert default_backend_env.get("VERIFICATION_ALLOW_LOCAL_CAPTURE") == "false", "local verification capture must stay off in production"
assert not default_backend_env.get("GOOGLE_OAUTH_CLIENT_ID"), "Google OAuth must remain unconfigured without production credentials"
assert not default_backend_env.get("GOOGLE_OAUTH_REDIRECT_URI"), "Google OAuth must fail closed without a production callback"

print("部署预检与服务器 Compose 变量透传通过：可选生产服务默认关闭；合成配置均传入后端，未启动或修改容器。")

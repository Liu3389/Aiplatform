#!/usr/bin/env python3
"""Guard local/remote preflight routing and production backend env pass-through."""

import json
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
preflight = (ROOT / "scripts/local-release-preflight.sh").read_text(encoding="utf-8")
deploy = (ROOT / "scripts/server-deploy.sh").read_text(encoding="utf-8")
compose = ROOT / "docker-compose.server.yml"
env_template = (ROOT / ".env.prod.example").read_text(encoding="utf-8")

assert 'PREFLIGHT_MODE="${1:-full}"' in preflight
assert '[[ "$PREFLIGHT_MODE" == "--local-only" ]]' in preflight
assert 'python3 "$ROOT/scripts/check-production-env.py"' in preflight
assert 'bash "$ROOT/scripts/local-release-preflight.sh" --local-only' in deploy

replacement_guard = deploy.index('AI_PLATFORM_ALLOW_IMAGE_AND_CONTAINER_REPLACEMENT')
local_preflight = deploy.index('bash "$ROOT/scripts/local-release-preflight.sh" --local-only')
remote_config_check = deploy.index('docker compose --env-file .env.prod -f - config -q')
remote_env_check = deploy.index('python3 - --server --env-file .env.prod')
existing_container_check = deploy.index("docker inspect --format '{{.State.Running}}' lingdian-mysql")
first_transfer = deploy.index("scp ")
assert replacement_guard < local_preflight < existing_container_check < remote_env_check < remote_config_check < first_transfer, "explicit replacement authorization, local tests, existing services, server env, and Compose must pass before transfer"
compose_transfer = deploy.index('"$ROOT/docker-compose.server.yml"', first_transfer)
candidate_config_check = deploy.index("docker compose -f .docker-compose.server.yml.next --env-file .env.prod config -q")
image_load = deploy.index("docker load -i 'aiplatform-backend:prod.tar'")
atomic_swap = deploy.index("mv -f .docker-compose.server.yml.next docker-compose.server.yml")
service_restart = deploy.index("docker compose -f docker-compose.server.yml --env-file .env.prod up -d redis backend frontend")
assert first_transfer < compose_transfer < candidate_config_check < image_load < atomic_swap < service_restart
assert "docker-compose.server.yml.backup.$DEPLOY_STAMP" in deploy, "old server Compose must be backed up before replacement"
assert 'up -d mysql' not in deploy, "deployment must leave the existing MySQL container untouched"

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
    "APP_STORAGE_PREFER_LOCAL": "false",
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


def compose_config(values: dict[str, str], *, read_from_stdin: bool = False, must_reject: bool = False) -> dict:
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=False) as env_file:
        env_path = Path(env_file.name)
        for key, value in values.items():
            env_file.write(f"{key}={value}\n")
    try:
        command = ["docker", "compose", "--env-file", str(env_path)]
        command += ["-f", "-"] if read_from_stdin else ["-f", str(compose)]
        command += ["config", "--format", "json"]
        result = subprocess.run(
            command,
            cwd=ROOT,
            input=compose.read_text(encoding="utf-8") if read_from_stdin else None,
            capture_output=True,
            text=True,
            check=False,
        )
    finally:
        env_path.unlink(missing_ok=True)
    if must_reject:
        assert result.returncode != 0 and "REDIS_PASSWORD_required" in result.stderr, "Compose must reject an empty production Redis password"
        return {}
    if result.returncode != 0:
        raise AssertionError("Docker Compose rejected the server configuration; output suppressed to avoid exposing env values")
    return json.loads(result.stdout)


configured_compose = compose_config(required | optional)
assert set(configured_compose["services"]) == {"mysql", "redis", "backend", "frontend"}, "server Compose must reuse only the existing four services"
configured_backend_env = configured_compose["services"]["backend"]["environment"]
configured_redis = configured_compose["services"]["redis"]
assert configured_redis["environment"].get("REDIS_PASSWORD") == required["REDIS_PASSWORD"]
assert configured_redis["command"][-2:] == ["--requirepass", required["REDIS_PASSWORD"]]
assert configured_redis["volumes"][0]["source"] == "redis-data" and configured_redis["volumes"][0]["target"] == "/data"
assert "REDISCLI_AUTH=$$REDIS_PASSWORD" in configured_redis["healthcheck"]["test"][1]
for name, expected in optional.items():
    assert configured_backend_env.get(name) == expected, f"server Compose does not pass through {name}"
    assert f"{name}=" in env_template, f"production template does not document {name}"
stdin_backend_env = compose_config(required | optional, read_from_stdin=True)["services"]["backend"]["environment"]
for name, expected in optional.items():
    assert stdin_backend_env.get(name) == expected, f"stdin Compose validation changes {name}"

assert "scripts/server-deploy.sh" in env_template, "production template must direct operators through the gated deploy script"
assert "docker-compose.prod.yml" not in env_template, "production template must not advertise the obsolete deployment command"

default_backend_env = compose_config(required)["services"]["backend"]["environment"]
assert default_backend_env.get("RABBITMQ_ENABLED") == "false", "RabbitMQ must remain opt-in by default"
assert default_backend_env.get("VERIFICATION_DELIVERY_MODE") == "disabled", "external delivery must remain disabled by default"
assert default_backend_env.get("VERIFICATION_ALLOW_LOCAL_CAPTURE") == "false", "local verification capture must stay off in production"
assert default_backend_env.get("APP_STORAGE_PREFER_LOCAL") == "true", "OSS must remain opt-in until explicitly enabled"
assert not default_backend_env.get("GOOGLE_OAUTH_CLIENT_ID"), "Google OAuth must remain unconfigured without production credentials"
assert not default_backend_env.get("GOOGLE_OAUTH_REDIRECT_URI"), "Google OAuth must fail closed without a production callback"
assert 'up -d redis backend frontend' in deploy and 'up -d mysql' not in deploy, "deployment may reconfigure existing Redis/app services, not recreate MySQL"
compose_config({**required, **optional, "REDIS_PASSWORD": ""}, must_reject=True)

print("部署预检与服务器 Compose 变量透传通过：可选生产服务默认关闭；合成配置均传入后端，未启动或修改容器。")

#!/usr/bin/env python3
"""Verify the in-place deployment path remains fail-closed and reuses containers."""

from __future__ import annotations

import os
from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / "scripts/deploy-existing-containers.sh"
REMOTE = ROOT / "scripts/deploy-existing-containers-remote.sh"
local_source = LOCAL.read_text(encoding="utf-8")
remote_source = REMOTE.read_text(encoding="utf-8")

for forbidden in ("docker compose", "docker load", "docker run", "docker rm", "up -d"):
    assert forbidden not in local_source and forbidden not in remote_source, f"forbidden container replacement command: {forbidden}"

for required in (
    "AI_PLATFORM_LEGAL_PAGES_APPROVED",
    "legal_draft_markers_present",
    "local-release-preflight.sh",
    "deploy-existing-containers-remote.sh",
):
    assert required in local_source, f"local guard missing: {required}"

for required in (
    "lingdian-backend",
    "lingdian-frontend",
    "lingdian-mysql",
    "lingdian-redis",
    "docker cp",
    "docker restart -t 30",
    "rollback_on_error",
    "backend.jar",
    "frontend/index.html",
    "api/auth/login/captcha",
    "api/actuator/health",
):
    assert required in remote_source, f"remote safety/verification step missing: {required}"

docker_subcommands = set(re.findall(r"\bdocker\s+([a-z]+)", remote_source))
assert docker_subcommands <= {"inspect", "exec", "cp", "restart"}, docker_subcommands
assert re.search(r'docker restart -t 30 "\$BACKEND"', remote_source)
assert "docker restart -t 30 \"$FRONTEND\"" not in remote_source
assert "verify_non_backend_containers_not_restarted" in remote_source

with tempfile.TemporaryDirectory(prefix="deploy-guard-test-") as temp:
    bin_dir = Path(temp) / "bin"
    bin_dir.mkdir()
    ssh_sentinel = Path(temp) / "ssh-was-called"
    ssh = bin_dir / "ssh"
    ssh.write_text(f"#!/bin/sh\ntouch '{ssh_sentinel}'\nexit 0\n", encoding="utf-8")
    ssh.chmod(0o700)
    key = Path(temp) / "fake-key"
    key.write_text("not a real key\n", encoding="utf-8")
    env = os.environ.copy()
    env.update(
        {
            "PATH": f"{bin_dir}:{env.get('PATH', '')}",
            "AI_PLATFORM_DEPLOY_HOST": "root@example.invalid",
            "AI_PLATFORM_DEPLOY_SSH_KEY": str(key),
            "AI_PLATFORM_LEGAL_PAGES_APPROVED": "true",
        }
    )
    blocked = subprocess.run([str(LOCAL), "--apply"], cwd=ROOT, env=env, capture_output=True, text=True)
    assert blocked.returncode == 2, (blocked.stdout, blocked.stderr)
    assert "法律页仍包含草案" in blocked.stderr
    assert not ssh_sentinel.exists(), "legal draft guard must block before any SSH call"
    planned = subprocess.run([str(LOCAL), "--plan"], cwd=ROOT, env=env, capture_output=True, text=True)
    assert planned.returncode == 0 and "计划模式" in planned.stdout, (planned.stdout, planned.stderr)
    assert not ssh_sentinel.exists(), "plan mode must not contact the server"

print("existing-container deployment safety checks passed")

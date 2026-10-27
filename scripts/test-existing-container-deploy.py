#!/usr/bin/env python3
"""Verify the in-place deployment path remains fail-closed and reuses containers."""

from __future__ import annotations

import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
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
    "migration-v19-user-avatar.sql",
    "TABLE_NAME=0x75736572",
    "brand/icon-macos-default.png",
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
        }
    )
    env.pop("AI_PLATFORM_LEGAL_PAGES_APPROVED", None)
    blocked = subprocess.run([str(LOCAL), "--apply"], cwd=ROOT, env=env, capture_output=True, text=True)
    assert blocked.returncode == 2, (blocked.stdout, blocked.stderr)
    assert "显式设置 AI_PLATFORM_LEGAL_PAGES_APPROVED=true" in blocked.stderr
    assert not ssh_sentinel.exists(), "missing publication authorization must block before any SSH call"
    planned = subprocess.run([str(LOCAL), "--plan"], cwd=ROOT, env=env, capture_output=True, text=True)
    assert planned.returncode == 0 and "计划模式" in planned.stdout, (planned.stdout, planned.stderr)
    assert not ssh_sentinel.exists(), "plan mode must not contact the server"

def create_remote_fixture(root: Path, *, fail_welcome: bool = False) -> tuple[Path, dict[str, str]]:
    deploy_root = root / "remote"
    container_root = root / "containers"
    stamp = "20261019T150000Z-123"
    release = deploy_root / "releases" / stamp
    release.mkdir(parents=True)
    (deploy_root / "backups").mkdir(parents=True)

    backend_dir = container_root / "lingdian-backend" / "app"
    frontend_root = container_root / "lingdian-frontend" / "usr/share/nginx/html"
    backend_dir.mkdir(parents=True)
    (container_root / "lingdian-mysql").mkdir(parents=True)
    (backend_dir / "app.jar").write_text("old-backend", encoding="utf-8")
    (frontend_root / "assets").mkdir(parents=True)
    (frontend_root / "index.html").write_text("old-frontend", encoding="utf-8")
    (frontend_root / "assets/old.js").write_text("old-asset", encoding="utf-8")
    assert (frontend_root / "index.html").is_file(), frontend_root

    new_backend = release / "backend.jar"
    new_backend.write_text("new-backend", encoding="utf-8")
    (release / "migration-v19-user-avatar.sql").write_text("SELECT 'migration-fixture';\n", encoding="utf-8")
    dist = root / "dist"
    (dist / "assets").mkdir(parents=True)
    (dist / "index.html").write_text(
        '<html><script type="module" src="/assets/app.js"></script>'
        '<link rel="stylesheet" href="/assets/app.css"></html>',
        encoding="utf-8",
    )
    (dist / "assets/app.js").write_text("new-asset", encoding="utf-8")
    (dist / "assets/app.css").write_text("new-style", encoding="utf-8")
    (dist / "brand").mkdir(parents=True)
    (dist / "brand/icon-macos-default.png").write_bytes(b"png fixture")
    with tarfile.open(release / "frontend.tar.gz", "w:gz") as archive:
        for child in dist.iterdir():
            archive.add(child, arcname=child.name)
    import hashlib

    sums = []
    for name in ("backend.jar", "frontend.tar.gz", "migration-v19-user-avatar.sql"):
        digest = hashlib.sha256((release / name).read_bytes()).hexdigest()
        sums.append(f"{digest}  {name}\n")
    (release / "SHA256SUMS").write_text("".join(sums), encoding="utf-8")

    test_script = root / "apply-existing-containers-remote.sh"
    test_script.write_text(
        remote_source.replace('REMOTE_ROOT="/root/lingdian"', f'REMOTE_ROOT="{deploy_root}"')
        .replace('PUBLIC_BASE="https://lingdian-ai.top"', 'PUBLIC_BASE="https://fixture.invalid"'),
        encoding="utf-8",
    )

    bin_dir = root / "bin"
    bin_dir.mkdir()
    docker = bin_dir / "docker"
    docker.write_text(
        r'''#!/usr/bin/env python3
import os, shutil, subprocess, sys
from pathlib import Path

root = Path(os.environ["MOCK_CONTAINER_ROOT"])
names = {"lingdian-backend", "lingdian-frontend", "lingdian-nginx", "lingdian-mysql", "lingdian-redis", "lingdian-mir-stream", "lingdian-hj666"}
args = sys.argv[1:]
with open(os.environ["MOCK_DOCKER_LOG"], "a", encoding="utf-8") as log:
    log.write(" ".join(args) + "\\n")

def mapped(name, path):
    path = path.removesuffix("/.").rstrip("/") or "/"
    if name == "lingdian-backend":
        base = root / name / "app"
        if path.startswith("/app."):
            return Path(str(base) + path.removeprefix("/app"))
        suffix = path.removeprefix("/app").lstrip("/")
        return base / suffix
    if name == "lingdian-frontend":
        base = root / name / "usr/share/nginx/html"
        if path.startswith("/usr/share/nginx/html."):
            return Path(str(base) + path.removeprefix("/usr/share/nginx/html"))
        suffix = path.removeprefix("/usr/share/nginx/html").lstrip("/")
        return base / suffix
    return root / name / path.lstrip("/")

def copy_contents(src, dst):
    dst.mkdir(parents=True, exist_ok=True)
    for child in src.iterdir():
        target = dst / child.name
        if child.is_dir():
            shutil.copytree(child, target, dirs_exist_ok=True)
        else:
            shutil.copy2(child, target)

def container_spec(value):
    prefix, sep, path = value.partition(":")
    if sep and prefix in names:
        return prefix, path
    return None

if args[0] == "inspect":
    template, name = args[2], args[3]
    if template == "{{.State.Running}}":
        print("true")
    elif template == "{{.Id}}":
        print("mock-id-" + name)
    elif template == "{{.State.StartedAt}}":
        state = Path(os.environ["MOCK_CONTAINER_ROOT"]) / "started" / name
        print(state.read_text(encoding="utf-8"))
    else:
        raise SystemExit("unexpected inspect template")
elif args[0] == "cp":
    source, dest = args[1], args[2]
    src_spec, dst_spec = container_spec(source), container_spec(dest)
    src_contents = source.endswith("/.")
    dst_contents = dest.endswith("/")
    src_path = mapped(*src_spec) if src_spec else Path(source.removesuffix("/."))
    dst_path = mapped(*dst_spec) if dst_spec else Path(dest)
    if src_contents:
        copy_contents(src_path, dst_path)
    elif src_path.is_dir():
        if dst_path.exists() and dst_path.is_dir():
            dst_path = dst_path / src_path.name
        shutil.copytree(src_path, dst_path, dirs_exist_ok=True)
    else:
        if dst_contents or (dst_path.exists() and dst_path.is_dir()):
            dst_path = dst_path / src_path.name
        dst_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src_path, dst_path)
elif args[0] == "exec":
    offset = 2 if args[1] == "-i" else 1
    name = args[offset]
    command = args[offset + 1:]
    if command[0] == "test" and command[1] == "-s":
        path = mapped(name, command[2])
        if not path.is_file() or path.stat().st_size == 0:
            print("mock docker test path missing: " + str(path) + "; parent=" + str(list(path.parent.iterdir()) if path.parent.exists() else "absent"), file=sys.stderr)
        raise SystemExit(0 if path.is_file() and path.stat().st_size > 0 else 1)
    if command[0] == "sh":
        script = command[-1]
        if name == "lingdian-mysql":
            if "information_schema.COLUMNS" in script:
                print("1")
            elif args[1] == "-i":
                migration = sys.stdin.buffer.read()
                (root / name / "migration-applied.sql").write_bytes(migration)
            else:
                raise SystemExit("unexpected mysql command: " + script)
            raise SystemExit(0)
        if name == "lingdian-backend":
            script = script.replace("/app/app.", str(root / name / "app") + "/app.")
        elif name == "lingdian-frontend":
            script = script.replace("/usr/share/nginx/html", str(root / name / "usr/share/nginx/html"))
        subprocess.run(["sh", "-eu", "-c", script], check=True)
    elif command[0] == "chmod":
        subprocess.run(["chmod", *command[1:-1], str(mapped(name, command[-1]))], check=True)
    elif command[0] == "rm":
        target = mapped(name, command[-1])
        if target.is_dir():
            shutil.rmtree(target, ignore_errors=True)
        else:
            target.unlink(missing_ok=True)
    else:
        raise SystemExit("unexpected exec command: " + " ".join(command))
elif args[0] == "restart":
    name = args[-1]
    state = Path(os.environ["MOCK_CONTAINER_ROOT"]) / "started" / name
    state.write_text("restarted-at-20261019T150100Z", encoding="utf-8")
else:
    raise SystemExit("unexpected docker subcommand: " + args[0])
''',
        encoding="utf-8",
    )
    docker.chmod(0o700)

    curl = bin_dir / "curl"
    curl.write_text(
        r'''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
from urllib.parse import urlparse

args = sys.argv[1:]
url = args[-1]
path = urlparse(url).path
out = None
write_code = False
for index, value in enumerate(args):
    if value == "-o":
        out = Path(args[index + 1])
    if value == "-w":
        write_code = True

containers = Path(os.environ["MOCK_CONTAINER_ROOT"])
frontend = containers / "lingdian-frontend/usr/share/nginx/html"
status, body = 200, b""
if path == "/api/actuator/health":
    body = b'{"status":"UP"}'
elif path == "/api/auth/login/captcha":
    body = json.dumps({"code":200,"data":{"captchaId":"fixture","image":"data:image/png;base64,AA=="}}).encode()
elif path == "/api/auth/oauth/providers":
    body = json.dumps({"code":200,"data":{"qq":True}}).encode()
elif path == "/api/auth/oauth/url":
    body = json.dumps({"code":200,"data":{"url":"https://graph.qq.com/oauth2.0/authorize?client_id=fixture&redirect_uri=https%3A%2F%2Ffixture.invalid%2Fcallback&state=fixture"}}).encode()
elif path in {"/welcome", "/", "/user"}:
    body = (frontend / "index.html").read_bytes()
    if os.environ.get("MOCK_FAIL_WELCOME") == "true":
        status = 503
elif path.startswith("/assets/"):
    asset = frontend / path.lstrip("/")
    if asset.is_file():
        body = asset.read_bytes()
    else:
        status, body = 404, b"missing"
elif path.startswith("/brand/"):
    asset = frontend / path.lstrip("/")
    if asset.is_file():
        body = asset.read_bytes()
    else:
        status, body = 404, b"missing"
else:
    body = b"ok"

if out is not None:
    out.write_bytes(body)
if write_code:
    print(status)
elif out is None:
    sys.stdout.buffer.write(body)
if "--fail" in args and status >= 400:
    raise SystemExit(22)
''',
        encoding="utf-8",
    )
    curl.chmod(0o700)

    sha256sum = bin_dir / "sha256sum"
    sha256sum.write_text(
        r'''#!/usr/bin/env python3
import hashlib, sys
from pathlib import Path

if sys.argv[1:3] != ["--check", "--status"]:
    raise SystemExit(2)
for line in Path(sys.argv[3]).read_text(encoding="utf-8").splitlines():
    expected, name = line.split("  ", 1)
    actual = hashlib.sha256(Path(name).read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit(1)
''',
        encoding="utf-8",
    )
    sha256sum.chmod(0o700)
    started = container_root / "started"
    started.mkdir(parents=True)
    for name in (
        "lingdian-backend", "lingdian-frontend", "lingdian-nginx", "lingdian-mysql",
        "lingdian-redis", "lingdian-mir-stream", "lingdian-hj666",
    ):
        (started / name).write_text("initial-start-time", encoding="utf-8")

    env = os.environ.copy()
    env.update(
        {
            "PATH": f"{bin_dir}:{env.get('PATH', '')}",
            "MOCK_CONTAINER_ROOT": str(container_root),
            "MOCK_DOCKER_LOG": str(root / "docker.log"),
            "MOCK_FAIL_WELCOME": "true" if fail_welcome else "false",
        }
    )
    return test_script, env


def simulate_remote_deploy(*, fail_welcome: bool) -> None:
    with tempfile.TemporaryDirectory(prefix="existing-deploy-sim-") as temp:
        root = Path(temp)
        script, env = create_remote_fixture(root, fail_welcome=fail_welcome)
        stamp = "20261019T150000Z-123"
        check = subprocess.run(["bash", str(script), "--check", stamp], env=env, capture_output=True, text=True, errors="replace")
        assert check.returncode == 0, (check.stdout, check.stderr)
        result = subprocess.run(["bash", str(script), "--apply", stamp], env=env, capture_output=True, text=True, errors="replace")
        expected_code = 1 if fail_welcome else 0
        if result.returncode != expected_code:
            debug = subprocess.run(["bash", "-x", str(script), "--apply", stamp], env=env, capture_output=True, text=True, errors="replace")
            log = Path(env["MOCK_DOCKER_LOG"]).read_text(encoding="utf-8")
            raise AssertionError((result.stdout, result.stderr, debug.stderr, log))
        container_root = Path(env["MOCK_CONTAINER_ROOT"])
        backend = container_root / "lingdian-backend/app/app.jar"
        frontend = container_root / "lingdian-frontend/usr/share/nginx/html/index.html"
        backup = root / "remote/backups" / stamp
        if fail_welcome:
            assert backend.read_text(encoding="utf-8") == "old-backend", "backend jar must roll back"
            assert frontend.read_text(encoding="utf-8") == "old-frontend", "frontend files must roll back"
        else:
            assert backend.read_text(encoding="utf-8") == "new-backend"
            assert "assets/app.js" in frontend.read_text(encoding="utf-8")
            assert (backup / "backend.jar").read_text(encoding="utf-8") == "old-backend"
            assert (backup / "frontend/index.html").read_text(encoding="utf-8") == "old-frontend"
            assert (root / "containers/lingdian-mysql/migration-applied.sql").is_file()
        for name in (
            "lingdian-frontend", "lingdian-nginx", "lingdian-mysql",
            "lingdian-redis", "lingdian-mir-stream", "lingdian-hj666",
        ):
            started = container_root / "started" / name
            assert started.read_text(encoding="utf-8") == "initial-start-time", f"{name} must not restart"
        assert result.stdout.count("应用验收通过") == (0 if fail_welcome else 1)


simulate_remote_deploy(fail_welcome=False)
simulate_remote_deploy(fail_welcome=True)
print("existing-container deployment safety checks passed")
print("remote deployment and automatic rollback simulations passed")

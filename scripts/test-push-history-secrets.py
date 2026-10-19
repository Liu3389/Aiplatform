#!/usr/bin/env python3
"""Exercise the pre-push secret scanner with disposable local repositories."""

from __future__ import annotations

import subprocess
import shutil
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCANNER = ROOT / "scripts" / "check-push-history-secrets.py"
ZERO_SHA = "0" * 40


def run(cwd: Path, *args: str, input_data: bytes | None = None) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        args,
        cwd=cwd,
        input=input_data,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def git(cwd: Path, *args: str) -> str:
    result = run(cwd, "git", *args)
    if result.returncode:
        raise AssertionError(f"git {args[0]} failed")
    return result.stdout.decode().strip()


def push_check(cwd: Path, tip: str, remote_sha: str = ZERO_SHA) -> subprocess.CompletedProcess[bytes]:
    update = f"refs/heads/main {tip} refs/heads/main {remote_sha}\n".encode()
    return run(cwd, "python3", str(SCANNER), input_data=update)


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="push-secret-guard-") as temporary:
        repo = Path(temporary)
        run(repo, "git", "init", "--quiet", "--initial-branch=main")
        git(repo, "config", "user.name", "Local Scanner Test")
        git(repo, "config", "user.email", "scanner@example.test")

        (repo / "scripts").mkdir()
        (repo / ".githooks").mkdir()
        shutil.copy2(SCANNER, repo / "scripts" / SCANNER.name)
        hook = repo / ".githooks" / "pre-push"
        shutil.copy2(ROOT / ".githooks" / "pre-push", hook)
        hook.chmod(0o755)
        git(repo, "config", "core.hooksPath", ".githooks")

        (repo / "safe.txt").write_text("ordinary local fixture\n", encoding="utf-8")
        git(repo, "add", "safe.txt", "scripts/check-push-history-secrets.py", ".githooks/pre-push")
        git(repo, "commit", "--quiet", "-m", "safe fixture")
        safe_tip = git(repo, "rev-parse", "HEAD")
        safe = push_check(repo, safe_tip)
        assert safe.returncode == 0, safe.stderr.decode(errors="replace")
        assert "通过".encode() in safe.stdout

        remote = repo / "remote.git"
        run(repo, "git", "init", "--quiet", "--bare", "--initial-branch=main", str(remote))
        git(repo, "remote", "add", "origin", str(remote))
        first_push = run(repo, "git", "push", "origin", "main")
        assert first_push.returncode == 0, first_push.stderr.decode(errors="replace")

        # Construct a synthetic value at runtime so the test source contains no credential-shaped literal.
        synthetic_value = "sk-" + "A" * 40

        message = f"synthetic key {synthetic_value}"
        git(repo, "tag", "-a", "message-fixture", safe_tip, "-m", message)
        tag_tip = git(repo, "rev-parse", "refs/tags/message-fixture")
        tag_check = push_check(repo, tag_tip)
        tag_diagnostic = tag_check.stderr.decode(errors="replace")
        assert tag_check.returncode == 1, tag_diagnostic
        assert "tag:" in tag_diagnostic, tag_diagnostic
        assert synthetic_value not in tag_diagnostic
        tag_push = run(repo, "git", "push", "origin", "refs/tags/message-fixture")
        assert tag_push.returncode != 0, tag_push.stdout.decode(errors="replace")
        assert synthetic_value.encode() not in tag_push.stderr
        assert run(remote, "git", "show-ref", "--verify", "--quiet", "refs/tags/message-fixture").returncode != 0

        (repo / "message.txt").write_text("safe tree content\n", encoding="utf-8")
        git(repo, "add", "message.txt")
        git(repo, "commit", "--quiet", "-m", message)
        message_tip = git(repo, "rev-parse", "HEAD")
        message_check = push_check(repo, message_tip)
        message_diagnostic = message_check.stderr.decode(errors="replace")
        assert message_check.returncode == 1, message_diagnostic
        assert "commit:" in message_diagnostic, message_diagnostic
        assert synthetic_value not in message_diagnostic
        message_push = run(repo, "git", "push", "origin", "main")
        assert message_push.returncode != 0, message_push.stdout.decode(errors="replace")
        assert synthetic_value.encode() not in message_push.stderr

        (repo / "legacy.txt").write_text(f"provider_key={synthetic_value}\n", encoding="utf-8")
        git(repo, "add", "legacy.txt")
        git(repo, "commit", "--quiet", "-m", "synthetic credential fixture")
        unsafe_tip = git(repo, "rev-parse", "HEAD")
        unsafe = push_check(repo, unsafe_tip, safe_tip)
        diagnostic = unsafe.stderr.decode(errors="replace")
        assert unsafe.returncode == 1, diagnostic
        assert "legacy.txt:1" in diagnostic, diagnostic
        assert "provider-api-key" in diagnostic, diagnostic
        assert synthetic_value not in diagnostic
        blob_push = run(repo, "git", "push", "origin", "main")
        assert blob_push.returncode != 0, blob_push.stdout.decode(errors="replace")
        assert synthetic_value.encode() not in blob_push.stderr

        # A clean child cannot hide a flagged ancestor from a clean remote base.
        (repo / "clean-child.txt").write_text("no credential in this new file\n", encoding="utf-8")
        git(repo, "add", "clean-child.txt")
        git(repo, "commit", "--quiet", "-m", "clean child after synthetic credential")
        clean_child = git(repo, "rev-parse", "HEAD")
        still_unsafe = push_check(repo, clean_child, safe_tip)
        diagnostic = still_unsafe.stderr.decode(errors="replace")
        assert still_unsafe.returncode == 1, diagnostic
        assert synthetic_value not in diagnostic

    print("推送历史扫描通过：真实 Git 推送可通过干净历史；文件、提交说明、标签及其后续历史中的模拟凭据均被拒；诊断不回显凭据值。")


if __name__ == "__main__":
    main()

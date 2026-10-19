#!/usr/bin/env python3
"""Reject staged environment files and real-looking values in templates."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit

SENSITIVE_NAME = re.compile(
    r"(secret|password|passwd|token|api[_-]?key|encryption[_-]?key|private|credential|access[_-]?key)", re.I
)
PLACEHOLDER = re.compile(
    r"(\$\{|example|placeholder|change[_ -]?me|replace|set[_ -]?me|your[_ -]?|dummy|local[_ -]?only|test[_ -]?key|^sk-local)",
    re.I,
)
HIGH_CONFIDENCE_SECRET_PATTERNS = (
    ("provider-api-key", re.compile(r"\bsk-[A-Za-z0-9_-]{32,}\b")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{30,}\b")),
    ("google-api-key", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("stripe-live-key", re.compile(r"\b(?:sk|pk)_live_[A-Za-z0-9]{20,}\b")),
    ("private-key-block", re.compile(r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----")),
)


def staged_paths() -> list[str]:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"],
        check=True,
        stdout=subprocess.PIPE,
    )
    return [part.decode("utf-8", "surrogateescape") for part in result.stdout.split(b"\0") if part]


def tracked_paths() -> list[str]:
    result = subprocess.run(["git", "ls-files", "-z"], check=True, stdout=subprocess.PIPE)
    return [part.decode("utf-8", "surrogateescape") for part in result.stdout.split(b"\0") if part]


def untracked_paths() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard", "-z"],
        check=True,
        stdout=subprocess.PIPE,
    )
    return [part.decode("utf-8", "surrogateescape") for part in result.stdout.split(b"\0") if part]


def staged_content(path: str) -> bytes:
    result = subprocess.run(
        ["git", "show", f":{path}"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout


def is_environment_file(path: str) -> bool:
    name = PurePosixPath(path).name
    return name == ".env" or name.startswith(".env.")


def is_template(path: str) -> bool:
    return PurePosixPath(path).name.endswith((".example", ".sample"))


def template_secrets(path: str, data: bytes) -> list[int]:
    invalid_lines: list[int] = []
    for line_number, raw in enumerate(data.decode("utf-8", "replace").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        value = value.strip().strip("\"'")
        if SENSITIVE_NAME.search(name) and value and not PLACEHOLDER.search(value):
            invalid_lines.append(line_number)
    return invalid_lines


def high_confidence_secrets(data: bytes) -> list[tuple[int, str]]:
    matches: list[tuple[int, str]] = []
    for line_number, raw in enumerate(data.decode("utf-8", "replace").splitlines(), start=1):
        for label, pattern in HIGH_CONFIDENCE_SECRET_PATTERNS:
            if pattern.search(raw):
                matches.append((line_number, label))
    return matches


def remotes_with_embedded_credentials() -> list[str]:
    result = subprocess.run(
        ["git", "config", "--local", "--get-regexp", r"^remote\..*\.url$"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    names: list[str] = []
    for row in result.stdout.splitlines():
        key, separator, value = row.partition(" ")
        if not separator:
            continue
        parsed = urlsplit(value)
        if parsed.username is not None or parsed.password is not None:
            match = re.match(r"remote\.(.+)\.url$", key)
            names.append(match.group(1) if match else "unknown")
    return names


def main() -> int:
    blocked: set[tuple[str, str]] = set()
    scanned_templates: set[str] = set()
    staged = staged_paths()
    for path in staged:
        data = staged_content(path)
        if not is_environment_file(path):
            for line_number, label in high_confidence_secrets(data):
                blocked.add((f"{path}:{line_number}", f"high-confidence secret pattern: {label}"))
        else:
            if not is_template(path):
                blocked.add((path, "environment files may not be committed"))
                continue
            scanned_templates.add(path)
            for line_number in template_secrets(path, data):
                blocked.add((f"{path}:{line_number}", "template contains a non-placeholder secret value"))

    # Check the resulting tracked tree as well, so commits cannot carry old
    # high-confidence credentials merely because an affected file was not staged.
    tracked = tracked_paths()
    for path in tracked:
        if is_environment_file(path):
            index_data = staged_content(path)
            if not is_template(path):
                blocked.add((path, "tracked environment files may not remain in the index"))
                continue
            scanned_templates.add(path)
            for line_number in template_secrets(path, index_data):
                blocked.add((f"{path}:{line_number}", "template contains a non-placeholder secret value"))

        file_path = Path(path)
        if not file_path.is_file() or file_path.is_symlink():
            continue
        data = file_path.read_bytes()
        if is_environment_file(path):
            scanned_templates.add(path)
            for line_number in template_secrets(path, data):
                blocked.add((f"{path}:{line_number}", "template contains a non-placeholder secret value"))
        else:
            for line_number, label in high_confidence_secrets(data):
                blocked.add((f"{path}:{line_number}", f"high-confidence secret pattern: {label}"))

    untracked = untracked_paths()
    for path in untracked:
        if not is_environment_file(path) or not is_template(path):
            continue
        file_path = Path(path)
        if not file_path.is_file() or file_path.is_symlink():
            continue
        scanned_templates.add(path)
        for line_number in template_secrets(path, file_path.read_bytes()):
            blocked.add((f"{path}:{line_number}", "template contains a non-placeholder secret value"))

    for remote_name in remotes_with_embedded_credentials():
        blocked.add((f".git/config remote {remote_name}", "remote URL contains embedded credentials; use a credential helper"))

    if blocked:
        for path, reason in sorted(blocked):
            print(f"拒绝提交 {path}: {reason}", file=sys.stderr)
        return 1

    print(
        f"凭据检查通过：扫描 {len(staged)} 个暂存变更、{len(tracked)} 个当前跟踪文件、"
        f"{len(untracked)} 个未跟踪文件，环境模板 {len(scanned_templates)} 个。"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

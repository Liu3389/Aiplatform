#!/usr/bin/env python3
"""Reject pushes containing high-confidence credentials or oversized Git blobs."""

from __future__ import annotations

import re
import subprocess
import sys
from collections import defaultdict

MAX_GIT_BLOB_BYTES = 100 * 1024 * 1024


PATTERNS = (
    ("provider-api-key", re.compile(rb"\bsk-[A-Za-z0-9_-]{32,}\b")),
    ("aws-access-key", re.compile(rb"\bAKIA[0-9A-Z]{16}\b")),
    ("github-token", re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,}\b")),
    ("google-api-key", re.compile(rb"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("stripe-live-key", re.compile(rb"\b(?:sk|pk)_live_[A-Za-z0-9]{20,}\b")),
    ("private-key-block", re.compile(rb"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----")),
)


def git(*args: str, input_data: bytes | None = None) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", *args],
        input=input_data,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def pushed_tips(stdin: bytes) -> list[str]:
    tips: list[str] = []
    for raw_line in stdin.splitlines():
        fields = raw_line.split()
        if len(fields) != 4:
            raise ValueError("Git supplied a malformed pre-push ref update; refusing push.")
        _, local_sha, _, _ = fields
        if local_sha and set(local_sha) != {ord("0")}:
            tips.append(local_sha.decode("ascii"))
    return tips


def reachable_objects(tips: list[str]) -> tuple[dict[str, set[str]], set[str]]:
    paths_by_object: dict[str, set[str]] = defaultdict(set)
    commits: set[str] = set()
    for tip in tips:
        result = git("rev-list", "--objects", tip)
        if result.returncode != 0:
            raise RuntimeError("Unable to enumerate a pushed ref history; refusing push.")
        for row in result.stdout.splitlines():
            fields = row.split(b" ", 1)
            oid = fields[0].decode("ascii")
            if len(fields) == 1:
                commits.add(oid)
            else:
                paths_by_object[oid].add(fields[1].decode("utf-8", "replace"))
    return paths_by_object, commits


def find_oversized_blobs(
    paths_by_object: dict[str, set[str]], max_bytes: int | None = None
) -> list[tuple[str, int]]:
    if max_bytes is None:
        max_bytes = MAX_GIT_BLOB_BYTES
    object_ids = sorted(paths_by_object)
    if not object_ids:
        return []
    request = b"".join(oid.encode("ascii") + b"\n" for oid in object_ids)
    result = git("cat-file", "--batch-check", input_data=request)
    if result.returncode != 0:
        raise RuntimeError("Unable to check pushed Git object sizes; refusing push.")

    oversized: set[tuple[str, int]] = set()
    rows = result.stdout.splitlines()
    if len(rows) != len(object_ids):
        raise RuntimeError("Git returned an incomplete object-size stream; refusing push.")
    for oid, row in zip(object_ids, rows):
        fields = row.split()
        if len(fields) < 3 or fields[1] == b"missing":
            raise RuntimeError("A pushed Git object is missing; refusing push.")
        if fields[1] != b"blob":
            continue
        size = int(fields[2])
        if size <= max_bytes:
            continue
        for location in paths_by_object.get(oid, set()) or {f"object:{oid[:12]}"}:
            oversized.add((location, size))
    return sorted(oversized)


def scan_objects(object_ids: list[str], paths_by_object: dict[str, set[str]]) -> list[tuple[str, str]]:
    if not object_ids:
        return []
    request = b"".join(oid.encode("ascii") + b"\n" for oid in object_ids)
    result = git("cat-file", "--batch", input_data=request)
    if result.returncode != 0:
        raise RuntimeError("Unable to read pushed Git objects; refusing push.")

    findings: set[tuple[str, str]] = set()
    cursor = 0
    for oid in object_ids:
        newline = result.stdout.find(b"\n", cursor)
        if newline < 0:
            raise RuntimeError("Git returned a truncated object stream; refusing push.")
        header = result.stdout[cursor:newline].split()
        cursor = newline + 1
        if len(header) < 3 or header[1] == b"missing":
            raise RuntimeError("A pushed Git object is missing; refusing push.")
        kind = header[1]
        size = int(header[2])
        data = result.stdout[cursor : cursor + size]
        if len(data) != size:
            raise RuntimeError("Git returned a truncated object body; refusing push.")
        cursor += size + 1
        if kind not in (b"blob", b"commit", b"tag"):
            continue

        if kind == b"blob":
            locations = sorted(paths_by_object.get(oid, set()))
        else:
            locations = [f"{kind.decode()}:{oid[:12]}"]
        for label, pattern in PATTERNS:
            for match in pattern.finditer(data):
                line_number = data.count(b"\n", 0, match.start()) + 1
                for location in locations or [f"object:{oid[:12]}"]:
                    findings.add((label, f"{location}:{line_number}"))
    return sorted(findings)


def main() -> int:
    try:
        tips = pushed_tips(sys.stdin.buffer.read())
        paths_by_object, commits = reachable_objects(tips)
        oversized = find_oversized_blobs(paths_by_object)
        tag_objects = {
            tip
            for tip in tips
            if (result := git("cat-file", "-t", tip)).returncode == 0 and result.stdout.strip() == b"tag"
        }
        findings = (
            scan_objects(sorted(set(paths_by_object) | commits | tag_objects), paths_by_object)
            if not oversized
            else []
        )
    except (ValueError, RuntimeError) as error:
        print(f"推送安全检查失败关闭：{error}", file=sys.stderr)
        return 2

    if oversized:
        for location, size in oversized[:100]:
            print(
                f"拒绝推送 {location}: Git 文件对象 {size} bytes 超过 100 MiB 安全上限。",
                file=sys.stderr,
            )
        if len(oversized) > 100:
            print(f"另有 {len(oversized) - 100} 个超限文件未展开。", file=sys.stderr)
        print("推送目标历史包含超大文件；请从目标历史中移除或改用 Git LFS。", file=sys.stderr)
        return 1

    if findings:
        for label, location in findings[:100]:
            print(f"拒绝推送 {location}: 高置信度凭据格式 ({label})", file=sys.stderr)
        if len(findings) > 100:
            print(f"另有 {len(findings) - 100} 个命中位置未展开。", file=sys.stderr)
        print("推送目标历史仍包含凭据格式命中；先核验凭据并清理目标历史。", file=sys.stderr)
        return 1

    print(f"推送历史凭据检查通过：扫描 {len(tips)} 个目标引用、{len(paths_by_object)} 个文件对象和 {len(commits)} 个提交。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Start a local development process in its own session and record its PID."""

import os
import subprocess
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 6 or sys.argv[4] != "--":
        print(
            "usage: launch-local-process.py PID_FILE LOG_FILE WORKDIR -- COMMAND [ARG ...]",
            file=sys.stderr,
        )
        return 2

    pid_file = Path(sys.argv[1])
    log_file = Path(sys.argv[2])
    workdir = sys.argv[3]
    command = sys.argv[5:]
    pid_file.parent.mkdir(parents=True, exist_ok=True)
    log_file.parent.mkdir(parents=True, exist_ok=True)

    with log_file.open("ab", buffering=0) as output:
        process = subprocess.Popen(
            command,
            cwd=workdir,
            env=os.environ.copy(),
            stdin=subprocess.DEVNULL,
            stdout=output,
            stderr=subprocess.STDOUT,
            close_fds=True,
            start_new_session=True,
        )

    pid_file.write_text(f"{process.pid}\n", encoding="ascii")
    print(process.pid)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

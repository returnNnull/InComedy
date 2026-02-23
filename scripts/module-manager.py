#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import sys
import termios
import tty
from pathlib import Path


def die(message: str) -> None:
    print(f"Error: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_key() -> str:
    ch = sys.stdin.read(1)
    if ch == "\x1b":
        seq = sys.stdin.read(2)
        if seq == "[A":
            return "UP"
        if seq == "[B":
            return "DOWN"
        return "ESC"
    if ch in {"\r", "\n"}:
        return "ENTER"
    if ch == "\x03":
        return "CTRL_C"
    return "OTHER"


def pick_action_with_arrows() -> str:
    if not sys.stdin.isatty() or not sys.stdout.isatty():
        die("Interactive selection requires a TTY")

    options = ["Add module", "Remove module", "Exit"]
    index = 0

    fd = sys.stdin.fileno()
    old_settings = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        while True:
            lines = ["=== Module Manager ===", "Use ↑/↓ and Enter", ""]
            for i, option in enumerate(options):
                prefix = "> " if i == index else "  "
                lines.append(f"{prefix}{option}")
            lines.append("")
            frame = "\r\n".join(lines)

            sys.stdout.write("\033[2J\033[H")
            sys.stdout.write(frame)
            sys.stdout.write("\r\n")
            sys.stdout.flush()

            key = read_key()
            if key == "UP":
                index = (index - 1) % len(options)
            elif key == "DOWN":
                index = (index + 1) % len(options)
            elif key == "ENTER":
                return ["add", "remove", "exit"][index]
            elif key == "CTRL_C":
                raise KeyboardInterrupt
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
        termios.tcflush(fd, termios.TCIFLUSH)
        sys.stdout.write("\033[2J\033[H")
        sys.stdout.flush()


def main() -> int:
    scripts_dir = Path(__file__).resolve().parent

    try:
        action = pick_action_with_arrows()
    except KeyboardInterrupt:
        print("Cancelled.")
        return 1

    if action == "exit":
        print("Cancelled.")
        return 0

    if action == "add":
        target = scripts_dir / "new-module.py"
    else:
        target = scripts_dir / "remove-module.py"

    if not target.exists():
        print(f"Error: script not found: {target}", file=sys.stderr)
        return 1

    result = subprocess.run([str(target), "--interactive"])
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())

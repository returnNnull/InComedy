#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import termios
import tty
from pathlib import Path


def die(message: str) -> None:
    print(f"Error: {message}", file=sys.stderr)
    raise SystemExit(1)


def normalize_module(module: str) -> str:
    value = module.replace("/", ":").strip()
    if not value:
        die("Module name cannot be empty")
    if not value.startswith(":"):
        value = f":{value}"
    return value


def module_to_dir(module: str) -> Path:
    return Path(module.lstrip(":").replace(":", "/"))


def module_to_accessor(module: str) -> str:
    return f"projects.{module.lstrip(':').replace(':', '.')}"


def load_modules(settings_file: Path) -> list[str]:
    modules: list[str] = []
    pattern = re.compile(r'^\s*include\("([^"]+)"\)')
    for line in settings_file.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line)
        if match:
            modules.append(match.group(1))
    return modules


def prompt_yes_no(question: str, default_no: bool = True) -> bool:
    suffix = "[y/N]" if default_no else "[Y/n]"
    default = False if default_no else True
    while True:
        answer = safe_input(f"{question} {suffix}: ").strip().lower()
        if not answer:
            return default
        if answer in {"y", "yes"}:
            return True
        if answer in {"n", "no"}:
            return False
        print("Please answer y or n.")


def safe_input(prompt: str = "") -> str:
    if prompt:
        sys.stdout.write(prompt)
        sys.stdout.flush()
    try:
        return input()
    except UnicodeDecodeError:
        # Fallback for terminals that leave undecodable bytes in stdin buffer.
        buf = bytearray()
        fd = sys.stdin.fileno()
        while True:
            b = os.read(fd, 1)
            if not b or b in {b"\n", b"\r"}:
                break
            buf.extend(b)
        return buf.decode("utf-8", errors="ignore")


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


def pick_module_with_arrows(modules: list[str]) -> str:
    if not sys.stdin.isatty() or not sys.stdout.isatty():
        die("Interactive selection requires a TTY")

    index = 0

    fd = sys.stdin.fileno()
    old_settings = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        while True:
            lines = ["=== Remove Module Wizard ===", "Use ↑/↓ and Enter", ""]
            for i, module in enumerate(modules):
                prefix = "> " if i == index else "  "
                lines.append(f"{prefix}{module}")
            lines.append("")
            frame = "\r\n".join(lines)

            sys.stdout.write("\033[2J\033[H")
            sys.stdout.write(frame)
            sys.stdout.write("\r\n")
            sys.stdout.flush()

            key = read_key()
            if key == "UP":
                index = (index - 1) % len(modules)
            elif key == "DOWN":
                index = (index + 1) % len(modules)
            elif key == "ENTER":
                return modules[index]
            elif key == "CTRL_C":
                raise KeyboardInterrupt
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
        termios.tcflush(fd, termios.TCIFLUSH)
        sys.stdout.write("\033[2J\033[H")
        sys.stdout.flush()


def remove_include(settings_file: Path, module: str) -> None:
    include_line = f'include("{module}")'
    lines = settings_file.read_text(encoding="utf-8").splitlines()
    filtered = [line for line in lines if line.strip() != include_line]
    settings_file.write_text("\n".join(filtered) + "\n", encoding="utf-8")
    print(f"updated {settings_file}")


def remove_module_references(project_root: Path, module: str) -> None:
    accessor = module_to_accessor(module)
    needle_project = f'project("{module}")'

    for file in project_root.rglob("build.gradle.kts"):
        text = file.read_text(encoding="utf-8")
        lines = text.splitlines()
        filtered = [line for line in lines if needle_project not in line and accessor not in line]
        if filtered != lines:
            file.write_text("\n".join(filtered) + "\n", encoding="utf-8")

    print(f"removed references to {module} in build.gradle.kts files")


def run_gradle_sync(project_root: Path) -> None:
    print("Running Gradle sync...")
    subprocess.run(["./gradlew", "help", "--no-daemon"], cwd=project_root, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Remove a module")
    parser.add_argument("module", nargs="?")
    parser.add_argument("--no-sync", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--interactive", action="store_true")
    args = parser.parse_args()

    project_root = Path(__file__).resolve().parents[1]
    settings_file = project_root / "settings.gradle.kts"
    if not settings_file.exists():
        die(f"File not found: {settings_file}")

    interactive = args.interactive or args.module is None

    if interactive:
        modules = load_modules(settings_file)
        if not modules:
            die("No modules found in settings.gradle.kts")
        try:
            module = pick_module_with_arrows(modules)
        except KeyboardInterrupt:
            print("Cancelled.")
            raise SystemExit(1)
        print(f"Selected module: {module}")
    else:
        module = normalize_module(args.module)

    module = normalize_module(module)
    module_dir = project_root / module_to_dir(module)

    if not module_dir.exists():
        die(f"Module directory not found: {module_dir.relative_to(project_root)}")

    if not args.force:
        print(f"Module: {module}")
        print(f"Directory to delete: {module_dir.relative_to(project_root)}")
        if not prompt_yes_no("Delete this module?"):
            print("Cancelled.")
            raise SystemExit(0)

    remove_include(settings_file, module)
    remove_module_references(project_root, module)
    shutil.rmtree(module_dir)
    print(f"deleted {module_dir.relative_to(project_root)}")

    if not args.no_sync:
        run_gradle_sync(project_root)

    print("Done.")


if __name__ == "__main__":
    main()

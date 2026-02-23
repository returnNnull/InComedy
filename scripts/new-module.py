#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
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


def to_pascal_case(value: str) -> str:
    parts = re.split(r"[-_/.:]", value)
    joined = "".join(part[:1].upper() + part[1:] for part in parts if part)
    return joined or "Module"


def to_package_part(value: str) -> str:
    return value.replace("-", ".").replace("_", ".")


def append_include_if_missing(settings_file: Path, include_line: str) -> None:
    text = settings_file.read_text(encoding="utf-8")
    if include_line in text.splitlines():
        print(f"exists in {settings_file}: {include_line}")
        return
    settings_file.write_text(text.rstrip("\n") + f"\n\n{include_line}\n", encoding="utf-8")
    print(f"updated {settings_file}")


def prompt_yes_no(question: str, default_yes: bool = False) -> bool:
    suffix = "[Y/n]" if default_yes else "[y/N]"
    while True:
        answer = safe_input(f"{question} {suffix}: ").strip().lower()
        if not answer:
            return default_yes
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
        buf = bytearray()
        fd = sys.stdin.fileno()
        while True:
            b = os.read(fd, 1)
            if not b or b in {b"\n", b"\r"}:
                break
            buf.extend(b)
        return buf.decode("utf-8", errors="ignore")


def run_gradle_sync(project_root: Path) -> None:
    print("Running Gradle sync...")
    subprocess.run(["./gradlew", "help", "--no-daemon"], cwd=project_root, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a KMP module")
    parser.add_argument("module", nargs="?", help="Module path like :feature:auth or feature/auth/login")
    parser.add_argument("--no-sync", action="store_true")
    parser.add_argument("--interactive", action="store_true")
    args = parser.parse_args()

    interactive = args.interactive or args.module is None

    if interactive:
        if not sys.stdin.isatty():
            die("Interactive mode requires a TTY")
        print("\n=== New Module Wizard ===")
        module_input = safe_input("Module (e.g. :feature:auth or feature/auth/login): ").strip()
        if not module_input:
            die("Module is required")
        if not prompt_yes_no(f"Create module '{module_input}'?", default_yes=False):
            print("Cancelled.")
            raise SystemExit(0)
    else:
        module_input = args.module or ""

    new_module = normalize_module(module_input)
    segments = [s for s in new_module.lstrip(":").split(":") if s]
    if len(segments) < 2:
        die("Use at least two segments, e.g. :feature:auth")

    layer = segments[0]
    plugin_by_layer = {
        "core": "incomedy.kmp.library",
        "data": "incomedy.data",
        "feature": "incomedy.feature",
    }
    plugin_id = plugin_by_layer.get(layer)
    if not plugin_id:
        die("First segment must be one of: core, data, feature")

    if not re.match(r"^[a-zA-Z0-9._:/-]+$", new_module):
        die(f"Invalid module '{new_module}'")

    project_root = Path(__file__).resolve().parents[1]
    settings_file = project_root / "settings.gradle.kts"
    if not settings_file.exists():
        die(f"File not found: {settings_file}")

    new_dir = project_root / module_to_dir(new_module)
    new_build_file = new_dir / "build.gradle.kts"

    if new_build_file.exists():
        die(f"Module already exists: {new_module} ({new_build_file.relative_to(project_root)})")

    package_suffix = ".".join(to_package_part(s) for s in segments)
    package_name = f"com.bam.incomedy.{package_suffix}"
    class_name = f"{to_pascal_case(segments[-1])}Module"
    class_file = new_dir / "src/commonMain/kotlin" / Path(package_name.replace(".", "/")) / f"{class_name}.kt"

    build_content = f"""plugins {{
    id(\"{plugin_id}\")
}}

kotlin {{
    androidLibrary {{
        namespace = \"{package_name}\"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }}
}}
"""

    class_content = f"""package {package_name}

object {class_name}
"""

    print(f"Creating module {new_module}")
    new_build_file.parent.mkdir(parents=True, exist_ok=True)
    new_build_file.write_text(build_content, encoding="utf-8")
    print(f"created {new_build_file.relative_to(project_root)}")

    class_file.parent.mkdir(parents=True, exist_ok=True)
    class_file.write_text(class_content, encoding="utf-8")
    print(f"created {class_file.relative_to(project_root)}")

    append_include_if_missing(settings_file, f'include("{new_module}")')

    if not args.no_sync:
        run_gradle_sync(project_root)

    print("Done.")


if __name__ == "__main__":
    main()

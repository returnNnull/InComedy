#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/new-module.sh <layer> <name> [--connect-to <module>] [--no-sync]
  scripts/new-module.sh --interactive

Arguments:
  <layer>      core | data | feature
  <name>       module name, e.g. logger, profile, chats

Options:
  --connect-to <module>  module that should depend on the new module.
                         Supported formats: :data:chats, data:chats, data/chats
  --no-sync              skip Gradle sync after module creation
  --interactive          ask all parameters in console UI

Examples:
  scripts/new-module.sh core logger
  scripts/new-module.sh data profile --connect-to feature:chats
  scripts/new-module.sh --interactive
USAGE
}

log() {
  printf '%s\n' "$*"
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || die "File not found: $path"
}

normalize_module_colon() {
  local input="$1"
  input="${input//\//:}"
  [[ "$input" == :* ]] || input=":$input"
  printf '%s' "$input"
}

module_to_dir() {
  local module="$1"
  printf '%s' "${module#:}" | tr ':' '/'
}

module_to_accessor() {
  local module="$1"
  local raw="${module#:}"
  printf '%s' "projects.${raw//:/.}"
}

to_package_suffix() {
  local s="$1"
  s="${s//-/.}"
  s="${s//_/.}"
  s="${s//\//.}"
  printf '%s' "$s"
}

to_pascal_case() {
  local input="$1"
  local IFS='-_/'
  read -ra parts <<< "$input"
  local out=""
  local p
  for p in "${parts[@]}"; do
    [[ -z "$p" ]] && continue
    out+="${p^}"
  done
  printf '%s' "$out"
}

write_file() {
  local path="$1"
  local content="$2"
  mkdir -p "$(dirname "$path")"
  printf '%s' "$content" > "$path"
  log "created $path"
}

append_line_if_missing() {
  local file="$1"
  local line="$2"

  if grep -Fqx "$line" "$file"; then
    log "exists in $file: $line"
    return
  fi

  printf '\n%s\n' "$line" >> "$file"
  log "updated $file"
}

insert_dependency_into_module() {
  local target_module="$1"
  local dependency_accessor="$2"

  local target_dir
  target_dir="$(module_to_dir "$target_module")"
  local target_file="$target_dir/build.gradle.kts"

  require_file "$target_file"

  local dep_line="            implementation($dependency_accessor)"
  if grep -Fq "$dep_line" "$target_file"; then
    log "dependency already exists in $target_file"
    return
  fi

  if grep -Fq "commonMain.dependencies" "$target_file"; then
    local tmp
    tmp="$(mktemp)"
    awk -v dep="$dep_line" '
      BEGIN { added = 0 }
      {
        print
        if ($0 ~ /commonMain\.dependencies\s*\{/) {
          print dep
          added = 1
        }
      }
      END {
        if (added == 0) exit 2
      }
    ' "$target_file" > "$tmp" || {
      rm -f "$tmp"
      die "Could not insert dependency into $target_file"
    }
    mv "$tmp" "$target_file"
    log "updated $target_file"
    return
  fi

  local block
  block=$(cat <<EOF_BLOCK

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation($dependency_accessor)
        }
    }
}
EOF_BLOCK
)

  printf '%s\n' "$block" >> "$target_file"
  log "updated $target_file"
}

prompt_yes_no() {
  local question="$1"
  local default="${2:-y}"
  local answer=""
  local prompt_suffix="[y/N]"
  [[ "$default" == "y" ]] && prompt_suffix="[Y/n]"

  while true; do
    read -rp "$question $prompt_suffix: " answer
    answer="${answer,,}"
    if [[ -z "$answer" ]]; then
      answer="$default"
    fi
    case "$answer" in
      y|yes) return 0 ;;
      n|no) return 1 ;;
      *) printf 'Please answer y or n.\n' >&2 ;;
    esac
  done
}

prompt_layer() {
  local answer=""
  while true; do
    printf '\nSelect layer:\n' >&2
    printf '  1) core\n' >&2
    printf '  2) data\n' >&2
    printf '  3) feature\n' >&2
    read -rp "Choice [1-3]: " answer
    case "$answer" in
      1|core) printf 'core'; return ;;
      2|data) printf 'data'; return ;;
      3|feature) printf 'feature'; return ;;
      *) printf 'Invalid choice.\n' >&2 ;;
    esac
  done
}

prompt_module_name() {
  local answer=""
  while true; do
    read -rp "Module name (e.g. logger, profile/chats): " answer
    if [[ -z "$answer" ]]; then
      printf 'Name is required.\n' >&2
      continue
    fi
    if [[ "$answer" =~ ^[a-zA-Z0-9._/-]+$ ]]; then
      printf '%s' "$answer"
      return
    fi
    printf 'Use only letters, digits, dot, underscore, slash or hyphen.\n' >&2
  done
}

INTERACTIVE=0
if [[ $# -eq 0 || "${1:-}" == "--interactive" ]]; then
  INTERACTIVE=1
  [[ "${1:-}" == "--interactive" ]] && shift
fi

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

CONNECT_TO=""
RUN_SYNC=1

if (( INTERACTIVE )); then
  [[ -t 0 ]] || die "Interactive mode requires a TTY."
  printf '\n=== New Module Wizard ===\n'
  LAYER="$(prompt_layer)"
  NAME="$(prompt_module_name)"

  if prompt_yes_no "Connect this module to another existing module?" "n"; then
    read -rp "Target module (example: :data:chats): " CONNECT_TO
  fi
else
  [[ $# -ge 2 ]] || {
    usage
    exit 1
  }

  LAYER="$1"
  NAME="$2"
  shift 2

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --connect-to)
        [[ $# -ge 2 ]] || die "--connect-to requires a value"
        CONNECT_TO="$2"
        shift 2
        ;;
      --no-sync)
        RUN_SYNC=0
        shift
        ;;
      *)
        die "Unknown argument: $1"
        ;;
    esac
  done
fi

case "$LAYER" in
  core) KMP_PLUGIN_ID="incomedy.kmp.library" ;;
  data) KMP_PLUGIN_ID="incomedy.data" ;;
  feature) KMP_PLUGIN_ID="incomedy.feature" ;;
  *) die "Invalid layer '$LAYER'. Use: core | data | feature" ;;
esac

[[ "$NAME" =~ ^[a-zA-Z0-9._/-]+$ ]] || die "Invalid name '$NAME'"

NEW_MODULE=":$LAYER:${NAME//\//:}"
NEW_DIR="$(module_to_dir "$NEW_MODULE")"
NEW_BUILD_FILE="$NEW_DIR/build.gradle.kts"
SETTINGS_FILE="settings.gradle.kts"

require_file "$SETTINGS_FILE"

PKG_SUFFIX="$(to_package_suffix "$NAME")"
PACKAGE="com.bam.incomedy.$LAYER.$PKG_SUFFIX"
CLASS_BASENAME="$(to_pascal_case "$NAME")"
[[ -n "$CLASS_BASENAME" ]] || CLASS_BASENAME="Module"
CLASS_NAME="${CLASS_BASENAME}Module"
CLASS_FILE="$NEW_DIR/src/commonMain/kotlin/${PACKAGE//./\/}/$CLASS_NAME.kt"

BUILD_CONTENT=$(cat <<EOF_BUILD
plugins {
    id("$KMP_PLUGIN_ID")
}

kotlin {
    androidLibrary {
        namespace = "$PACKAGE"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
EOF_BUILD
)

CLASS_CONTENT=$(cat <<EOF_CLASS
package $PACKAGE

object $CLASS_NAME
EOF_CLASS
)

if [[ -e "$NEW_BUILD_FILE" ]]; then
  die "Module already exists: $NEW_MODULE ($NEW_BUILD_FILE)"
fi

log "Creating module $NEW_MODULE"
write_file "$NEW_BUILD_FILE" "$BUILD_CONTENT"
write_file "$CLASS_FILE" "$CLASS_CONTENT"

append_line_if_missing "$SETTINGS_FILE" "include(\"$NEW_MODULE\")"

if [[ -n "$CONNECT_TO" ]]; then
  CONNECT_TO="$(normalize_module_colon "$CONNECT_TO")"
  DEP_ACCESSOR="$(module_to_accessor "$NEW_MODULE")"
  log "Connecting $CONNECT_TO -> $NEW_MODULE"
  insert_dependency_into_module "$CONNECT_TO" "$DEP_ACCESSOR"
fi

if (( RUN_SYNC )); then
  log "Running Gradle sync..."
  ./gradlew help --no-daemon
fi

log "Done."

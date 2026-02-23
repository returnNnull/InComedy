#!/usr/bin/env bash
set -u -o pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"
"$PROJECT_ROOT/scripts/module-manager.py"
STATUS=$?
echo
[[ $STATUS -eq 0 ]] && echo "Completed successfully." || echo "Failed with exit code: $STATUS"
read -rp "Press Enter to close..." _
exit "$STATUS"

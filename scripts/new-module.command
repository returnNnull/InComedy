#!/usr/bin/env bash
set -u -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"
"$PROJECT_ROOT/scripts/new-module.sh" --interactive
STATUS=$?

echo
if [[ $STATUS -eq 0 ]]; then
  echo "Completed successfully."
else
  echo "Failed with exit code: $STATUS"
fi
read -rp "Press Enter to close..." _
exit "$STATUS"

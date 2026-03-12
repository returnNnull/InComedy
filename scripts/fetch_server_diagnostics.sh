#!/usr/bin/env bash

set -euo pipefail

# Fetches sanitized diagnostics events from a running InComedy server.
# The script is intentionally operator-only and requires a diagnostics token.

usage() {
  cat <<'EOF'
Usage:
  scripts/fetch_server_diagnostics.sh [options]

Options:
  --base-url URL         Server base URL. Defaults to $INCOMEDY_DIAGNOSTICS_BASE_URL.
  --token TOKEN          Diagnostics token. Defaults to $INCOMEDY_DIAGNOSTICS_TOKEN.
  --request-id UUID      Filter by X-Request-ID correlation id.
  --route-prefix PATH    Filter by route prefix, for example /api/v1/auth.
  --stage VALUE          Filter by diagnostics stage.
  --status CODE          Filter by HTTP status code.
  --from ISO8601         Inclusive lower time bound, for example 2026-03-13T00:00:00Z.
  --to ISO8601           Inclusive upper time bound.
  --limit NUMBER         Max number of events to fetch. Default: 50.
  --help                 Show this help.

Environment:
  INCOMEDY_DIAGNOSTICS_BASE_URL
  INCOMEDY_DIAGNOSTICS_TOKEN
EOF
}

base_url="${INCOMEDY_DIAGNOSTICS_BASE_URL:-}"
token="${INCOMEDY_DIAGNOSTICS_TOKEN:-}"
request_id=""
route_prefix=""
stage=""
status=""
from=""
to=""
limit="50"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      base_url="${2:-}"
      shift 2
      ;;
    --token)
      token="${2:-}"
      shift 2
      ;;
    --request-id)
      request_id="${2:-}"
      shift 2
      ;;
    --route-prefix)
      route_prefix="${2:-}"
      shift 2
      ;;
    --stage)
      stage="${2:-}"
      shift 2
      ;;
    --status)
      status="${2:-}"
      shift 2
      ;;
    --from)
      from="${2:-}"
      shift 2
      ;;
    --to)
      to="${2:-}"
      shift 2
      ;;
    --limit)
      limit="${2:-}"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$base_url" ]]; then
  echo "Missing base URL. Pass --base-url or set INCOMEDY_DIAGNOSTICS_BASE_URL." >&2
  exit 1
fi

if [[ -z "$token" ]]; then
  echo "Missing diagnostics token. Pass --token or set INCOMEDY_DIAGNOSTICS_TOKEN." >&2
  exit 1
fi

endpoint="${base_url%/}/api/v1/diagnostics/events"
curl_args=(
  --fail-with-body
  --silent
  --show-error
  --get
  --header "X-Diagnostics-Token: ${token}"
  --data-urlencode "limit=${limit}"
)

if [[ -n "$request_id" ]]; then
  curl_args+=(--data-urlencode "request_id=${request_id}")
fi

if [[ -n "$route_prefix" ]]; then
  curl_args+=(--data-urlencode "route_prefix=${route_prefix}")
fi

if [[ -n "$stage" ]]; then
  curl_args+=(--data-urlencode "stage=${stage}")
fi

if [[ -n "$status" ]]; then
  curl_args+=(--data-urlencode "status=${status}")
fi

if [[ -n "$from" ]]; then
  curl_args+=(--data-urlencode "from=${from}")
fi

if [[ -n "$to" ]]; then
  curl_args+=(--data-urlencode "to=${to}")
fi

curl "${curl_args[@]}" "$endpoint"

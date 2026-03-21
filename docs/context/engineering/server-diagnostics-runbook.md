# Server Diagnostics Runbook

Use this runbook when a task requires live/staging backend diagnostics or production-triage context.

## Preferred Path

- Prefer the bounded sanitized diagnostics system over raw container logs.
- Correlate backend events through `requestId` and stable low-cardinality metadata such as `stage`, `provider`, and safe outcome fields.
- Never record tokens, secrets, or raw sensitive payloads in docs, responses, or governance logs.

## Diagnostics Helper

- Helper script: `/Users/abetirov/AndroidStudioProjects/InComedy/scripts/fetch_server_diagnostics.sh`
- Base URL: `https://incomedy.ru`
- Access token source: `/Users/abetirov/AndroidStudioProjects/InComedy/deploy/server/.env` (`DIAGNOSTICS_ACCESS_TOKEN`)
- The token may be read locally for execution, but must never be printed into docs or chat output.

Example:

```bash
INCOMEDY_DIAGNOSTICS_BASE_URL=https://incomedy.ru \
INCOMEDY_DIAGNOSTICS_TOKEN=<token from deploy/server/.env> \
/Users/abetirov/AndroidStudioProjects/InComedy/scripts/fetch_server_diagnostics.sh \
  --route-prefix /api/v1/auth/telegram \
  --from 2026-03-13T00:00:00Z
```

## Correlation Rule

- Match backend diagnostics with backend error messages, surfaced mobile request ids, and Android/iOS client logs.
- When adding backend diagnostics, keep metadata bounded, sanitized, and stable enough for later correlation.

## Raw Host Fallback

Use raw container logs only when the sanitized diagnostics path is insufficient.

```bash
ssh -i ~/.ssh/incomedy_gha root@83.222.24.63
cd /opt/incomedy/server
docker compose ps
docker logs --tail 200 incomedy-server
```

## Deployment Safety Note

- Before restart/recreate on the host, inspect `/opt/incomedy/server/.env`.
- Do not assume `IMAGE=ghcr.io/returnnnull/incomedy-server:latest` is current on the host; prefer an explicitly pinned SHA image.

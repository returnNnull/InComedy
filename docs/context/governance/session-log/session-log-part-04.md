# Session Log Part 04

## 2026-03-13 01:20

- Context: Reported inability to diagnose `Telegram auth failed` from device/server logs and requested a general mechanism that lets future chats securely fetch server-side diagnostics rather than relying on raw auth logs only.
- Changes: Formalized the observability task, accepted ADR `D-052`, added an operator-only bounded diagnostics store and retrieval endpoint on the backend, wired sanitized diagnostics capture into the current auth/session/identity/workspace routes, surfaced backend `requestId` values in shared mobile backend failure messages for device/server correlation, added the repository helper `scripts/fetch_server_diagnostics.sh`, updated API/docs/env examples, and covered the slice with server diagnostics tests plus KMP/mobile compilation verification.
- Decisions: Accepted `D-052`: live backend diagnostics must be retrievable through a sanitized operator-only mechanism keyed by request correlation ids instead of depending on raw server-log access.
- Next: Configure `DIAGNOSTICS_ACCESS_TOKEN` on the target server/staging environment, deploy the updated backend, reproduce the Telegram auth failure, fetch diagnostics by returned `requestId`, and then fix the concrete auth root cause based on the captured safe failure code/stage.

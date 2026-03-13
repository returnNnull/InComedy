# Session Log Part 04

## 2026-03-13 01:20

- Context: Reported inability to diagnose `Telegram auth failed` from device/server logs and requested a general mechanism that lets future chats securely fetch server-side diagnostics rather than relying on raw auth logs only.
- Changes: Formalized the observability task, accepted ADR `D-052`, added an operator-only bounded diagnostics store and retrieval endpoint on the backend, wired sanitized diagnostics capture into the current auth/session/identity/workspace routes, surfaced backend `requestId` values in shared mobile backend failure messages for device/server correlation, added the repository helper `scripts/fetch_server_diagnostics.sh`, updated API/docs/env examples, and covered the slice with server diagnostics tests plus KMP/mobile compilation verification.
- Decisions: Accepted `D-052`: live backend diagnostics must be retrievable through a sanitized operator-only mechanism keyed by request correlation ids instead of depending on raw server-log access.
- Next: Configure `DIAGNOSTICS_ACCESS_TOKEN` on the target server/staging environment, deploy the updated backend, reproduce the Telegram auth failure, fetch diagnostics by returned `requestId`, and then fix the concrete auth root cause based on the captured safe failure code/stage.

## 2026-03-13 12:15

- Context: Product confirmed that the newly introduced Telegram OIDC authorization-code flow is not an acceptable active path for the RU launch slice and requested an analysis, documentation fix, and commit-based rollback to the previous Telegram auth flow.
- Changes: Analyzed the commit chain (`421979b -> faa08c0 -> e2eab1e -> 39e874c`) and confirmed that the regression came from switching the active Telegram contract from legacy payload verify to OIDC without target-market validation; restored code/contracts/docs to the legacy Telegram verify flow, removed OIDC-specific routes/config/docs, formalized the rollback request, added risk `R-011`, and encoded a quality rule requiring target-market validation plus rollback planning before replacing active third-party auth/payment flows.
- Decisions: Accepted `D-053`: keep legacy Telegram payload verify as the active auth path and defer the Telegram OIDC authorization-code rollout until RU-market validation exists.
- Next: Deploy the rollback build to staging/server, verify Telegram login on real RU devices/network conditions, and only revisit alternative Telegram flows behind explicit market validation evidence and a pre-defined rollback plan.

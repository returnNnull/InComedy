# Task Request Template Part 10

## Latest Formalized Request (Replace Direct Telegram OIDC Launch With First-Party Launch Bridge)

## Context

- Related docs/decisions:
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `D-053`, `D-054`
- Current constraints:
  - Live backend recovery is complete, but the real Android Chrome path still shows `origin required` after the app opens the documented Telegram OIDC URL directly.
  - Telegram's official `telegram-login.js` library is explicitly built around a first-party web origin context, while the current app launch path bypasses such a page and opens `oauth.telegram.org/auth` raw.
  - The existing HTTPS callback bridge and backend code exchange should be preserved if a smaller launch-surface correction can unblock the flow.

## Goal

- What should be delivered:
  - Replace the direct raw Telegram OIDC launch from the mobile app with a first-party launch bridge on `https://incomedy.ru`, while keeping the existing callback bridge and backend verification/session contract intact.

## Scope

- In scope:
  - change `/api/v1/auth/telegram/start` to return an InComedy launch URL instead of a raw Telegram URL
  - add a first-party `/auth/telegram/launch` page that resumes the official Telegram OIDC browser flow
  - add safe launch telemetry analogous to the existing callback bridge telemetry
  - preserve callback bridge, backend code exchange, and app callback parsing contracts
  - update docs/tests/traceability to reflect the new active Telegram entry path
- Out of scope:
  - replacing the current callback bridge
  - exposing tokens or raw Telegram payloads through browser query strings
  - redesigning the rest of auth/session architecture

## Constraints

- Tech/business constraints:
  - Raw signed `state`, authorization codes, and secrets must stay out of diagnostics and docs.
  - The resulting mobile client contract should stay backend-driven so Android/iOS do not need provider-specific launch assembly.
  - The new launch surface is only complete when it has both tests and live-device validation.

## Definition of Done

- Functional result:
  - Telegram auth start returns an InComedy first-party launch URL
  - the launch page initiates Telegram browser auth from the approved origin and leaves safe diagnostics
  - callback bridge and backend verify/session contracts remain intact
  - decision/status docs reflect that the new launch surface is the active hypothesis for resolving `origin required`
- Required tests:
  - `./gradlew :server:test :data:auth:allTests :feature:auth:allTests :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

## Formalized Review Request (Telegram OIDC Conformance Check)

## Context

- Related docs/decisions:
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-054`, `D-055`
- Current constraints:
  - The active Telegram login path is backend-driven and starts from the first-party launch bridge on `https://incomedy.ru/auth/telegram/launch`.
  - The user provided the official manual Telegram OIDC requirements for `/auth`, `/token`, JWKS, PKCE, and `id_token` validation and asked for a client/server conformance review.

## Goal

- What should be delivered:
  - Audit the current mobile client and backend Telegram auth implementation against the provided official OIDC flow description.
  - Identify exact matches, intentional deviations, and any gaps that could explain authorization failures.

## Scope

- In scope:
  - compare the authorization URL construction against the documented parameter set
  - verify the server-side `/token` exchange contract, Basic auth usage, PKCE, and callback handling
  - verify `id_token` signature/JWKS/claims validation
  - verify the mobile client launch/callback/verify path against the server contract
- Out of scope:
  - changing the implementation
  - changing accepted decisions unless a contradiction is discovered
  - unrelated auth providers

## Constraints

- Tech/business constraints:
  - Review output should call out intentional divergence from the raw direct-launch doc separately from accidental mismatches.
  - Findings should reference concrete code paths on both client and server.

## Definition of Done

- Functional result:
  - a clear verdict on whether the current implementation matches the documented Telegram OIDC flow
  - a severity-ordered list of mismatches or risks, if any
  - explicit note of which parts already conform as implemented
- Required tests:
  - inspection only; rely on existing code and test coverage unless a local rerun becomes necessary
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`

## Formalized Implementation Request (Align Telegram Scope With Official OIDC Doc)

## Context

- Related docs/decisions:
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-054`, `D-055`
- Current constraints:
  - Review found that the current Telegram authorization URL builder requests `scope=openid profile`, while the official manual OIDC documentation provided by the user requires `scope=openid profile phone`.
  - The active mobile launch path must stay backend-driven through the first-party launch bridge.

## Goal

- What should be delivered:
  - Align the current Telegram OIDC implementation with the documented scope requirements without changing the accepted launch-bridge architecture.

## Scope

- In scope:
  - update the server-side authorization URL builder to request `openid profile phone`
  - add/adjust automated tests so this scope mismatch is caught in the future
  - sync docs/context and server-facing docs with the corrected scope
- Out of scope:
  - changing callback bridge behavior
  - changing token exchange logic
  - changing accepted governance decisions

## Constraints

- Tech/business constraints:
  - keep the first-party `https://incomedy.ru/auth/telegram/launch` flow intact
  - keep PKCE, signed `state`, backend `/token` exchange, and `id_token` verification unchanged apart from the documented scope alignment

## Definition of Done

- Functional result:
  - generated Telegram authorization URLs request `openid profile phone`
  - tests fail if the documented scope regresses
  - docs/context reflect the corrected official scope usage
- Required tests:
  - `./gradlew :server:test`
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`

## Formalized Review Request (Server Token Handling Audit For Telegram OIDC)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-052`, `D-054`, `D-055`
- Current constraints:
  - The user asked to review the backend logic after Telegram `/token` exchange: what the server does with returned tokens, what is stored, how refresh/session rotation works, and whether sensitive token material leaks into persistence or logs.
  - This is an inspection/review task, not a new implementation request.

## Goal

- What should be delivered:
  - Trace the server flow from Telegram code exchange through internal session issuance and storage.
  - Confirm which token artifacts are persisted, hashed, discarded, or exposed back to the client.

## Scope

- In scope:
  - review Telegram `/token` exchange handling and `id_token` validation
  - review replay-protection persistence and user identity upsert
  - review internal access/refresh token issuance, hashing, refresh rotation, logout revocation, and protected-route validation
  - review whether raw Telegram tokens enter logs or diagnostics
- Out of scope:
  - changing the implementation
  - non-Telegram auth providers
  - frontend secure storage

## Constraints

- Tech/business constraints:
  - Findings should distinguish Telegram-issued tokens from internal InComedy session tokens.
  - Security-sensitive conclusions should be grounded in concrete storage and logging paths.

## Definition of Done

- Functional result:
  - a clear explanation of what happens to Telegram token response data on the server
  - a clear explanation of how internal access/refresh tokens are stored and revoked
  - severity-ordered findings only if real risks or bugs are found
- Required tests:
  - inspection only; rely on existing code and tests
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`

## Formalized Validation Request (Manual Telegram Button Smoke With Server Diagnostics)

## Context

- Related docs/decisions:
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-052`, `D-053`, `D-054`, `D-055`
- Current constraints:
  - The user requested a real manual check of the Telegram login button behavior, not another static review.
  - The validation must use both Android-side evidence and operator-only server diagnostics.

## Goal

- What should be delivered:
  - Reproduce a real Telegram button press on the Android emulator/device and determine the exact step where the flow stops.
  - Correlate the client-side `AUTH_FLOW` stages with server diagnostics for `/api/v1/auth/telegram/*`, `/auth/telegram/launch*`, and `/auth/telegram/callback*`.

## Scope

- In scope:
  - verify live `GET /health` and `GET /api/v1/auth/telegram/start`
  - manually trigger the Telegram button on the Android app
  - capture relevant `adb logcat` flow events
  - fetch sanitized server diagnostics around the reproduced attempt
  - summarize the observed post-click behavior
- Out of scope:
  - changing code
  - broad regression testing outside Telegram auth
  - iOS reproduction

## Constraints

- Tech/business constraints:
  - Do not expose diagnostics token or raw secrets in the report.
  - Prefer exact timestamps, request ids, and diagnostics stages over subjective descriptions.

## Definition of Done

- Functional result:
  - a confirmed end-to-end behavior trace for one real Telegram button attempt
  - explicit statement whether the flow reaches launch bridge, callback bridge, and backend verify
  - server diagnostics and client logs are cross-referenced
- Required tests:
  - manual emulator/device smoke only
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

## Formalized Validation Request (Telegram Allowed URL Registration Check)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-054`, `D-055`
- Current constraints:
  - Live Android validation already proved that the app reaches `/api/v1/auth/telegram/start`, opens the first-party `/auth/telegram/launch` page, and then stalls before `/auth/telegram/callback`.
  - The user now wants the remaining blocker checked specifically against Telegram's provider-side origin/redirect requirements, not another code change.

## Goal

- What should be delivered:
  - Reproduce the live Telegram authorization request outside the Android UI and determine whether `origin required` still happens independent of the app.
  - Compare the active InComedy origin/redirect contract against the current official Telegram Allowed URL requirements.

## Scope

- In scope:
  - fetch a fresh live `/api/v1/auth/telegram/start` payload and `/auth/telegram/launch` page
  - extract the exact live `https://oauth.telegram.org/auth?...` URL used by the bridge
  - reproduce the Telegram endpoint response with and without common `Origin` / `Referer` headers
  - correlate the fresh `/start` and `/launch` requests with operator diagnostics
  - summarize whether the remaining blocker is most consistent with provider-side registration drift
- Out of scope:
  - changing backend or mobile code
  - editing Telegram/BotFather configuration directly from the repo
  - broad non-Telegram auth regression testing

## Constraints

- Tech/business constraints:
  - Do not expose diagnostics token or other secrets in the report.
  - Conclusions about Telegram requirements should be grounded in current official Telegram docs rather than third-party posts.

## Definition of Done

- Functional result:
  - a reproducible statement of how the exact live Telegram auth URL responds outside Android
  - a clear mapping between InComedy's current origin/redirect values and Telegram's Allowed URL rules
  - an explicit conclusion about whether app/server code or provider-side registration is the leading blocker
- Required tests:
  - live HTTP reproduction plus diagnostics correlation only
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/engineering/architecture-overview.md`

## Formalized Operator Request (Export Telegram Server Diagnostics To Readable Files)

## Context

- Related docs/decisions:
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-052`, `D-054`, `D-055`
- Current constraints:
  - The user wants the current sanitized Telegram auth diagnostics exported from the live server into a local file they can read directly.
  - The export must use the existing operator-only diagnostics mechanism and must not reveal the diagnostics access token.

## Goal

- What should be delivered:
  - Save the current Telegram auth diagnostics from the live server into a repository file.
  - Provide a second, human-readable summary file that makes the recent routes/stages/request ids easy to inspect.

## Scope

- In scope:
  - fetch current diagnostics for Telegram auth-related routes from the live server
  - save raw sanitized JSON output into a local artifact file
  - generate a concise readable markdown summary next to the raw JSON
- Out of scope:
  - changing server code
  - changing diagnostics retention behavior
  - modifying production configuration

## Constraints

- Tech/business constraints:
  - Do not print or persist the diagnostics token in the generated files.
  - Keep the exported data limited to Telegram auth routes relevant to the current incident.

## Definition of Done

- Functional result:
  - a local file contains the fetched Telegram diagnostics payload
  - a local markdown file summarizes the payload in readable form
  - the user can open the files directly from the repo
- Required tests:
  - live diagnostics fetch only
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`

## Formalized Investigation Request (Validate Real Telegram OIDC Runtime Credentials)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/session-log.md`
  - `D-054`, `D-055`
- Current constraints:
  - The user provided the live Telegram bot token, login client id, and login client secret currently configured on the server and wants to understand why authorization still fails.
  - The current live browser flow already reproduces `origin required` before `/auth/telegram/callback`.

## Goal

- What should be delivered:
  - Verify whether the live Telegram runtime credentials are internally consistent and accepted by Telegram endpoints.
  - Distinguish a bad-credentials failure from a provider-side login-domain / allowed-URL registration failure.

## Scope

- In scope:
  - verify the provided bot token against Telegram Bot API
  - verify the provided client id / client secret behavior against Telegram OIDC endpoints
  - compare credential checks with the observed live browser `origin required` failure
  - summarize the most likely root cause
- Out of scope:
  - changing server code
  - editing BotFather or Telegram Login configuration directly
  - persisting secrets in docs/context

## Constraints

- Tech/business constraints:
  - Do not echo sensitive secrets back into saved docs or the final report.
  - Any claim about Telegram endpoint behavior should be backed by direct verification or official docs.

## Definition of Done

- Functional result:
  - the bot token/client credentials are either verified or ruled out as the blocker
  - the remaining blocker is narrowed to the specific Telegram-side requirement category
- Required tests:
  - live endpoint verification only
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`

## Formalized Operator Request (Remote Server Recreate After Telegram Runtime Checks)

## Context

- Related docs/decisions:
  - `server/README.md`
  - `docs/context/governance/session-log.md`
  - `D-027`, `D-028`, `D-052`
- Current constraints:
  - The user requested a real server restart after inspecting the live Telegram runtime parameters.
  - The deployed backend is managed on the staging host through Docker Compose in `/opt/incomedy/server`.

## Goal

- What should be delivered:
  - Recreate the remote backend container so updated runtime env values can take effect.
  - Confirm that the public health and Telegram auth-start endpoints are healthy after the restart.

## Scope

- In scope:
  - connect to the deployment host via the locally available SSH key
  - inspect current remote compose state and runtime image selection
  - recreate the remote `server` service
  - verify `https://incomedy.ru/health` and `https://incomedy.ru/api/v1/auth/telegram/start`
- Out of scope:
  - code changes
  - CI/CD workflow changes
  - broad end-to-end Telegram browser validation after restart

## Constraints

- Tech/business constraints:
  - If the restart exposes runtime drift, restore the previously healthy server image before leaving the host in a degraded state.
  - Record the exact operational outcome in session log/context.

## Definition of Done

- Functional result:
  - the remote backend container is recreated successfully
  - the live service remains healthy after the recreate
  - any runtime image/env drift uncovered during the restart is documented
- Required tests:
  - live `GET /health`
  - live `GET /api/v1/auth/telegram/start`
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

## Formalized Documentation Request (Add Server Log Retrieval To Context Bootstrap Guidance)

## Context

- Related docs/decisions:
  - `docs/context/handoff/context-protocol.md`
  - `server/README.md`
  - `D-019`, `D-052`
- Current constraints:
  - The user wants future chats to know immediately how to pull server-side diagnostics/logs without rediscovering the workflow.
  - The instructions must cover both the preferred sanitized diagnostics path and the fallback raw host/container log path.

## Goal

- What should be delivered:
  - Extend the context bootstrap guidance with concrete instructions for fetching server diagnostics/logs.
  - Keep the instructions safe: no secret values copied into docs, but exact file paths and commands should be included.

## Scope

- In scope:
  - document sanitized diagnostics retrieval via `scripts/fetch_server_diagnostics.sh`
  - document where the diagnostics token lives locally
  - document the currently working SSH path for raw container logs on the host
  - mention the current remote image-pin caveat relevant to manual server recreate
- Out of scope:
  - changing backend diagnostics behavior
  - rotating secrets
  - changing deployment topology

## Constraints

- Tech/business constraints:
  - Never persist the diagnostics token itself in docs.
  - Keep the bootstrap guidance concise enough to stay usable as a copy-paste starter.

## Definition of Done

- Functional result:
  - future chats can fetch server diagnostics/logs directly from the bootstrap guidance
  - the guidance points to both sanitized and raw log paths
- Required tests:
  - docs-only change
- Required docs updates:
  - `docs/context/handoff/context-protocol.md`
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

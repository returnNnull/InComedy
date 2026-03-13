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

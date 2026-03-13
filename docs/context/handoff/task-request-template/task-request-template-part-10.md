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

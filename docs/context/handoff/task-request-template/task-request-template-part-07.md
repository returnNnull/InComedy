# Task Request Template Part 07

## Latest Formalized Request (Telegram Official OIDC Auth Alignment)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/governance/decisions-log/decisions-log-part-03.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
- Current constraints:
  - Telegram auth is currently broken in the live mobile flow.
  - The current implementation opens `https://oauth.telegram.org/auth`, but the mobile callback and backend verification layers still expect the legacy Telegram Login Widget payload (`id`, `auth_date`, `hash`) instead of the current official authorization-code contract.
  - The app must still return from successful Telegram authorization back into the mobile app through the existing HTTPS callback bridge and registered `incomedy://auth/telegram` deep link.
  - The user additionally asked to prefer Telegram-app-first login if officially possible, otherwise fall back to browser.

## Goal

- What should be delivered:
  - Align Telegram login with the current official Telegram authorization flow so successful authorization returns into the app reliably and backend session issuance works again.

## Scope

- In scope:
  - replace the broken legacy Telegram mobile callback/verify assumptions with the official Telegram authorization-code flow
  - preserve the HTTPS callback bridge contract and deep-link handoff back into `incomedy://auth/telegram`
  - add secure server-side code exchange and `id_token` verification against official Telegram OIDC metadata/JWKS
  - keep Android and iOS callback handling compatible with the shared auth architecture
  - update tests, API/docs/env examples, and governance context to reflect the official Telegram auth contract
- Out of scope:
  - redesigning the overall auth UI
  - replacing the provider-agnostic internal user/session model
  - unsupported package-specific hacks that try to force Telegram-app-only login without official documentation support

## Constraints

- Tech/business constraints:
  - Follow current official Telegram auth documentation rather than archived widget-only behavior.
  - Do not log raw authorization codes, `id_token` values, Telegram callback payloads, or secrets.
  - Keep the post-success handoff back into the app explicit and reliable on Android and iOS.
  - If Telegram-app-first login is not documented/supported by Telegram for this flow, keep browser-based launch as the supported path and document that constraint.
- Deadlines or milestones:
  - This work is the immediate continuation of the blocked Telegram auth debugging track.

## Definition of Done

- Functional result:
  - Telegram login starts with the official Telegram auth URL
  - successful Telegram auth reaches the HTTPS callback bridge and returns back into the app
  - the app can exchange the callback for a backend session again
  - the implementation no longer depends on the legacy `hash` verify contract for the active Telegram mobile flow
- Required tests:
  - Telegram launch URL/config coverage in `data/auth`
  - auth callback parsing coverage for the Telegram code callback
  - server tests for Telegram auth start/verify flow and callback bridge detection
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`

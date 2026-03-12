# Task Request Template Part 06

## Latest Formalized Request (Telegram Auth Browser Handoff Regression Fix)

## Context

- Related docs/decisions:
  - `docs/context/governance/decisions-log/decisions-log-part-02.md` (`D-031`)
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `D-031`, `D-049`, `D-051`, `D-052`
- Current constraints:
  - The current live diagnostics show that some Telegram login attempts never reach backend verify at all, which means the flow breaks in the browser-to-app handoff before `/api/v1/auth/telegram/verify`.
  - Current implementation routes Telegram return through `https://incomedy.ru/auth/telegram/callback`, even though accepted decision `D-031` records direct `return_to=incomedy://auth/telegram` as the intended mobile handoff contract.
  - Users currently observe that pressing “Login via Telegram” opens a browser and then stalls instead of returning control to the app.

## Goal

- What should be delivered:
  - Restore a reliable Telegram mobile auth handoff so the browser flow returns directly into the app and reaches backend verification again.

## Scope

- In scope:
  - align the Telegram mobile launch configuration with the accepted direct deep-link return contract
  - preserve current Telegram payload parsing from query/fragment and backend verification behavior
  - add automated tests around Telegram auth launch/callback handoff behavior
  - synchronize context docs and session memory with the regression fix
- Out of scope:
  - redesigning the overall auth UI
  - replacing Telegram auth provider or backend contract
  - broad changes to VK/Google auth flows

## Constraints

- Tech/business constraints:
  - Keep the current provider-agnostic auth/session architecture intact.
  - Do not weaken Telegram auth validation or diagnostics.
  - The fix must keep iOS and Android custom-scheme callback handling compatible with existing app registration.
- Deadlines or milestones:
  - This fix is the immediate next step after diagnostics showed that the flow often dies before backend verification.

## Definition of Done

- Functional result:
  - Telegram login launches browser flow and returns back into the app through the registered deep link instead of stalling in the browser-only state
  - backend verify requests appear again for fresh login attempts
- Required tests:
  - auth callback parsing test coverage
  - Telegram launch URL/config coverage in `data/auth`
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Safe Android Auth Handoff Logging)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/governance/decisions-log/decisions-log-part-02.md` (`D-032`)
  - `D-031`, `D-032`, `D-052`
- Current constraints:
  - After the Telegram handoff fix, the next debugging gap is visibility into whether Android actually receives and parses the returning deep link.
  - Raw callback URLs must not be logged because Telegram callback payload contains sensitive fields (`tgAuthResult`, `hash`, user identifiers).
  - Existing auth logs show high-level flow stages, but not enough Android-specific handoff detail to distinguish `browser never returned`, `app received malformed deep link`, and `parser ignored callback`.

## Goal

- What should be delivered:
  - Add safe Android-side logging around auth callback handoff so device logs show whether the app received a deep link, what provider/path it matched, and where parsing/forwarding stopped, without logging the raw callback payload.

## Scope

- In scope:
  - log callback summary in `MainActivity` for initial intent and `onNewIntent`
  - log callback receive/ignore/parse/forward stages in `AuthAndroidViewModel`
  - introduce a reusable callback-summary helper that exposes only safe booleans/shape metadata
  - add automated tests proving the summary helper does not leak callback payload values
- Out of scope:
  - backend logging changes
  - UI redesign
  - changes to Telegram auth cryptography or API contract

## Constraints

- Tech/business constraints:
  - Do not log raw callback URL, `tgAuthResult`, Telegram `hash`, token-like values, or raw user identifiers.
  - Keep the new logs actionable enough for manual device-log inspection.
- Deadlines or milestones:
  - This is the immediate follow-up after restoring direct Telegram deep-link return.

## Definition of Done

- Functional result:
  - Android logs clearly indicate whether auth callback handoff reached the app and how far parsing progressed
  - callback payload secrets remain absent from logs
- Required tests:
  - Android/JVM unit test for safe callback summary sanitization
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Telegram HTTPS Callback Bridge Stabilization)

## Context

- Related docs/decisions:
  - `docs/context/governance/decisions-log/decisions-log-part-02.md` (`D-031`)
  - `docs/context/governance/decisions-log/decisions-log-part-03.md` (`D-052`)
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `D-031`, `D-032`, `D-052`
- Current constraints:
  - Fresh Telegram auth attempts still fail before mobile callback handling: after successful Telegram authorization, the browser may bounce back to another authorization screen instead of reliably continuing in the app.
  - The current HTTPS callback bridge is the intended production contract, but browser-to-app launch behavior on Android is still not stable enough for real-device debugging.
  - Existing server diagnostics can explain backend verify failures, but they need bridge-level signal to show whether the Telegram browser flow at least reached `/auth/telegram/callback`.

## Goal

- What should be delivered:
  - Stabilize the Telegram HTTPS callback bridge so successful Telegram authorization returns users into the app more reliably, while exposing enough safe diagnostics to distinguish `Telegram reached bridge`, `bridge attempted app handoff`, and `app callback never arrived`.

## Scope

- In scope:
  - keep Telegram `return_to` on the approved HTTPS domain callback bridge
  - improve the bridge page handoff strategy for Android so it does not fall into a browser-only or apparent re-auth loop
  - record safe server-side diagnostics when the bridge route is hit
  - keep Android callback logging in place for post-bridge analysis
  - update ADR/task/session traceability so docs match the actual bridge-based contract
- Out of scope:
  - replacing Telegram auth provider
  - changing backend Telegram payload verification semantics
  - introducing a full external observability stack

## Constraints

- Tech/business constraints:
  - Do not expose raw Telegram callback payload values in server or device diagnostics.
  - Keep the callback handoff compatible with existing Android deep-link registration.
  - Preserve the current deployed-domain origin/return contract with Telegram OAuth.
- Deadlines or milestones:
  - This is the immediate next debugging slice after user reports of post-success browser reauthorization loops.

## Definition of Done

- Functional result:
  - Telegram auth returns through `https://incomedy.ru/auth/telegram/callback`
  - the callback bridge attempts app handoff in a browser-safe way and leaves a usable fallback path if auto-open fails
  - server diagnostics show bridge hits for new attempts
- Required tests:
  - server route test for Telegram callback bridge response and diagnostics capture
  - existing Android/data auth tests stay green
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`

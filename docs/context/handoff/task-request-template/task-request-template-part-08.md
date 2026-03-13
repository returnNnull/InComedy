# Task Request Template Part 08

## Latest Formalized Request (Telegram Legacy Rollback Investigation And Temporary Restore)

## Context

- Related docs/decisions:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/product/risk-log.md`
  - `docs/context/governance/decisions-log/decisions-log-part-03.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
- Current constraints:
  - The repository had switched the active Telegram auth path to the official OIDC authorization-code contract in commit `e2eab1e`.
  - Product temporarily requested a branch-local rollback to the previous legacy Telegram payload-verify flow for the current RU launch slice.
  - The rollback had to be performed by commits and could not remove unrelated auth/session, diagnostics, role, or workspace work.

## Goal

- What should be delivered:
  - Analyze how the incorrect Telegram auth transition happened, document the finding in source-of-truth docs, and temporarily restore the old Telegram auth flow in code/contracts/docs for device reproduction.

## Scope

- In scope:
  - analyze the relevant Telegram auth commit chain and identify the regression point
  - record rollout/validation guardrails in `docs/context/*`
  - temporarily revert active Telegram auth code and API contracts from the OIDC code-exchange contract back to the legacy payload verify flow
  - keep diagnostics, session, identity, role, and workspace foundations intact
- Out of scope:
  - introducing a third Telegram auth flow
  - removing the provider-agnostic internal user/session model
  - redesigning auth UI or changing non-Telegram providers

## Constraints

- Tech/business constraints:
  - The temporary rollback must preserve diagnostics and request-id correlation work.
  - Findings from this rollback must remain documented even if the repository later reactivates OIDC.
- Deadlines or milestones:
  - This rollback is an intermediate corrective action for debugging and is not assumed to be the final long-lived Telegram direction.

## Definition of Done

- Functional result:
  - the repository can temporarily run the legacy Telegram verify contract again for reproduction purposes
  - governance docs explain how the regression happened and what validation rule now prevents a repeat
- Required tests:
  - `./gradlew :data:auth:allTests :feature:auth:allTests :server:test :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/product/risk-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`

---

## Latest Formalized Request (Android Telegram Launch Path Diagnostics)

## Context

- Related docs/decisions:
  - `docs/context/governance/session-log.md`
  - `docs/context/engineering/quality-rules.md`
  - `D-052`
- Current constraints:
  - A real device/emulator reproduction shows that after tapping the Telegram button the app reaches browser launch, but the server receives no `/api/v1/auth/telegram/verify` request.
  - The current Android auth screen opens external URLs through Compose `LocalUriHandler`, which hides the exact Android `Intent` details needed for launch-path debugging.
  - Raw auth URLs and callback payload values must stay out of logs.

## Goal

- What should be delivered:
  - Replace the Android Telegram external-auth launch path with an explicit browser intent and add safe logging that shows whether the full query-parameter set survives up to `startActivity`.

## Scope

- In scope:
  - replace Android auth-screen external URL opening from `LocalUriHandler` to explicit `ACTION_VIEW` + `CATEGORY_BROWSABLE`
  - add safe URI summaries for the launch URL and final `intent.data`
  - keep the shared auth contract and backend Telegram verify flow unchanged during this diagnostic step
  - document the diagnostics step in governance session memory
- Out of scope:
  - changing iOS launch behavior in this step
  - redesigning the Telegram auth UI
  - introducing raw URL logging

## Constraints

- Tech/business constraints:
  - logs must stay sanitized and must not dump raw callback payload values or secrets
  - the Android fix is diagnostic-first and should minimize blast radius to shared auth code
  - backend diagnostics and request-id correlation must remain intact

## Definition of Done

- Functional result:
  - Android auth launches external Telegram/browser flow through an explicit intent
  - device logs show safe summaries of launch URL and `intent.data` before browser handoff
  - repository context records that this step was introduced to isolate the pre-backend Telegram regression
- Required tests:
  - `./gradlew :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Reactivate Official Telegram OIDC After Legacy Direct-Launch Failure)

## Context

- Related docs/decisions:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/governance/decisions-log/decisions-log-part-03.md`
  - `docs/context/governance/decision-traceability/decision-traceability-part-04.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `D-053`, `D-054`
- Current constraints:
  - Fresh device reproduction of the temporary legacy path proved that the full legacy query set survives inside Android up to `startActivity`, but the browser/provider path still ends on `origin required` and never reaches backend verify.
  - The user explicitly selected the official Telegram OIDC option as the active path.
  - `revert 23b3400` did not restore every OIDC file cleanly, so the repository needs manual reconciliation on top of the reapply commit.

## Goal

- What should be delivered:
  - Reactivate the official Telegram OIDC authorization-code flow as the active Telegram path, keep the HTTPS callback bridge, preserve sanitized Android/browser diagnostics, and resynchronize source-of-truth docs.

## Scope

- In scope:
  - restore backend-driven Telegram auth start (`/api/v1/auth/telegram/start`) and code verify (`/api/v1/auth/telegram/verify`)
  - restore the HTTPS callback bridge route, static bridge HTML, and bridge telemetry coverage
  - restore OIDC-specific client/backend tests and DI wiring
  - keep explicit Android external-auth intent logging for continued handoff diagnostics
  - update governance/docs so active Telegram auth again matches `D-053` and `D-054`
- Out of scope:
  - introducing a Telegram Login Widget page as the active path
  - redesigning auth UI
  - removing provider-agnostic user/session foundations

## Constraints

- Tech/business constraints:
  - Do not log raw authorization codes, `id_token` values, callback payload values, or secrets.
  - Active Telegram auth must remain backend-driven and use the HTTPS callback bridge plus the official OIDC code exchange contract.
  - Docs must be updated before or together with any implementation changes that alter the active Telegram auth path.

## Definition of Done

- Functional result:
  - Telegram login starts with the official backend-issued OIDC auth URL
  - successful Telegram auth can return through the HTTPS callback bridge back into the app
  - the repository no longer contains mixed legacy/OIDC runtime wiring after the reactivation
- Required tests:
  - `./gradlew :data:auth:allTests :feature:auth:allTests :server:test :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`

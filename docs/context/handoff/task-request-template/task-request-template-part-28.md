# Task Request Template Part 28

## Formalized Governance Correction Request (External Provider Approval Semantics)

## Why This Step

- The previous documentation correction still left YooKassa phrased as part of the active `P0` path, even though the user had not explicitly approved YooKassa as the chosen PSP.
- That wording makes a future chat likely to confuse “implemented optional adapter” with “user-confirmed provider decision,” which is exactly the governance drift the user wants to prevent.
- The repository needs a durable rule that assistant inference, local code presence, and sample config do not count as provider approval.

## Scope

- Add an explicit governance decision clarifying that assistant/chat inference is not user confirmation for external providers.
- Re-align `00-current-state.md` so active `P0` and next-step wording stay provider-agnostic until the user explicitly chooses a provider.
- Update tooling, architecture, handoff, checklist, README, and env docs so YooKassa is described only as an unapproved disabled-by-default candidate.
- Roll decisions/session/task memory into new part files because the previous latest parts already exceeded the context-size threshold.

## Explicitly Out Of Scope

- removing the existing YooKassa implementation from the repository
- selecting a different PSP automatically
- adding new payment-provider code or runtime behavior

## Constraints

- `docs/context/*` remains the source of truth and must be updated in the same change.
- The correction must distinguish clearly between `implemented` and `approved by the user`.
- Existing provider-specific code may remain in the repository, but the active documentation must not present it as selected without explicit user confirmation.
- Context-size split rules from `context-protocol.md` must be respected while updating governance memory.

## Acceptance Signals

- Active context docs no longer describe YooKassa as the chosen current PSP path.
- Governance docs explicitly state that earlier assistant inference, code presence, or env examples do not equal user approval.
- Latest decision/session/task memory and handoff instructions are synchronized around the corrected rule.

## Implementation Outcome

## Delivered

- Added `D-065` to record that assistant inference, existing code, and example config/docs do not count as user approval of an external provider.
- Rewrote `00-current-state.md` so the active `P0` ticketing focus is provider-agnostic again and the next bounded step requires explicit user confirmation before any provider-specific PSP path is treated as selected.
- Updated tooling, architecture, context protocol, bootstrap guidance, integrity checklist, README, and env examples so YooKassa is documented only as an unapproved disabled-by-default candidate.
- Split rolling governance/task memory forward into `decisions-log-part-05.md`, `session-log-part-17.md`, and `task-request-template-part-28.md`.

## Verification

- Manual consistency review across the updated governance/current-state/handoff files.
- Search review for `YooKassa`, `provider`, `confirmed`, and `user confirmation` wording in the active context and server docs.

## Remaining Follow-Up

- Wait for explicit user confirmation before treating any external PSP as selected in future implementation planning.
- Continue ticketing work only through provider-agnostic slices until such confirmation exists.

## Formalized Roadmap Clarification Request (Defer PSP Until Pre-Publication)

## Why This Step

- The user explicitly wants the external PSP choice postponed until the final tasks before publication, not treated as the current execution gate.
- Without recording that roadmap change, the active context would still over-prioritize provider confirmation before other ticketing work.
- The next delivery step therefore needs to be re-pointed to a provider-agnostic ticketing slice that can progress without PSP selection.

## Scope

- Record a decision that concrete PSP selection/activation is deferred until the final pre-publication stage.
- Update `00-current-state.md` and `product/backlog.md` so the active `P0` and next step reflect provider-agnostic continuation.
- Update session memory and decision traceability to show the new next task.

## Explicitly Out Of Scope

- selecting any external PSP now
- enabling any provider-specific runtime path
- removing existing disabled provider adapters from the repository

## Constraints

- The active context must stay consistent with the rule that external-provider approval requires explicit user confirmation.
- The next task should remain inside the audience ticketing `P0` path and avoid provider lock-in.

## Acceptance Signals

- Active docs explicitly defer PSP selection until the final pre-publication stage.
- The next bounded step is a provider-agnostic ticket issuance / QR / check-in foundation.

## Implementation Outcome

## Delivered

- Added `D-066` to defer concrete external PSP selection and activation until final pre-publication work.
- Updated `00-current-state.md` and `product/backlog.md` so the active `P0` path continues through provider-agnostic ticket issuance / QR / check-in foundations first.
- Updated session memory and decision traceability to reflect the new immediate task ordering.

## Verification

- Manual consistency review across `00-current-state.md`, `product/backlog.md`, decisions log, session log, and decision traceability.

## Remaining Follow-Up

- Implement the provider-agnostic ticket issuance / QR / check-in slice.
- Revisit PSP selection only in the final pre-publication stage after explicit user confirmation.

## Formalized Implementation Request (Provider-Agnostic Ticket Issuance / QR / Check-In Foundation)

## Why This Step

- `D-066` redirected the active ticketing work away from PSP selection and toward provider-agnostic order/ticket/check-in delivery first.
- The repository already had `TicketOrder` and payment-confirmation foundations, so the next meaningful increment was to turn successful paid orders into actual tickets with QR payloads and a checker-safe scan flow.
- This slice needed to stay provider-agnostic and avoid treating any existing PSP adapter as selected by default.

## Scope

- Add provider-agnostic issued-ticket persistence and domain/data contracts.
- Issue tickets idempotently after confirmed payment and expose them to the buyer through authenticated backend API.
- Add checker-facing QR scan/check-in backend flow with duplicate-scan handling and RBAC.
- Add migration, tests, and active-context documentation updates.

## Explicitly Out Of Scope

- selecting or activating a concrete external PSP
- mobile UI for `My Tickets`, QR presentation, or checker scanner UX
- complimentary tickets, refunds, sold-out automation, or offline scan buffering

## Constraints

- Provider-specific code may exist in the repository, but no provider choice is considered approved without explicit user confirmation.
- The new flow must keep structured diagnostics and Russian repository-code comments.
- Ticket issuance and check-in must stay idempotent and covered by automated tests.

## Acceptance Signals

- Confirmed paid orders produce issued tickets with stable QR payloads.
- Authenticated users can read their tickets through backend API.
- Checker/owner/manager can scan QR payloads, first scan marks `checked_in`, repeated scan returns `duplicate`.
- Migration and route coverage protect the new foundation.

## Implementation Outcome

## Delivered

- Added `V12__ticket_issuance_and_checkin_foundation.sql` with `tickets` persistence and check-in metadata.
- Introduced `IssuedTicket` / `TicketCheckInResult` models in `:domain:ticketing` and corresponding backend/data adapters in `:data:ticketing`.
- Extended server ticketing repository/service/routes with idempotent ticket issuance on paid confirmation, historical paid-order backfill on ticket reads, authenticated `GET /api/v1/me/tickets`, and checker/owner/manager `POST /api/v1/checkin/scan`.
- Added regression coverage for issued-ticket listing, checker-only check-in, duplicate scan semantics, and migration presence.
- Updated active context docs so the next step now shifts from backend foundation to shared/mobile audience + checker surfaces on top of these contracts.

## Verification

- `./gradlew :domain:ticketing:allTests :data:ticketing:compileKotlinMetadata :shared:compileKotlinMetadata :server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.ticketing.TicketingRoutesTest'`

## Remaining Follow-Up

- Implement shared/mobile `My Tickets`, buyer QR presentation, and checker scan UX on top of the new backend contracts.
- Add later ticketing follow-ups such as complimentary issuance, refund/cancel lifecycle, sold-out automation, and offline-tolerant checker tooling.

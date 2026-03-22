# Session Log Part 17

## 2026-03-21 23:08

- Context: User clarified that the previous provider-governance interpretation was still wrong: the chat must not automatically treat an external provider as chosen just because code/docs/config for it already exist, and YooKassa was cited as the concrete example.
- Changes: Rewrote the active context snapshot, tooling/architecture rules, handoff instructions, provider notes in README/env examples, and governance traceability so YooKassa is described only as an unapproved disabled-by-default candidate; added an explicit decision that assistant inference, existing code, and config examples are not user confirmation; and rolled decisions/session/task memory into new part files because the previous latest parts had already exceeded the context-size threshold.
- Decisions: Accept `D-065`. No external PSP is currently selected in governance, and provider-specific payment work must not be described as the active/current path until the user explicitly confirms a provider choice.
- Next: Keep external PSP selection blocked on explicit user confirmation, and continue only provider-agnostic ticketing work or provider-agnostic preparation until that confirmation exists.

## 2026-03-21 23:18

- Context: User clarified the roadmap further: no external PSP should be chosen now, and that decision should be postponed until the final tasks before publication.
- Changes: Updated the active `P0` focus, next bounded step, and backlog wording so PSP activation is explicitly deferred to the final pre-publication stage rather than treated as the immediate blocker; recorded the roadmap clarification as a new accepted decision; and redirected the next delivery slice toward provider-agnostic ticket issuance plus QR/check-in foundations.
- Decisions: Accept `D-066`. Concrete external PSP selection is intentionally postponed, and the current next task becomes provider-agnostic ticket issuance / QR / check-in work on top of the existing `TicketOrder` lifecycle.
- Next: Implement provider-agnostic ticket issuance and checker-facing QR/check-in foundations; keep PSP activation/confirmation for the final pre-publication milestone.

## 2026-03-21 23:46

- Context: User asked to resynchronize the interrupted task and continue from the active `D-066` follow-up: provider-agnostic ticket issuance plus checker-facing QR/check-in on top of the existing checkout-order lifecycle.
- Changes: Completed the backend/shared foundation for issued tickets and check-in by adding `IssuedTicket` / check-in domain-data contracts, the `tickets` persistence migration, idempotent ticket issuance on paid-order confirmation plus backfill for historical paid orders, authenticated `GET /api/v1/me/tickets`, checker/owner/manager `POST /api/v1/checkin/scan`, and the corresponding structured diagnostics. Added server regression coverage for issued-ticket listing, checker-only check-in, duplicate scan semantics, and the new migration surface.
- Decisions: Kept `D-065` / `D-066` intact: no concrete PSP was selected or activated; the work remained provider-agnostic and used the existing optional YooKassa adapter only as a disabled candidate in tests for payment-confirmation flow.
- Next: Build shared/mobile audience and staff surfaces on top of the new contracts: `My Tickets`, QR presentation for the buyer, and checker scan UX over the existing `me/tickets` and `checkin/scan` backend routes.

## 2026-03-22 00:27

- Context: User asked to proceed immediately after context synchronization, so the active follow-up to `D-066` became the shared/mobile ticketing slice: `My Tickets`, buyer QR presentation, and checker scan UX on top of the already delivered provider-agnostic backend contracts.
- Changes: Added shared `:feature:ticketing` state/effect/intent orchestration plus Koin/iOS bridge wiring, Android Compose `Билеты` tab UI with QR rendering and checker scan form, iOS SwiftUI `TicketWalletView` / `TicketWalletModel`, and targeted Android/iOS UI coverage. Fixed SwiftUI accessibility identifier collisions so QR-toggle, scan input/button, and result code remain individually addressable in XCUITest.
- Decisions: Kept `D-065` / `D-066` intact without new governance changes. No concrete PSP was selected or activated; the client delivery stayed provider-agnostic and continued to treat the existing YooKassa adapter as a disabled candidate only.
- Next: Shift the next bounded `P0` step to comedian applications plus organizer approve/reject/waitlist and lineup ordering, while PSP selection remains deferred until final pre-publication work.

## 2026-03-22 22:12

- Context: Automation started a new epic after context sync and no recoverable in-progress run was found; the next bounded `P0` slice remained comedian applications plus lineup ordering.
- Changes: Formalized `EPIC-067` in task memory, created branch `codex/epic-067-comedian-applications-foundation`, and completed `TASK-067` as a backend-only foundation step: added migration `V13__comedian_applications_foundation.sql`, repository/service/routes for comedian submit + organizer list/status change, OpenAPI entries, and targeted server regression coverage.
- Decisions: Kept `D-065` / `D-066` intact without new governance decisions. No PSP was selected; delivery continued on an additive non-payment MVP slice with localized blast radius.
- Next: Execute exactly one next subtask in the same epic: `TASK-068` for lineup-entry foundation (`approved -> lineup draft entry`, explicit `order_index`, organizer reorder API) before any shared/mobile UI work.

## 2026-03-23 00:08

- Context: Automation resumed the same `EPIC-067` branch and recovery checkpoint for `TASK-068`; the git state was consistent for continuing one backend-only lineup step without taking a new epic.
- Changes: Added migration `V14__lineup_entries_foundation.sql`, lineup persistence/service/routes, and idempotent `approved -> draft lineup entry` materialization with explicit `order_index`. Added organizer/host `GET/PATCH /api/v1/events/{eventId}/lineup`, updated OpenAPI/context docs, and extended targeted server regression coverage for lineup creation/reorder plus migration surface.
- Decisions: Accept `D-067`: approved review status now creates one draft lineup entry exactly once, while reverse-sync/deletion on later application status changes remains intentionally out of scope for this additive slice. `D-065` / `D-066` remain unchanged.
- Next: Keep `EPIC-067` active and execute exactly one next subtask: `TASK-069` for shared/data/feature integration of comedian applications + lineup surfaces before Android/iOS wiring.

## 2026-03-23 02:07

- Context: Automation resumed the same `EPIC-067` branch and completed the next bounded shared/mobile preparation step without taking a new epic or touching Android/iOS UI wiring.
- Changes: Added dedicated `:domain:lineup`, `:data:lineup`, `:feature:lineup`, and `shared/lineup` modules with backend adapters, shared MVI state, Koin wiring, and Swift-friendly bridge/snapshots for comedian submit plus organizer review/reorder flows. Updated active context docs and verification guidance for the new KMP bounded context.
- Decisions: No new governance decision was needed. `D-065` / `D-066` / `D-067` remain unchanged, and the new shared modules consume the existing backend foundation without changing provider or lineup-deletion semantics.
- Next: Keep `EPIC-067` active and execute exactly one next subtask: `TASK-070` for Android/iOS UI wiring and executable platform coverage on top of the delivered shared/data/feature lineup foundation.

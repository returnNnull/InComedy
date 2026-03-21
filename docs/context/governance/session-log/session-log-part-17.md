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

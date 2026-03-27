# Product Backlog

Priority scale:
- P0: required for MVP launch
- P1: high-value after MVP
- P2: later optimization

## P0

Актуальная последовательность реализации (`2026-03-26`):
- Reset the active MVP auth standard to login + password.
- Keep provider-agnostic identity, roles, active-role context, and organizer workspace membership as the internal auth foundation.
- Implement credential auth first, then VK ID as the external provider; phone OTP, Telegram, and Google are no longer part of the active MVP auth scope.
- Keep the external PSP decision deferred until the final pre-publication stage; the delivered ticketing slice remains provider-agnostic until then.
- Provider-agnostic ticket wallet / QR / checker scan surfaces are now delivered on top of the ticketing foundation.
- Comedian applications + organizer approve/reject/waitlist + lineup ordering are now delivered end-to-end, including Android/iOS shells and targeted executable verification.
- Live stage status foundation now includes backend mutation semantics plus shared/data/feature lineup contracts for `current performer` / `next up`.
- Android/iOS UI wiring for organizer live controls and audience-facing current/next state on top of the delivered shared foundation завершены в `EPIC-068`, который теперь принят пользователем и закрыт как `done`.
- `EPIC-069` realtime/WebSocket delivery для live stage updates завершён, принят пользователем и смержен в `main`.
- `EPIC-070` donations/payout foundation уже доставлен по плану `TASK-087 -> TASK-088 -> TASK-089`, явно подтверждён пользователем, смержен в `main` и закрыт как `done`; donations scope по-прежнему нельзя расширять к checkout/webhooks/payout automation без отдельного provider/legal confirmation.
- `EPIC-071` notifications / announcements delivery foundation уже доставлен по плану `TASK-090 -> TASK-092`, review-driven follow-up закрыт, epic явно подтверждён пользователем, смержен в `main` и закрыт как `done`; `R-014` остаётся open до follow-up по `/api/v1/me/notifications`, moderation/reporting, durable outbox и push/background delivery.
- После старта текущего active epic оперативная очередь следующих epic-ов и их порядок ведутся в `product/next-epic-queue.md`.

- Multi-role identity model (Audience, Comedian, Organizer on one account).
- Auth via standard login + password, with provider-agnostic extension points and VK ID as the external provider.
- Real auth completion via backend (`credentials/VK -> internal session`) + secure session restore.
- Organizer workspace with member invitations and permission roles (`owner`, `manager`, `checker`, `host`).
- Venue management and hall template builder v1.
- Event creation/editing/publication and sales lifecycle states.
- Event discovery by city/date/price.
- Seat/zone reservation with hold expiration and protected inventory transitions.
- Ticket checkout foundation, QR ticket issuance, and check-in, with concrete external PSP activation deferred until final pre-publication tasks.
- Comedian applications + organizer approve/reject/waitlist + lineup ordering.
- Live stage status (`current performer`, `next up`) with real-time updates.
- Basic donations to verified comedians with payout verification gate.
- Push notifications and organizer announcements/event feed.
- Audit trail for critical organizer actions.

## P1

- Waitlist for sold-out events and released-seat return to sale.
- Refund and cancellation handling with operator tooling.
- Promo codes and simple campaigns.
- Wallet pass / improved ticket wallet.
- Organizer moderation and incident tools.
- Comedian profile enhancements (media, tags, performance history).
- Organizer analytics (sales, attendance, conversion, lineup stats).
- Recurring event templates and repeated shows.

## P2

- Personalized recommendations.
- Dynamic pricing.
- Advanced payout automation and settlement reports.
- Public audience chat/community layer.
- Multi-city organizer network management.
- Referral and loyalty mechanics.

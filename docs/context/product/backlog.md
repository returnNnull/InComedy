# Product Backlog

Priority scale:
- P0: required for MVP launch
- P1: high-value after MVP
- P2: later optimization

## P0

Current implementation sequence note (`2026-03-24`):
- Reset the active MVP auth standard to login + password.
- Keep provider-agnostic identity, roles, active-role context, and organizer workspace membership as the internal auth foundation.
- Implement credential auth first, then VK ID as the external provider; phone OTP, Telegram, and Google are no longer part of the active MVP auth scope.
- Keep the external PSP decision deferred until the final pre-publication stage; the delivered ticketing slice remains provider-agnostic until then.
- Provider-agnostic ticket wallet / QR / checker scan surfaces are now delivered on top of the ticketing foundation.
- Comedian applications + organizer approve/reject/waitlist + lineup ordering are now delivered end-to-end, including Android/iOS shells and targeted executable verification.
- The next bounded `P0` delivery slice is live stage status (`current performer`, `next up`) on top of the delivered lineup foundation, starting from backend mutation semantics and then moving to realtime/client surfaces.

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

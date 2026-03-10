# Product Backlog

Priority scale:
- P0: required for MVP launch
- P1: high-value after MVP
- P2: later optimization

## P0

Current implementation sequence note (`2026-03-10`):
- Keep Telegram as the only implemented login temporarily, but treat it as the first linked auth identity only.
- Build provider-agnostic identity, roles, active-role context, and organizer workspace membership before adding more auth providers.
- Add VK, Google, and Sign in with Apple on top of that internal identity model before public release.

- Multi-role identity model (Audience, Comedian, Organizer on one account).
- Auth via Telegram, VK, Google, and Sign in with Apple for iOS release.
- Real auth completion via backend (`provider -> internal session`) + secure session restore.
- Organizer workspace with member invitations and permission roles (`owner`, `manager`, `checker`, `host`).
- Venue management and hall template builder v1.
- Event creation/editing/publication and sales lifecycle states.
- Event discovery by city/date/price.
- Seat/zone reservation with hold expiration and protected inventory transitions.
- Ticket checkout via external PSP, QR ticket issuance, and check-in.
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

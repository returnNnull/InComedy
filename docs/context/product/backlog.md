# Product Backlog

Priority scale:
- P0: required for MVP launch
- P1: high-value after MVP
- P2: later optimization

## P0

- Role-based onboarding (Audience, Comedian, Organizer).
- Social authorization via VK, Telegram, and Google.
- Real auth completion via Ktor backend (`code -> token/session`) + deep-link callback wiring on Android/iOS.
- Startup session restore (`token -> /api/v1/auth/session/me`) with automatic auth/main routing.
- Event creation/editing (organizer).
- Event discovery by city/date/price.
- Seat reservation and ticket checkout.
- Ticket QR for check-in.
- Event chat with organizer announcements.
- Comedian application to events + organizer approve/reject.
- Basic donations to comedians.
- Push notifications for status changes (ticket, schedule, application).

## P1

- Event map view and nearby events.
- Refund and cancellation handling.
- Organizer moderation tools (report, mute, ban).
- Comedian profile enhancements (media, tags, performance history).
- Simple organizer analytics (sales, attendance, conversion).

## P2

- Personalized recommendations.
- Dynamic pricing and promo campaigns.
- Advanced payout automation and settlement reports.
- Multi-city organizer account management.

# InComedy Product Brief

## One-liner

Full-cycle standup event platform covering organizer operations, venue-aware ticketing, comedian lineup management, live show state, and donations.

## Problem

- Organizers run standup events across fragmented tools for applications, lineup planning, seating, ticketing, guest lists, and live coordination.
- Comedians lack a transparent workflow for applying, confirming participation, tracking running order, and receiving donations.
- Audience has a disconnected experience between discovery, seat selection, purchase, event entry, and supporting performers.

## Core Roles

1. Audience
2. Comedian
3. Organizer

Additional operational permissions exist inside organizer workspace (`owner`, `manager`, `checker`, `host/coordinator`).

## Core Flows

1. Audience discovers events, selects seat/zone, buys ticket, checks in, follows live stage status, and sends donations.
2. Organizer creates venue and hall template, publishes event, manages sales, guests, applications, lineup, staff responsibilities, and live event state.
3. Comedian applies to events, receives approval/rejection, tracks slot/order, and receives donations through verified payout setup.

## Product Principles

- Operations-first: organizer workflow is more important than broad social features in MVP.
- One account can hold multiple roles; roles are permissions, not separate accounts.
- Venue-aware sales: hall layout, inventory states, and seat holds are first-class domain concepts.
- Live show state matters: current performer and lineup changes are product-level concerns.
- Compliance by design: auth/payment/donation flows must fit App Store / Google Play constraints from the start.

## MVP Scope

- Multi-role onboarding and profile context switching.
- Auth via Telegram, VK, Google, plus Sign in with Apple for iOS release.
- Organizer workspace with team permissions.
- Venue and hall template builder v1.
- Event creation/editing/publication and sales states.
- Ticket reservation, purchase, QR ticket, and check-in.
- Comedian applications, approvals, and lineup management.
- Live stage status (`current performer` / `next up`).
- Basic donation flow for verified comedian payout profiles.
- Push notifications and event announcements/feed.

## Out of Scope (MVP)

- Full public audience chat with advanced moderation.
- Advanced recommendation engine.
- Deep analytics dashboards.
- Complex multi-party payout splitting.
- 3D/fully freeform hall design editor.

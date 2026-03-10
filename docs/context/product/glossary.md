# Glossary

## Terms

- Event: A standup show instance with date, venue, lineup, ticket inventory, and live operational state.
- Lineup: Ordered list of comedians confirmed for an event.
- Slot: Time window assigned to a comedian in the lineup.
- Audience: User role that discovers events, buys tickets, and can donate.
- Comedian: User role that applies to events and performs.
- Organizer: User role that creates events and manages lineup/applications.
- Auth Identity: Link between one internal platform user and one external auth provider account such as Telegram, VK, Google, or Apple.
- Active Role Context: Currently selected user role/home scenario within one account without requiring re-login.
- Organizer Workspace: Organizer-owned workspace containing events, venues, staff members, and permissions.
- Workspace Member: User linked to an organizer workspace with permission role such as `owner`, `manager`, `checker`, or `host`.
- Venue: Reusable event location with address, capacity, and hall templates.
- Hall Template: Saved 2D hall/seating layout used to create event-specific inventory snapshots.
- Event Hall Snapshot: Frozen event-specific copy of a hall template used to preserve sold inventory consistency after template changes.
- Inventory Unit: Sellable ticket unit such as a seat, table, standing zone, or tariff package.
- Seat Hold: Temporary reservation of inventory during checkout before payment confirmation.
- Ticket Order: Purchase entity that groups held inventory, payment, and issued tickets.
- Check-in: Ticket validation process at venue entry (typically via QR).
- Donation: Voluntary payment from audience to comedian.
- Payout Profile: Verified payout identity/configuration required before a comedian can receive donations.
- Audit Log: Immutable record of critical organizer/staff actions such as pricing, refunds, lineup changes, or permission changes.
- Stage Status: Real-time event state that shows who is currently on stage and who is next.
- Event Feed: Organizer announcements and system updates for an event; lighter-weight than full public chat.
- Event Chat: Optional real-time communication channel tied to one event; not mandatory for MVP.
- Intent/State/Effect: MVI primitives for user actions, UI state, and one-time side effects.

## Rule

- Add new domain terms here before broad usage in tickets/specs.

# Decisions Log Part 03

## D-045

- Date: 2026-03-06
- Status: accepted
- Decision: Model product roles as capabilities on a single account rather than separate mutually exclusive accounts.
- Rationale: Real users can be audience, comedian, organizer, or invited event staff at the same time; separate accounts would fragment identity, purchases, and permissions.
- Consequences: User identity must support multiple linked roles and context switching; organizer staff permissions become workspace-scoped rather than standalone account types.

## D-046

- Date: 2026-03-06
- Status: accepted
- Decision: Prioritize organizer operations, venue-aware ticketing, lineup management, live stage status, and check-in for MVP; defer full public audience chat to later phases.
- Rationale: Operational workflows create launch value and revenue sooner, while public chat multiplies moderation and abuse complexity before core event flows are stable.
- Consequences: MVP communication layer is reduced to announcements/event feed + push notifications; broad public chat becomes P1/P2.

## D-047

- Date: 2026-03-06
- Status: accepted
- Decision: Treat hall layouts and ticket inventory as first-class domain models with reusable venue templates and event-specific hall snapshots.
- Rationale: Standup event monetization depends on accurate inventory control; layout editing cannot remain a loose UI concern or free-form text configuration.
- Consequences: Venue/hall/ticketing become explicit bounded contexts; seat holds, order issuance, and hall versioning require dedicated domain invariants and storage models.

## D-048

- Date: 2026-03-06
- Status: accepted
- Decision: Include Sign in with Apple in iOS scope and design donations as a compliance-constrained flow separated from ticket checkout.
- Rationale: Current App Store policy makes third-party-login-only iOS release risky, and donation flows have stricter interpretation than real-world ticket purchases.
- Consequences: Auth scope expands beyond Telegram/VK/Google; donation architecture must support pass-through or web-checkout fallback and verified payout profiles.

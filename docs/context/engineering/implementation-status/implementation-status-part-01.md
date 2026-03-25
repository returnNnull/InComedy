# Implementation Status Part 01

## Current Implementation Status (2026-03-25)

- Implemented:
  - first-party credential registration/login flow across backend, shared auth MVI, Android Compose UI, and iOS SwiftUI UI;
  - Argon2id-backed credential storage/migration plus server-side credential abuse controls for register/login routes;
  - VK ID start/verify flow across backend routes, shared callback parsing, public HTTPS callback bridge with auto-return attempt plus manual fallback, Android official VK OneTap in documented auth-code mode with client-generated `state/PKCE` plus browser fallback, and iOS/browser handoff;
  - auth/session foundation across mobile and server, with mobile split into dedicated `core`, `domain`, `feature`, and `data` responsibilities so role/workspace growth stays out of the auth presentation layer and data adapters depend on domain contracts instead of feature modules;
  - provider-agnostic backend `User + AuthIdentity` persistence foundation that can support multiple auth providers without turning provider ids into primary business ids;
  - backend role storage, active-role context, and organizer workspace create/list plus registered-user invitation inbox, invitation response, roster visibility, and bounded permission-role update routes;
  - shared session-focused ViewModel/bridge state with role context, linked providers, organizer workspace list/create wiring, invitation inbox handling, and workspace membership mutations;
  - organizer venue management foundation across backend migration/repository/routes, shared `domain/data/feature` venue modules, Android Compose, and iOS SwiftUI, covering venue list/create plus hall-template create/update/clone on top of a canonical 2D hall layout schema;
  - organizer event management foundation across backend migration/repository/routes, shared `domain/data/feature` event modules, Android Compose, and iOS SwiftUI, covering event create/list/get/update/publish, `workspace -> venue -> hall template` selection, frozen `EventHallSnapshot` persistence, and text-based organizer editing of event-local price zones, pricing assignments, and availability overrides on top of the canonical hall layout schema;
  - Android root navigation + auth subgraph + post-auth main shell with bottom navigation, home/account tabs, avatar/profile data, role switching, sign-out, workspace create/list, invitation inbox, team roster, invite form, and permission-role edits bound to shared session state;
  - iOS root graph container with auth/main shells + post-auth bottom navigation, home/account tabs, organizer venue/event tabs, avatar/profile data, role switching, sign-out, workspace create/list, invitation inbox, team roster, invite form, permission-role edits, and associated-domain handling for auth return links;
  - auth entry surfaces now expose credentials plus VK while preserving provider-extensible session/identity seams for future providers;
  - session restore/refresh/logout backend contract;
  - operator-only bounded server diagnostics store + retrieval endpoint with request-id correlation, covering the current auth/session/identity/workspace route surface;
  - shared/mobile backend error correlation via surfaced backend request ids in failure messages.
- Partial:
  - VK ID requires runtime browser/public-callback config, optional dedicated Android SDK client config, Apple associated-domain app-id metadata, and live smoke validation before it can be treated as rollout-ready;
  - legacy phone/Telegram/Google-oriented auth code and docs still exist in parts of the repository and must be removed or archived from the active supported surface;
  - organizer workspace team management is intentionally bounded to invites for already registered users by exact login/username lookup, pending invitations via `workspace_members.joined_at IS NULL`, and owner/manager role edits; owner transfer, arbitrary member removal/cancel, and external invitation delivery are still missing;
  - event foundation now includes `create/list/get/update/publish`, sales open/pause/cancel controls, frozen hall snapshots, event-local price/availability overrides, and a public audience discovery route for published public events with bounded `city/date/price` filtering plus audience-safe summaries;
  - ticketing foundation now includes derived `InventoryUnit` persistence from frozen snapshots, event-versioned sync markers, public and authenticated inventory routes, protected hold lifecycle, provider-agnostic checkout order creation, authenticated order-status reads, issued-ticket persistence and QR delivery, checker scan flow, shared `:feature:ticketing` state, Android `Билеты` tab wiring, and iOS `TicketWalletView`; `sold_out` automation, complimentary issuance, refund/cancel ticket lifecycle, wallet pass/export, and check-in stats/offline buffering are still missing;
  - comedian applications and lineup backend foundation now includes submit/review/list/reorder/live-stage mutation with diagnostics and regression coverage; realtime delivery is still missing;
  - comedian applications and lineup shared/mobile foundation now includes dedicated `:domain:lineup`, `:data:lineup`, `:feature:lineup`, and `shared/lineup` modules plus Android/iOS live-stage UI surfaces; richer comedian-facing history and realtime delivery are still missing;
  - current Android/iOS main flow now exposes organizer venue and event surfaces plus audience/staff ticket wallet and check-in surfaces, but deeper organizer operational flows beyond workspaces, venues, events, and ticketing foundations are still missing.

## Next Bounded Contexts

1. realtime/WebSocket delivery for live stage updates
2. donations/payouts
3. notifications
4. analytics

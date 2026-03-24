# Architecture Overview

## High-Level Components

- Mobile Client (`Kotlin Multiplatform`)
  - Core support: `:core:common` для shared primitives и `:core:backend` для backend environment/config + HTTP helper-ов
  - Domain contracts: `:domain:auth`, `:domain:session`, `:domain:venue`, `:domain:event`, и `:domain:ticketing` для бизнес-моделей, портов и use-case contract-ов
  - Auth bounded context: `:feature:auth` + `:data:auth` для auth orchestration, callback parsing, provider launch/verify, refresh, logout
  - Post-auth session context: `:data:session` + shared session orchestration для ролей, active role и organizer workspace/team management
  - Organizer venue context: `:feature:venue` + `:data:venue` для venue catalog, hall template builder form orchestration, и backend venue API adapters
  - Organizer/public event context: `:feature:event` + `:data:event` для organizer event create/list/get/update/publish, sales open/pause/cancel orchestration, audience public discovery filters, venue/template selection, `EventHallSnapshot`, и event/local public API adapters
  - Ticketing foundation context: `:domain:ticketing` + `:data:ticketing` + backend ticketing routes/repository для public/authenticated derived event inventory, protected seat holds, provider-agnostic checkout order lifecycle, issued-ticket / QR contracts, и checker-facing check-in foundation поверх frozen snapshot-а
  - Presentation: shared MVI ViewModels + platform-specific UI (Android Compose, iOS SwiftUI)
  - Domain: use cases and entities
  - Data: repositories, remote/local sources
- Backend (`Ktor` modular monolith)
  - API layer
  - Domain services
  - Persistence and integrations (DB, payments, notifications, auth providers, real-time)
  - Startup applies versioned PostgreSQL migrations before serving traffic

## Core Domain Areas

- Authentication and role onboarding (login + password primary, VK ID as external provider)
- Organizer workspace and staff permissions
- Events and scheduling
- Venues, hall templates, and seat inventory
- Tickets and check-in
- Comedian applications and lineup management
- Live stage status and event announcements/feed
- Donations and payouts

## Current Implementation Status (2026-03-24)

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
  - ticketing foundation now includes derived `InventoryUnit` persistence from frozen snapshots, event-versioned sync markers so inventory GET does not perform a full reconcile on every unchanged read, a public audience inventory route for published public events, authenticated personalized inventory list, hold create/release/expiry routes serialized through inventory-first locking, provider-agnostic checkout order creation from active hold-ов with persisted order lines, authenticated order-status polling through `GET /api/v1/orders/{orderId}`, `pending_payment` inventory blocking, automatic expiry recovery, idempotent issued-ticket creation with dedicated `tickets` persistence, authenticated `GET /api/v1/me/tickets` with QR payload delivery for the buyer, checker/owner/manager `POST /api/v1/checkin/scan` with duplicate-scan semantics plus structured diagnostics, shared `:feature:ticketing` presentation state, Android Compose `Билеты` tab wiring, and iOS SwiftUI `TicketWalletView` surfaces for `My Tickets`, QR presentation, and checker scan UX; the optional YooKassa-specific PSP adapter remains an unapproved disabled-by-default candidate, and its presence in the repository/docs does not mean a PSP has been selected. The shared order/inventory/ticket semantics remain provider-agnostic. `sold_out` automation, complimentary issuance, refund/cancel ticket lifecycle, wallet pass/export, and check-in stats/offline buffering are still not implemented;
  - comedian applications and lineup backend foundation now include backend migrations/persistence plus authenticated routes for comedian submit, organizer owner/manager review (`submit/list/status change`), idempotent `approved -> draft lineup entry` materialization, organizer/host lineup list + reorder with explicit `order_index`, and organizer/host live-stage mutation through `POST /api/v1/events/{eventId}/lineup/live-state` with structured diagnostics, uniqueness guards for `up_next` / `on_stage`, and targeted regression coverage; realtime delivery is still missing;
  - comedian applications and lineup shared/mobile foundation now also includes dedicated `:domain:lineup`, `:data:lineup`, `:feature:lineup`, and `shared/lineup` modules with backend adapters, shared MVI state, Koin wiring, Swift-friendly snapshots/bridge, Android Compose `Lineup` tab wiring, and iOS SwiftUI lineup management surfaces for comedian submit plus organizer review/reorder flows; live-stage shared integration now also exports all lineup status keys, organizer live-stage mutation through the shared feature model, and derived `current performer` / `next up` summary for later platform UI wiring. Targeted Android UI coverage, `composeApp` compilation, and targeted iOS executable verification for `testLineupTabShowsApplicationsAndReorderSurface` are green, while dedicated Android/iOS live controls, richer comedian-facing history, and realtime delivery are still missing;
- current Android/iOS main flow now exposes organizer venue and event surfaces plus audience/staff ticket wallet and check-in surfaces, but deeper organizer operational flows beyond workspaces, venues, events, and ticketing foundations are still missing.
- Planned next bounded contexts:
  - Android/iOS UI wiring for current performer / next up / organizer live controls on top of the delivered shared live-stage foundation,
  - realtime/WebSocket delivery for live stage updates,
  - donations/payouts,
  - notifications,
  - analytics.

## Dependency Direction (Client)

- Compile-time dependencies follow:
  - `feature -> domain`
  - `data -> domain`
  - `core` is shared technical support and may be used by both `feature` and `data`
  - `domain` does not depend on `feature` or `data`
- External frameworks/providers are accessed through adapters/interfaces.

## ViewModel Integration (KMP)

- Shared ViewModels live in `commonMain` and expose state/events via coroutines flows.
- Android consumes shared ViewModels through native AndroidX `ViewModel` wrappers.
- iOS consumes shared ViewModels through native Swift `ObservableObject` wrappers/bridges.

## Navigation (Android)

- Root navigation host is centralized in app layer (`composeApp`) and owns top-level graph boundaries.
- Each feature contributes a dedicated nested subgraph from its navigation package.
- Cross-feature navigation is orchestrated via graph callbacks/events; `ViewModel` stays platform-agnostic and does not depend on `NavController`.

## Navigation (iOS)

- Root app navigation is centralized in app layer (`iosApp`) via app graph container + navigator state.
- Each feature contributes a dedicated SwiftUI graph view (`Features/<Feature>/Navigation/*`).
- Cross-feature transitions are coordinated by app/graph layer; shared `ViewModel` remains platform-agnostic and does not depend on navigation framework types.

## Notes

- Keep module boundaries aligned with feature domains and Clean architecture rules.
- Keep auth entry flows, post-auth session context, domain contracts, and shared backend environment/config in separate mobile modules so organizer role/workspace growth does not bloat `auth` and data adapters never need to depend on feature modules for business contracts.
- PostgreSQL schema evolution must live in versioned migration files; application code should invoke migration execution, not own mutable schema DDL as business logic.
- Internal identity must be modeled provider-agnostically (`User` + linked auth identities); provider-specific ids must not become the primary keys for profile, RBAC, or workspace domains.
- Password-based auth is implemented as a first-party backend flow with secure password hashing and credential-abuse controls; VK ID plugs into the same internal user/session model as a linked external identity.
- The canonical public VK callback contract remains `https://incomedy.ru/auth/vk/callback`; it is still used for browser/public-callback completion and for non-SDK fallback flows. Android now prefers official VK OneTap in documented auth-code mode when both the app and backend have dedicated Android VK client config: the Android client generates `state`, `codeVerifier`, and `codeChallenge` locally, passes them to VK SDK/OneTap, then sends `code + deviceId + state + codeVerifier` to backend `/verify`, while browser/public-callback completion continues to use backend-issued signed state. This keeps the app session model unchanged while separating browser and Android SDK VK lifecycles the way VK documents them.
- Treat seat inventory, ticketing, lineup live state, and donations as separate bounded contexts even when implemented in one backend app.
- Prefer REST for CRUD and WebSocket/push for live event updates.
- For PSP/push/payout side effects, prefer transactional outbox + background workers once those domains are introduced.
- Cross-cutting diagnostics infrastructure must stay sanitized and operator-only; client-visible correlation should use request ids rather than exposing raw server logs.
- Update this file when introducing major modules or cross-cutting infrastructure.

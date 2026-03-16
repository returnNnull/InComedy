# Architecture Overview

## High-Level Components

- Mobile Client (`Kotlin Multiplatform`)
  - Core support: `:core:common` для shared primitives и `:core:backend` для backend environment/config + HTTP helper-ов
  - Domain contracts: `:domain:auth`, `:domain:session`, `:domain:venue`, и `:domain:event` для бизнес-моделей, портов и use-case contract-ов
  - Auth bounded context: `:feature:auth` + `:data:auth` для auth orchestration, callback parsing, provider launch/verify, refresh, logout
  - Post-auth session context: `:data:session` + shared session orchestration для ролей, active role и organizer workspace/team management
  - Organizer venue context: `:feature:venue` + `:data:venue` для venue catalog, hall template builder form orchestration, и backend venue API adapters
  - Organizer event context: `:feature:event` + `:data:event` для event create/list/get/update/publish, sales open/pause/cancel orchestration, venue/template selection, `EventHallSnapshot`, и event-local override API adapters
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

## Current Implementation Status (2026-03-17)

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
  - event foundation now includes `create/list/get/update/publish`, sales open/pause/cancel controls, frozen hall snapshots, and event-local price/availability overrides, but ticket inventory transitions and `sold_out` automation are still not implemented;
  - current Android/iOS main flow now exposes organizer venue and event surfaces, including event lifecycle controls and override editing, but deeper organizer operational flows beyond workspaces, venues, and event foundations are still missing.
- Planned next bounded contexts:
  - lineup,
  - ticketing/check-in,
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

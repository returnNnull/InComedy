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

## Current Delivery Status

- Current delivery status and next bounded contexts now live in [implementation-status.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/implementation-status.md).
- Keep this document architecture-level; do not append per-epic delivery history here.

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

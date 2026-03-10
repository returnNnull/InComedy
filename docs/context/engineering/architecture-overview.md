# Architecture Overview

## High-Level Components

- Mobile Client (`Kotlin Multiplatform`)
  - Presentation: shared MVI ViewModels + platform-specific UI (Android Compose, iOS SwiftUI)
  - Domain: use cases and entities
  - Data: repositories, remote/local sources
- Backend (`Ktor` modular monolith)
  - API layer
  - Domain services
  - Persistence and integrations (DB, payments, notifications, auth providers, real-time)

## Core Domain Areas

- Authentication and role onboarding (VK/Telegram/Google auth)
- Organizer workspace and staff permissions
- Events and scheduling
- Venues, hall templates, and seat inventory
- Tickets and check-in
- Comedian applications and lineup management
- Live stage status and event announcements/feed
- Donations and payouts

## Current Implementation Status (2026-03-10)

- Implemented:
  - auth/session foundation across mobile and server;
  - shared session-focused ViewModel/bridge state;
  - Android root navigation + auth subgraph;
  - iOS root graph container with auth/main shells;
  - Telegram verify + session restore/refresh/logout backend contract.
- Partial:
  - VK and Google auth provider wiring exists in mobile data layer, but server-backed unified identity exchange is not complete;
  - iOS bridge/navigation rollout is structural and still limited to auth/main placeholder scope.
- Planned next bounded contexts:
  - identity/roles,
  - organizer workspace,
  - venues,
  - events,
  - lineup,
  - ticketing/check-in,
  - donations/payouts,
  - notifications,
  - analytics.

## Dependency Direction (Client)

- `presentation -> domain -> data`
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
- Internal identity must be modeled provider-agnostically (`User` + linked auth identities); provider-specific ids must not become the primary keys for profile, RBAC, or workspace domains.
- Treat seat inventory, ticketing, lineup live state, and donations as separate bounded contexts even when implemented in one backend app.
- Prefer REST for CRUD and WebSocket/push for live event updates.
- For PSP/push/payout side effects, prefer transactional outbox + background workers once those domains are introduced.
- Update this file when introducing major modules or cross-cutting infrastructure.

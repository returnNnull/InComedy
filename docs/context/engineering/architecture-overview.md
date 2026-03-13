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
  - Startup applies versioned PostgreSQL migrations before serving traffic

## Core Domain Areas

- Authentication and role onboarding (VK/Telegram/Google auth)
- Organizer workspace and staff permissions
- Events and scheduling
- Venues, hall templates, and seat inventory
- Tickets and check-in
- Comedian applications and lineup management
- Live stage status and event announcements/feed
- Donations and payouts

## Current Implementation Status (2026-03-13)

- Implemented:
  - auth/session foundation across mobile and server;
  - provider-agnostic backend `User + AuthIdentity` persistence foundation behind the current Telegram login;
  - backend role storage, active-role context, and minimal organizer workspace create/list routes;
  - shared session-focused ViewModel/bridge state with role context, linked providers, and organizer workspace list/create wiring;
  - Android root navigation + auth subgraph + post-auth main shell with bottom navigation, home/account tabs, avatar/profile data, role switching, sign-out, and workspace create/list bound to shared session state;
  - iOS root graph container with auth/main shells + post-auth bottom navigation, home/account tabs, avatar/profile data, role switching, sign-out, and workspace create/list bound to shared session state;
  - official Telegram OIDC browser auth start (`/api/v1/auth/telegram/start`) that returns a first-party `https://incomedy.ru/auth/telegram/launch` URL, where backend-owned PKCE/state are resumed into Telegram browser auth before the HTTPS callback bridge returns into `incomedy://auth/telegram`, followed by backend code exchange / `id_token` verification before session issuance;
  - Telegram session restore/refresh/logout backend contract;
  - operator-only bounded server diagnostics store + retrieval endpoint with request-id correlation, covering the current auth/session/identity/workspace route surface;
  - shared/mobile backend error correlation via surfaced backend request ids in failure messages.
- Partial:
  - VK and Google auth provider wiring exists in mobile data layer, but server-backed linked-identity exchange is not complete;
  - organizer workspace currently supports owner create/list only; invites, member management, and permission editing are still missing;
  - current Android/iOS main flow exposes a foundation shell for account/workspace context, but dedicated organizer feature graphs and deeper operational flows are still missing.
- Planned next bounded contexts:
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
- PostgreSQL schema evolution must live in versioned migration files; application code should invoke migration execution, not own mutable schema DDL as business logic.
- Internal identity must be modeled provider-agnostically (`User` + linked auth identities); provider-specific ids must not become the primary keys for profile, RBAC, or workspace domains.
- Treat seat inventory, ticketing, lineup live state, and donations as separate bounded contexts even when implemented in one backend app.
- Prefer REST for CRUD and WebSocket/push for live event updates.
- For PSP/push/payout side effects, prefer transactional outbox + background workers once those domains are introduced.
- Cross-cutting diagnostics infrastructure must stay sanitized and operator-only; client-visible correlation should use request ids rather than exposing raw server logs.
- Update this file when introducing major modules or cross-cutting infrastructure.

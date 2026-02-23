# Architecture Overview

## High-Level Components

- Mobile Client (`Kotlin Multiplatform`)
  - Presentation: shared MVI ViewModels + platform-specific UI (Android Compose, iOS SwiftUI)
  - Domain: use cases and entities
  - Data: repositories, remote/local sources
- Backend (`Ktor`)
  - API layer
  - Domain services
  - Persistence and integrations (DB, payments, notifications)

## Core Domain Areas

- Authentication and role onboarding (VK/Telegram/Google auth)
- Events and scheduling
- Tickets and check-in
- Comedian applications and lineup management
- Event chat and moderation
- Donations and payouts

## Dependency Direction (Client)

- `presentation -> domain -> data`
- External frameworks/providers are accessed through adapters/interfaces.

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
- Update this file when introducing major modules or cross-cutting infrastructure.

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

## Notes

- Keep module boundaries aligned with feature domains and Clean architecture rules.
- Update this file when introducing major modules or cross-cutting infrastructure.

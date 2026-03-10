# InComedy

InComedy is a Kotlin Multiplatform + Ktor repository for a standup-event platform covering organizer operations, venue-aware ticketing, lineup/live-show management, and comedian donations.

## Current Status

Repository snapshot: `2026-03-10`.

- Implemented foundation:
  - mobile auth/session flow with shared `MVI` logic;
  - Telegram backend verification and internal JWT session issuance;
  - refresh-token rotation, secure mobile token storage, and auth security hardening;
  - Android root `NavHost` + feature subgraph structure;
  - iOS root graph container with auth/main graph shells;
  - OpenAPI contract and CI/CD/deploy baseline for the current server scope.
- Partial:
  - VK and Google auth launch flows exist in client data layer, but backend identity exchange is still stubbed;
  - iOS Sign in with Apple is not implemented yet.
- Not started:
  - multi-role profile/workspace model;
  - venues, hall templates, and events;
  - ticketing/check-in;
  - comedian applications, lineup, and live stage status;
  - donations, notifications, analytics.

## Repository Structure

- `composeApp/`: Android application shell and platform UI.
- `iosApp/`: native SwiftUI application and iOS navigation layer.
- `shared/`: shared bridges, DI, and app/session state for mobile clients.
- `core/common/`: cross-platform core utilities.
- `feature/auth/`: shared auth domain and `MVI` ViewModel logic.
- `data/auth/`: auth provider integrations and backend API adapters.
- `server/`: Ktor backend for current auth/session scope.
- `deploy/server/`: Docker Compose and Caddy deployment assets.
- `docs/context/`: compact project memory and governance records.
- `docs/standup-platform-ru/`: detailed Russian-language target-state product/technical specification.

## Useful Commands

```bash
./gradlew :composeApp:assembleDebug
./gradlew :feature:auth:allTests
./gradlew :server:test :server:installDist
./gradlew :server:run
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` target.

## Documentation

- `docs/context/README.md`: compact source of truth for product, engineering, and governance context.
- `docs/standup-platform-ru/README.md`: detailed target-state specification package.
- `docs/standup-platform-ru/11-статус-реализации-на-2026-03-10.md`: current repo-to-spec alignment snapshot.

When scope, decisions, or implementation status change, update the relevant documentation in the same change.

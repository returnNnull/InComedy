# InComedy

InComedy is a Kotlin Multiplatform + Ktor repository for a standup-event platform covering organizer operations, venue-aware ticketing, lineup/live-show management, and comedian donations.

## Current Status

Repository snapshot: `2026-03-14`.

- Implemented foundation:
  - first-party credential auth flow with shared `MVI` logic across Android/iOS and backend-issued sessions;
  - VK ID auth flow wired through shared mobile callback handling and backend verification contracts, with Android preferring the official VK ID Android SDK when configured and falling back to browser/public-callback launch otherwise;
  - provider-agnostic backend user/auth-identity persistence foundation;
  - backend role storage, active-role switching, and minimal organizer workspace create/list routes;
  - versioned backend database migrations for clean deploys and in-place schema upgrades;
  - refresh-token rotation, secure mobile token storage, and auth security hardening;
  - Android root `NavHost` + feature subgraph structure;
  - iOS root graph container with auth/main graph shells;
  - OpenAPI contract and CI/CD/deploy baseline for the current server scope;
  - product-level auth standard set to `login + password`, with `VK ID` as the supported external provider.
- Partial:
  - VK ID now has a public HTTPS callback bridge and iOS associated-domain plumbing in the repository, but still requires runtime client configuration, Apple app-id metadata, and live smoke validation before rollout;
  - legacy Telegram/Google/phone-first assumptions still exist in parts of the repository and should be archived or removed from the active supported surface;
  - iOS Sign in with Apple is not implemented yet.
- Not started:
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
- `server/`: Ktor backend for current session/identity/workspace scope.
- `deploy/server/`: Docker Compose and Caddy deployment assets.
- `docs/context/`: compact project memory and governance records.
- `docs/standup-platform-ru/`: detailed Russian-language target-state product/technical specification.

## Useful Commands

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:assembleRelease
./gradlew :feature:auth:allTests
./gradlew :server:test :server:installDist
./gradlew :server:run
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` target.

Android release signing is wired through Gradle using the local keystore at `signing/android/incomedy-release.jks` plus ignored credentials from `signing/android/release-signing.properties` or `INCOMEDY_RELEASE_*` Gradle/environment properties.

Android VK SDK auth is enabled only when the following properties are provided through the git-ignored root `local.properties`, `~/.gradle/gradle.properties`, or environment variables:

- `INCOMEDY_VK_ANDROID_CLIENT_ID`
- `INCOMEDY_VK_ANDROID_CLIENT_SECRET`
- Optional `INCOMEDY_VK_ANDROID_REDIRECT_HOST` (`vk.ru` by default)
- Optional `INCOMEDY_VK_ANDROID_REDIRECT_SCHEME` (`vk<client_id>` by default)

The preferred project-local setup for this public repository is the root `local.properties` file:

```properties
INCOMEDY_VK_ANDROID_CLIENT_ID=
INCOMEDY_VK_ANDROID_CLIENT_SECRET=
```

Leaving those values empty keeps the browser/public-callback fallback active.

## Documentation

- `docs/context/README.md`: compact source of truth for product, engineering, and governance context.
- `docs/standup-platform-ru/README.md`: detailed target-state specification package.
- `docs/standup-platform-ru/11-статус-реализации-на-2026-03-10.md`: current repo-to-spec alignment snapshot.

When scope, decisions, or implementation status change, update the relevant documentation in the same change.

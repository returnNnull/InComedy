# Tooling Stack

This document tracks the approved and planned technology stack for InComedy.

Status labels:
- `confirmed`: approved and should be used by default.
- `candidate`: under evaluation.
- `deferred`: intentionally postponed.

## Confirmed

- Backend server: `Ktor` (Kotlin)
- Mobile app: `Kotlin Multiplatform` (Android + iOS)
- Dependency Injection: `Koin` (KMP-first, shared across mobile/server layers)
- Android navigation: `androidx.navigation:navigation-compose`
- Database: `PostgreSQL`
- Database migrations: `Flyway`
- Redis: shared short-lived state / distributed coordination / rate limiting
- CI/CD: `GitHub Actions`
- Server packaging/runtime: `Docker` + `Docker Compose`
- Auth architecture: provider-agnostic internal identity/session foundation with login + password as the active product login standard
- External auth provider: `VK ID`
- Android VK auth transport: official `VK ID Android SDK` in auth-code mode with backend verification/exchange and internal session issuance
- Password hashing: `Argon2id`
- Android release signing: Gradle-managed release signing with a local ignored keystore/properties file
- iOS deep-linking: custom scheme + associated domains for production auth return flows

## Candidate

- Payments provider:
  - YooKassa (primary RU-market candidate)
  - CloudPayments (fallback candidate)
- Push notifications provider:
  - Firebase Cloud Messaging
  - Apple Push Notification service
- Real-time transport:
  - Ktor WebSockets for live lineup/stage/event updates
- Object storage:
  - S3-compatible storage for media/assets if comedian media or venue assets expand
- Analytics stack: TBD
- KMP iOS interop helpers:
  - KMP-NativeCoroutines (bridge Flow/suspend to Swift async/Combine)
  - KMP-ObservableViewModel (optional ViewModel bridge convenience)

## Decision Rule

- Any stack change must be reflected here and in `decisions-log.md` before large implementation starts.

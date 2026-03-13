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
- Active Telegram auth: official `oauth.telegram.org` OIDC authorization-code flow with backend `/token` exchange, PKCE, and HTTPS callback bridge back into the app

## Candidate

- Authentication providers:
  - VK ID
  - Google Identity / Credential Manager wrappers
  - Sign in with Apple (required for iOS release if third-party login remains)
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

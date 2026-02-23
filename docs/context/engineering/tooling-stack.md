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
- CI/CD: `GitHub Actions`
- Server packaging/runtime: `Docker` + `Docker Compose`

## Candidate

- Authentication providers:
  - Telegram login (server verify + mobile callback wiring) in progress
  - VK: TBD (next)
  - Google: TBD (next)
- Payments provider: TBD
- Push notifications provider: TBD
- Analytics stack: TBD
- KMP iOS interop helpers:
  - KMP-NativeCoroutines (bridge Flow/suspend to Swift async/Combine)
  - KMP-ObservableViewModel (optional ViewModel bridge convenience)

## Decision Rule

- Any stack change must be reflected here and in `decisions-log.md` before large implementation starts.

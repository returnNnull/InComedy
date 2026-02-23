# Task Request Template

Use this template for new implementation tasks.

## Context

- Related docs/decisions:
- Current constraints:

## Goal

- What should be delivered:

## Scope

- In scope:
- Out of scope:

## Constraints

- Tech/business constraints:
- Deadlines or milestones:

## Definition of Done

- Functional result:
- Required tests:
- Required docs updates:

---

## Latest Formalized Request

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (`P0`: social auth, deep-link callback)
  - `docs/context/engineering/tooling-stack.md`
  - `D-012` (platform-specific UI split), `D-015` (Koin DI standard)
- Current constraints:
  - Keep platform-specific UI split (Android Compose, iOS SwiftUI).
  - Do not change auth business logic during navigation library onboarding.

## Goal

- What should be delivered:
  - Add an Android navigation library to project dependencies.
  - Wire minimal navigation host so app entry goes through a navigation graph.

## Scope

- In scope:
  - `gradle/libs.versions.toml` dependency alias for navigation.
  - `composeApp/build.gradle.kts` dependency wiring.
  - `composeApp/src/main/kotlin/com/bam/incomedy/App.kt` minimal `NavHost`.
- Out of scope:
  - Multi-screen redesign.
  - iOS navigation refactor.
  - Auth backend/deep-link completion.

## Constraints

- Tech/business constraints:
  - Follow `MVI` and existing feature boundaries.
  - Keep current auth UX and DI initialization.
- Deadlines or milestones:
  - Prepare base navigation layer for the next implementation block.

## Definition of Done

- Functional result:
  - Android app uses configured navigation library and renders auth route via `NavHost`.
- Required tests:
  - Build verification for `composeApp` and existing auth unit tests remain green.
- Required docs updates:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Navigation Subgraphs)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md` (MVI/effect-driven navigation)
  - `D-020` (Android navigation standard)
- Current constraints:
  - Keep current auth UI behavior unchanged.
  - Prepare scalable navigation structure for upcoming feature growth.

## Goal

- What should be delivered:
  - Reorganize Android navigation into root host + feature subgraph.
  - Move auth route registration into dedicated auth navigation package.

## Scope

- In scope:
  - `AppNavHost` root navigation container.
  - `AuthGraph` nested graph and route constants.
  - `App.kt` wiring through new navigation layer.
- Out of scope:
  - New screens and cross-feature transitions.
  - iOS navigation updates.

## Constraints

- Tech/business constraints:
  - Keep platform-specific UI split.
  - Keep ViewModel free from `NavController` dependencies.
- Deadlines or milestones:
  - Deliver as base structure for next feature navigation increments.

## Definition of Done

- Functional result:
  - App boots through root `NavHost` and renders auth screen via auth subgraph.
- Required tests:
  - `composeApp` debug assembly succeeds.
- Required docs updates:
  - `docs/context/governance/session-log/*`
  - `docs/context/governance/decision-traceability.md`

---

## Latest Formalized Request (Rule + Full Commit)

## Context

- Related docs/decisions:
  - `D-020` (navigation-compose standard),
  - current navigation subgraph implementation in `composeApp`.
- Current constraints:
  - Keep documentation as source of truth.
  - Include script changes in the same final commit.

## Goal

- What should be delivered:
  - Add explicit rule that navigation must be decomposed into subgraphs.
  - Commit all pending workspace changes, including script updates.

## Scope

- In scope:
  - Context updates in engineering/governance docs.
  - Full git commit of current working tree changes.
- Out of scope:
  - New feature screens implementation.

## Constraints

- Tech/business constraints:
  - Rules must be documented before implementation/commit completion.
- Deadlines or milestones:
  - Complete in current session.

## Definition of Done

- Functional result:
  - Rule is documented and decision recorded.
  - All pending changes are committed, including `scripts/*`.
- Required tests:
  - `:composeApp:assembleDebug`.
- Required docs updates:
  - `engineering-standards`, `architecture-overview`, `decisions-log`, `decision-traceability`, `session-log`.

---

## Latest Formalized Request (iOS Navigation Graphs)

## Context

- Related docs/decisions:
  - `D-022` (Android subgraph rule),
  - current iOS app starts directly from auth screen via `ContentView`.
- Current constraints:
  - Keep iOS UI platform-specific and SwiftUI-based.
  - Preserve existing auth UI behavior.

## Goal

- What should be delivered:
  - Add scalable iOS navigation architecture with root graph container and feature graphs.
  - Route current auth flow through feature-owned graph view.

## Scope

- In scope:
  - iOS navigation container in app layer.
  - Auth graph and placeholder post-auth graph.
  - Documentation updates with explicit iOS navigation rule.
- Out of scope:
  - Full implementation of post-auth screens.

## Constraints

- Tech/business constraints:
  - Shared ViewModel stays navigation-framework-agnostic.
  - Cross-feature transitions handled in app/graph layer.
- Deadlines or milestones:
  - Complete in current iteration as structural foundation.

## Definition of Done

- Functional result:
  - `ContentView` renders root graph, auth is hosted in feature graph, and auth success can transition to main graph placeholder.
- Required tests:
  - `xcodebuild` iOS compile check (if environment allows).
- Required docs updates:
  - `engineering-standards`, `architecture-overview`, `decisions-log`, `decision-traceability`, `session-log`.

---

## Latest Formalized Request (iOS BridgeHandle Compile Fix)

## Context

- Related docs/decisions:
  - existing iOS bridge adapter base (`BridgeBackedObservableObject`),
  - `D-023` iOS navigation structure rollout.
- Current constraints:
  - Fix must not change shared Kotlin bridge contracts.
  - Keep iOS bridge lifecycle disposal behavior.

## Goal

- What should be delivered:
  - Remove compile-time dependency on unresolved `BridgeHandle` Swift type in iOS app target.

## Scope

- In scope:
  - `iosApp/iosApp/Common/Bridge/BridgeBackedObservableObject.swift` type handling update.
- Out of scope:
  - Kotlin bridge API redesign.
  - Navigation flow changes.

## Constraints

- Tech/business constraints:
  - Maintain safe disposal of active binding handle.
- Deadlines or milestones:
  - Immediate unblock for iOS run/build.

## Definition of Done

- Functional result:
  - iOS code no longer references unknown `BridgeHandle` type and compiles past this point.
- Required tests:
  - iOS compile check in Xcode environment.
- Required docs updates:
  - `session-log`.

---

## Latest Formalized Request (KMP Native VM Wrappers)

## Context

- Related docs/decisions:
  - Existing shared auth ViewModel in `commonMain`.
  - User-approved integration recipe for KMP VM usage on Android/iOS.
- Current constraints:
  - Preserve shared MVI contracts (`StateFlow` + `Event Flow`).
  - Add native platform wrappers for lifecycle-safe consumption.

## Goal

- What should be delivered:
  - Implement Android native ViewModel wrapper around shared auth VM.
  - Keep/align iOS ObservableObject wrapper pattern for shared VM consumption.
  - Register this pattern as explicit engineering rule.

## Scope

- In scope:
  - Android auth wrapper + UI wiring changes.
  - Context governance updates for new rule/decision.
- Out of scope:
  - Full iOS flow refactor beyond compile stabilization.

## Constraints

- Tech/business constraints:
  - Shared VM remains platform-agnostic and framework-free.
  - Lifecycle cleanup must be explicit in native wrappers.
- Deadlines or milestones:
  - Apply in current auth feature iteration.

## Definition of Done

- Functional result:
  - Android auth UI consumes native ViewModel wrapper and lifecycle-aware state collection.
  - Rule is documented and traceable in governance docs.
- Required tests:
  - `:composeApp:assembleDebug`.
- Required docs updates:
  - `engineering-standards`, `architecture-overview`, `decisions-log`, `decision-traceability`, `session-log`.

---

## Latest Formalized Request (iOS Wrapper Simplification)

## Context

- Related docs/decisions:
  - Ongoing `D-023`/`D-024` rollout for iOS graph and native wrapper patterns.
- Current constraints:
  - Keep lifecycle-safe cleanup.
  - Reduce accidental complexity in iOS layer.

## Goal

- What should be delivered:
  - Make iOS auth wrapper/navigation code easier to read and maintain while preserving behavior.

## Scope

- In scope:
  - Remove extra base wrapper class in iOS auth stack.
  - Simplify auth graph view structure for current single-screen flow.
- Out of scope:
  - New post-auth feature implementation.

## Constraints

- Tech/business constraints:
  - Keep explicit binding disposal.
  - Keep platform-specific iOS wrapper pattern.
- Deadlines or milestones:
  - Immediate cleanup in current session.

## Definition of Done

- Functional result:
  - iOS auth wrapper is self-contained and easier to reason about.
- Required tests:
  - iOS compile check in Xcode environment.
- Required docs updates:
  - `session-log`.

---

## Пример (на русском)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (P0: social auth)
  - `D-015` (Koin), `D-012` (platform-specific UI)
- Current constraints:
  - не менять платежный модуль,
  - не трогать release-процесс,
  - сохранить текущий UX auth-кнопок.

## Goal

- What should be delivered:
  - подключить deep-link callback для auth на Android/iOS,
  - прокинуть callback в shared ViewModel,
  - показать понятный error/success status в UI.

## Scope

- In scope:
  - `composeApp` auth callback wiring,
  - `iosApp` auth callback wiring,
  - `shared/feature/auth` обработка callback.
- Out of scope:
  - новый дизайн экрана,
  - рефактор платежей,
  - внедрение feature flags.

## Constraints

- Tech/business constraints:
  - соблюдать `MVI`,
  - использовать `Koin` DI,
  - сохранить platform-specific UI split.
- Deadlines or milestones:
  - MVP-ready к концу спринта.

## Definition of Done

- Functional result:
  - успешный OAuth callback завершает авторизацию на обеих платформах.
- Required tests:
  - happy path, error path, edge case для callback.
- Required docs updates:
  - `session-log`, `decisions-log` (если появилось новое решение), `decision-traceability`.

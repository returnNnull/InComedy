# Task Request Template Part 04

## Latest Formalized Request (Client Roles and Workspace Integration)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (`P0` sequencing note)
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/test-strategy.md`
  - `D-045`, `D-049`, `D-050`
- Current constraints:
  - Backend identity, active-role, and organizer workspace create/list routes already exist.
  - Mobile/shared layers still expose only basic authorized session info and sign-out.
  - `docs/context/*` remains the primary source of truth and must stay synchronized with implementation status.

## Goal

- What should be delivered:
  - Wire provider-agnostic role context and organizer workspace foundation into shared/mobile client flows so the current main experience can surface roles, active role, linked providers, and organizer workspaces instead of only a bare authorized session.

## Scope

- In scope:
  - shared session state expansion for roles, active role, linked providers, and organizer workspaces
  - data-layer clients for role switching and workspace list/create endpoints
  - Android and iOS main-screen wiring for the new shared session capabilities
  - automated tests for shared session behavior and any changed auth/session mapping
  - context/governance sync for the implementation result
- Out of scope:
  - organizer member invitation flows and permission editing
  - venue, event, ticketing, lineup, donation domains
  - adding new auth providers in this slice

## Constraints

- Tech/business constraints:
  - Keep the existing Telegram login, session restore, and sign-out flow working.
  - Do not reintroduce provider-specific identity coupling into client/session state.
  - Respect current backend contracts instead of inventing parallel client-only role/workspace models.
- Deadlines or milestones:
  - This is the next repository implementation slice after backend identity/workspace foundation and migration rollout.

## Definition of Done

- Functional result:
  - Authorized users can see current role context and organizer workspaces through the shared session layer, switch active role, and create/list organizer workspaces from the current main flow.
- Required tests:
  - shared/session tests for role and workspace state transitions
  - `./gradlew :feature:auth:allTests`
  - `./gradlew :shared:allTests`
  - `./gradlew :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Post-Auth Main Navigation and Account Screen)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/engineering/architecture-overview.md`
  - `D-038`, `D-045`, `D-049`
- Current constraints:
  - Current post-auth flow lands on a simple main screen without bottom navigation.
  - Account information, role switching, and sign-out exist only as a temporary foundation view.
  - A new engineering rule must require class/method/field comments for repository code, and the files edited in the previous task must be brought into compliance.

## Goal

- What should be delivered:
  - Replace the temporary post-auth UI with a main shell that includes bottom navigation and an account tab showing avatar/photo, profile data, role switching, and sign-out.

## Scope

- In scope:
  - Android main screen with bottom menu and account tab
  - iOS main screen with bottom menu and account tab
  - UI tests for the new post-auth UI layer where the repository test infrastructure supports execution
  - mandatory code-comment rule in context docs/governance
  - class/method/field comments for code files edited in the previous task and any new/changed files in this task
- Out of scope:
  - organizer invitation/member management flows
  - venue, event, ticketing, lineup, donation domains
  - redesign of auth provider flows

## Constraints

- Tech/business constraints:
  - Keep platform-specific UI separation (`Compose` on Android, `SwiftUI` on iOS).
  - Reuse the shared session state as the source of profile and role data.
  - If a platform lacks executable UI-test infrastructure in-repo, do not fake coverage; either add a real runnable target or document the limitation explicitly.
- Deadlines or milestones:
  - Complete in the current session together with context synchronization.

## Definition of Done

- Functional result:
  - After authorization, users land on a main shell with bottom navigation, and the account area shows photo/avatar, profile fields, role switch controls, and sign-out.
- Required tests:
  - automated UI tests for the Android post-auth UI flow
  - runnable verification for iOS UI path if a real test target exists; otherwise explicit limitation note plus build verification
  - `./gradlew :feature:auth:allTests`
  - `./gradlew :shared:allTests`
  - `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build`
- Required docs updates:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Android Startup Crash and Network Permission Verification)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/engineering/architecture-overview.md`
  - `D-045`, `D-049`, `D-051`
- Current constraints:
  - Android runtime crashes on `MainActivity` startup because `AuthAndroidViewModel` is created through the default `ViewModelProvider` path.
  - The log also reports network failures that look like a missing `INTERNET` permission, so current manifest state must be verified rather than assumed.
  - `docs/context/*` remains the primary source of truth and task formalization is required before implementation.

## Goal

- What should be delivered:
  - Restore Android app startup by switching auth/session Android adapters to a deterministic `ViewModel` creation path and verify that network permission is declared in the shipped app manifest.

## Scope

- In scope:
  - fix `AuthAndroidViewModel` creation in `MainActivity`
  - fix `SessionAndroidViewModel` creation in the Compose navigation path if it relies on the same default factory behavior
  - verify Android manifest network permission declaration in current source
  - add regression coverage for the explicit Android `ViewModel` factory path
  - sync governance/task context with the fix result
- Out of scope:
  - backend auth/provider changes
  - iOS changes
  - redesign of auth/session flows

## Constraints

- Tech/business constraints:
  - Keep the shared KMP auth/session state and existing Android adapters intact.
  - Do not remove the secure token storage behavior from `AuthAndroidViewModel`.
  - If the network-permission issue is already fixed in source, document that finding rather than inventing a second code change.
- Deadlines or milestones:
  - Complete in the current session as a hotfix-level follow-up to the previous Android UI slice.

## Definition of Done

- Functional result:
  - Android app no longer depends on reflective default `ViewModel` creation for auth/session adapters and can start without the reported crash.
  - Current source confirms the `INTERNET` permission state for Android networking.
- Required tests:
  - regression test for the Android `ViewModel` factory/helper
  - `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Android UI Coverage Iteration 1)

## Context

- Related docs/decisions:
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/engineering/engineering-standards.md`
  - `D-045`, `D-049`, `D-051`
- Current constraints:
  - Android UI coverage currently focuses on the post-auth main shell only.
  - `MainScreen` tests still rely on several literal strings and do not cover important state combinations such as empty data, loading locks, and fallback profile values.
  - `AuthScreen` is not covered by Android UI tests yet, and the task must be reflected in `docs/context/*` before implementation.

## Goal

- What should be delivered:
  - Complete the first Android UI test expansion pass by hardening `MainScreen` testability, adding reusable test-state factories, and covering `AuthScreen` behavior through executable Robolectric Compose UI tests.

## Scope

- In scope:
  - add stable Android UI tags for `MainScreen` stateful elements that are currently asserted via full literal text
  - expand `MainScreen` UI tests for empty, loading, disabled-action, and fallback-profile states
  - extract test-friendly `AuthScreen` content where needed without changing auth flow behavior
  - add Android UI tests for `AuthScreen` provider actions, loading, error, and authorized states
  - introduce Android test-state factories/helpers for `SessionState` and `AuthState`
  - synchronize context docs with the expanded Android UI coverage
- Out of scope:
  - iOS UI coverage changes
  - screenshot testing
  - CI workflow changes
  - full navigation integration tests across graphs

## Constraints

- Tech/business constraints:
  - Keep Robolectric Compose tests as the Android UI test execution path for this slice.
  - Prefer `testTag`-based assertions over brittle full-string assertions where the UI structure allows it.
  - Do not break the existing shared auth/session flows while extracting testable UI content.
- Deadlines or milestones:
  - This is the immediate next testing slice after the main-shell implementation and Android startup hotfix.

## Definition of Done

- Functional result:
  - Android `MainScreen` and `AuthScreen` have executable UI coverage for happy path, error path, and at least one edge-state variant each.
  - Reusable Android UI test-state factories replace ad-hoc inline state assembly for these flows.
- Required tests:
  - `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/session-log.md`

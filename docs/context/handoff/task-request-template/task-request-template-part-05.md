# Task Request Template Part 05

## Latest Formalized Request (Android Root Navigation Coverage and CI Wiring)

## Context

- Related docs/decisions:
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `D-049`, `D-051`
- Current constraints:
  - Android UI coverage already exists for `AuthScreen` and `MainScreen`, but the root `auth/main` navigation behavior is still unverified.
  - The current repository CI only covers the server workflow and does not execute Android UI unit tests.
  - `task-request-template-part-04.md` exceeded the context-size threshold, so new requests must continue in a new split part according to `context-protocol.md`.

## Goal

- What should be delivered:
  - Complete Android UI testing iteration 2 by covering root auth/main navigation transitions with executable tests and wiring the Android UI unit-test command into GitHub Actions CI.

## Scope

- In scope:
  - extract or introduce a test-friendly Android root navigation container without changing shipped auth/session behavior
  - add executable Robolectric Compose UI tests for `unauthorized -> auth`, `authorized -> main`, and `logout/state reset -> auth`
  - add GitHub Actions CI coverage for `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`
  - synchronize context docs for the new mobile test/CI behavior
- Out of scope:
  - iOS CI wiring
  - screenshot testing
  - backend staging deploy/smoke validation
  - additional feature-domain UI work outside auth/main shell navigation

## Constraints

- Tech/business constraints:
  - Keep Robolectric Compose tests as the Android UI test execution path.
  - Do not couple navigation tests to reflective Android `ViewModel` creation or live backend calls.
  - Preserve the existing Telegram-first but provider-agnostic auth/session behavior.
- Deadlines or milestones:
  - This is the immediate next testing slice after Android UI coverage iteration 1.

## Definition of Done

- Functional result:
  - Android root navigation has executable coverage for the auth/main state transitions, and repository CI includes a runnable Android UI unit-test job.
- Required tests:
  - `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`
- Required docs updates:
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/handoff/task-request-template.md`

---

## Latest Formalized Request (Deferral of Deeper Android Integration Coverage)

## Context

- Related docs/decisions:
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/session-log.md`
  - `D-049`, `D-051`
- Current constraints:
  - Android UI coverage now includes auth screen, main screen, root auth/main navigation, and CI execution for `composeApp`.
  - A deeper Android integration layer around the real `App -> AuthScreen -> MainScreen` wiring is useful, but it is not the highest immediate repository risk.
  - The staging smoke validation for `auth/roles/workspaces` is still the older deferred execution step and should not be lost from context.

## Goal

- What should be delivered:
  - Record in project context that deeper Android integration coverage is intentionally postponed and that the next recommended execution step returns to staging smoke checks for `auth/roles/workspaces`.

## Scope

- In scope:
  - document the deferral of deeper Android integration coverage
  - document that the next recommended repository step is staging smoke validation for `auth/roles/workspaces`
  - keep the Android testing follow-up visible for a later return
- Out of scope:
  - new code changes in Android app or CI
  - staging deployment execution itself
  - changes to product scope or architecture decisions

## Constraints

- Tech/business constraints:
  - Do not silently drop the Android integration-testing follow-up; it must remain visible as a deferred engineering step.
  - Do not replace the already documented staging-smoke priority with more mobile test work.
- Deadlines or milestones:
  - This is a context-sync-only step requested immediately after Android UI testing iteration 2.

## Definition of Done

- Functional result:
  - Context docs clearly state that deeper Android integration coverage is deferred for later and that staging smoke for `auth/roles/workspaces` is the next recommended execution step.
- Required tests:
  - none
- Required docs updates:
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/session-log.md`

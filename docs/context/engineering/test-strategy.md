# Test Strategy

## Test Pyramid

- Unit tests: primary layer, fast and deterministic.
- Integration tests: repository/data/infrastructure behaviors with realistic boundaries.
- End-to-end/smoke tests: critical user flows on release branches.

## Mandatory Coverage by Feature

- Happy path.
- Error path.
- At least one edge case.
- Regression test for each fixed production bug.

## Integration and Contract Expectations

- Contract tests are required for the current auth API surface and for future payment/webhook integrations.
- Integration tests should cover concurrency and idempotency risks for ticketing, payments, and check-in once those domains are introduced.
- Backend persistence changes must include migration-path verification for both clean schema creation and upgrade of a legacy initialized schema.
- Smoke tests on release branches must validate the currently shipped critical flows.
- Mobile UI changes in active product flows must have executable platform UI coverage where in-repo infrastructure exists.

## MVI-Specific Expectations

- ViewModel tests must assert:
  - state transitions,
  - effect emission,
  - deterministic handling of intents.

## CI Expectations

- Test suite runs on pull request.
- Merge is blocked on failing required tests.
- Flaky tests must be fixed or quarantined with explicit follow-up.
- Android repository changes that affect `composeApp`/shared mobile flows must execute `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin` in GitHub Actions CI.

## Current Mobile UI Coverage (2026-03-13)

- Android root auth/main navigation is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/navigation/AppNavHostContentTest.kt`, including `unauthorized -> auth`, `authorized -> main`, and `state reset/logout -> auth`.
- Android post-auth main shell is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/main/ui/MainScreenContentTest.kt`, including happy path, empty state, loading indicator, disabled actions, fallback-profile rendering, and dismissible error state.
- Android auth entry UI is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/auth/ui/AuthScreenContentTest.kt`, including provider dispatch, loading lock, error rendering, and authorized state.
- Android UI tests use stable `testTag` hooks plus shared Android UI state factories to avoid brittle ad-hoc state assembly.
- GitHub Actions mobile CI coverage is implemented through `.github/workflows/ci-android.yml` for Android UI unit tests and `composeApp` compilation.
- Более глубокое Android integration coverage на реальной связке `App -> AuthScreen -> MainScreen` осознанно отложено и должно быть возобновлено после staging smoke-проверок для `auth/roles/workspaces`.
- iOS post-auth main shell is covered by the real `iosAppUITests` XCUITest target using the `--ui-test-main` launch fixture.

## High-Risk Scenario Set

- Double-booking race on seat/inventory transitions.
- Duplicate webhook delivery.
- Expired seat hold release.
- Refund after check-in.
- Role escalation / unauthorized finance access.
- Live lineup reorder conflict.

## Ownership

- Feature owner is responsible for keeping tests updated with behavior changes.

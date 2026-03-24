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
- Integration tests should cover concurrency and idempotency risks for ticketing, payments, and check-in; the current ticketing foundation already requires coverage for double-reserve conflicts and hold expiry/release recovery.
- Backend persistence changes must include migration-path verification for both clean schema creation and upgrade of a legacy initialized schema.
- Venue/hall-template backend changes must cover route behavior, layout contract validation, and migration-path verification for the organizer venue slice.
- Event/EventHallSnapshot backend changes must cover route behavior, lifecycle transition validation, detail/update override validation, snapshot freeze invariants, and migration-path verification for the organizer event slice.
- Comedian applications backend changes must cover comedian submit happy path, organizer review/list authorization, status transition validation, request-id-correlated diagnostics, and migration-path verification for the applications slice.
- Lineup backend changes must cover approved-application materialization into draft lineup entries, organizer/host reorder authorization, explicit contiguous `order_index` validation, live-stage mutation authorization/transition validation (`up_next` / `on_stage` uniqueness plus terminal status guards), request-id-correlated diagnostics, and migration-path verification for the lineup slice when schema changes are introduced.
- Public event discovery backend changes must cover audience-safe summary shaping, `city/date/price` filtering, and request-id-correlated diagnostics for the anonymous route.
- Ticketing inventory/hold/order/checkout backend changes must cover derivation from `EventHallSnapshot` plus event-local overrides, hold conflict invariants, checkout-order creation from active hold-ов, authenticated order-status reads, paid-order ticket issuance idempotency, authenticated `GET /api/v1/me/tickets`, checker-only `POST /api/v1/checkin/scan` with duplicate-scan handling, PSP handoff session creation with idempotent reuse, webhook-driven payment confirmation/cancellation with provider-status recheck plus source validation, duplicate-webhook idempotency, expiry/release semantics for both hold-ов and pending order-ов, stale-sync avoidance on unchanged reads, and migration-path verification for the ticketing foundation slice.
- Smoke tests on release branches must validate the currently shipped critical flows.
- Mobile UI changes in active product flows must have executable platform UI coverage where in-repo infrastructure exists.
- Auth/session boundary refactors must execute `:domain:auth:allTests`, `:domain:session:allTests`, `:core:backend:allTests`, `:data:auth:allTests`, `:data:session:allTests`, `:shared:allTests`, `:composeApp:testDebugUnitTest`, and `:composeApp:compileDebugKotlin` before completion.

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

## Current Mobile UI Coverage (2026-03-23)

- Android root auth/main navigation is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/navigation/AppNavHostContentTest.kt`, including `unauthorized -> auth`, `authorized -> main`, and `state reset/logout -> auth`.
- Android post-auth main shell is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/main/ui/MainScreenContentTest.kt`, including happy path, empty state, loading indicator, disabled actions, fallback-profile rendering, dismissible error state, workspace invitation inbox actions, workspace invite form, and membership permission-role updates.
- Android organizer venue management tab is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/venue/ui/VenueManagementTabContentTest.kt` plus bottom-tab reachability coverage in `composeApp/src/test/kotlin/com/bam/incomedy/feature/main/ui/MainScreenContentTest.kt`.
- Android organizer event management tab is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/event/ui/EventManagementTabContentTest.kt` plus bottom-tab reachability coverage in `composeApp/src/test/kotlin/com/bam/incomedy/feature/main/ui/MainScreenContentTest.kt`, including create-form wiring, publish/open/pause/cancel actions, and text-based override-editor save flow.
- Android lineup management tab is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/lineup/ui/LineupManagementTabContentTest.kt` plus bottom-tab reachability coverage in `composeApp/src/test/kotlin/com/bam/incomedy/feature/main/ui/MainScreenContentTest.kt`, including organizer context load, comedian submit form, review actions, and lineup reorder controls over stable shared fixtures.
- Android audience/staff ticketing tab is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/main/ui/MainScreenContentTest.kt`, including ticket-tab reachability and checker scan callback wiring over stable bindings, while shared ticketing state transitions are covered by `feature/ticketing/src/commonTest/kotlin/com/bam/incomedy/feature/ticketing/TicketingViewModelTest.kt`.
- Backend public event discovery route is covered by `server/src/test/kotlin/com/bam/incomedy/server/events/EventRoutesTest.kt`, including anonymous listing, deterministic `city/date/price` filtering, and request-id-correlated diagnostics for the public catalog route.
- Backend comedian applications and lineup routes are covered by `server/src/test/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsRoutesTest.kt`, including comedian submit, organizer review/list flow, organizer lineup reorder, organizer live-stage mutation, terminal-status guards, uniqueness validation for `on_stage`, and request-id-correlated diagnostics for the lineup slice.
- Android auth entry UI is covered by Robolectric Compose UI tests in `composeApp/src/test/kotlin/com/bam/incomedy/feature/auth/ui/AuthScreenContentTest.kt`, including provider dispatch, loading lock, error rendering, and authorized state.
- Android UI tests use stable `testTag` hooks plus shared Android UI state factories to avoid brittle ad-hoc state assembly.
- GitHub Actions mobile CI coverage is implemented through `.github/workflows/ci-android.yml` for Android UI unit tests and `composeApp` compilation.
- Более глубокое Android integration coverage на реальной связке `App -> AuthScreen -> MainScreen` осознанно отложено и должно быть возобновлено после staging smoke-проверок для `auth/roles/workspaces`.
- iOS post-auth main shell is covered by the real `iosAppUITests` XCUITest target using the `--ui-test-main` launch fixture, including workspace invitation inbox rendering plus permission-role action coverage on the home tab.
- iOS organizer venue tab is covered by `iosApp/iosAppUITests/iosAppUITests.swift` through `testVenueTabShowsVenueManagementSurface`, which smoke-checks tab reachability plus stable form/builder controls over the shared venue fixture.
- iOS organizer event tab is covered by `iosApp/iosAppUITests/iosAppUITests.swift` through `testEventTabShowsEventManagementSurface`, which smoke-checks tab reachability plus stable workspace/venue/template selectors, create controls, publish/open/pause/cancel actions, and override-editor surface over the shared event fixture.
- iOS lineup tab is covered by `iosApp/iosAppUITests/iosAppUITests.swift` through `testLineupTabShowsApplicationsAndReorderSurface`, which smoke-checks tab reachability, organizer event selection, submit/review controls, and reorder affordances over the shared lineup fixture; the targeted local XCUITest path is now green on `iPhone 17 Pro (iOS 26.2)`.
- iOS audience/staff ticketing tab is covered by `iosApp/iosAppUITests/iosAppUITests.swift` through `testTicketTabShowsWalletAndCheckInSurface`, which smoke-checks ticket wallet rendering, QR expansion, and successful staff QR scan over the shared main fixture.
- Venue foundation automation now also includes `:feature:venue:allTests`, `:server:test --tests 'com.bam.incomedy.server.venues.VenueRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'`, `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`, and the targeted venue XCUITest run for the new iOS tab.
- Event lifecycle/override automation now also includes `:domain:event:jvmTest`, `:feature:event:allTests`, `:composeApp:testDebugUnitTest`, `:composeApp:compileDebugKotlin`, `:server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.events.EventRoutesTest'`, `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`, and the targeted event XCUITest run for the organizer event tab.
- Ticketing foundation automation now also includes `:domain:ticketing:allTests`, `:feature:ticketing:allTests`, `:data:ticketing:compileKotlinMetadata`, `:shared:compileKotlinMetadata`, `:composeApp:compileDebugKotlin`, `:composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest'`, `:server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.ticketing.TicketingRoutesTest' --tests 'com.bam.incomedy.server.payments.yookassa.YooKassaCheckoutGatewayTest'`, `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`, and the targeted ticketing XCUITest run for the iOS tab.
- `TicketingRoutesTest` now also covers anonymous public inventory access, request-id-correlated diagnostics for the public route, sanitization of active hold metadata in anonymous inventory responses, successful checkout-order creation, rejection of чужих/неактивных hold-ов, inventory recovery after pending order expiration, successful YooKassa checkout-session start, idempotent reuse of an existing session, authenticated order-status reads, disabled-provider `503`, expired-order rejection, foreign-order denial, successful `payment.succeeded` webhook application with issued-ticket creation, authenticated issued-ticket listing with QR payload delivery, checker-only check-in, duplicate scan semantics, `payment.canceled` release semantics, and source-IP rejection for the public payments webhook.
- `YooKassaCheckoutGatewayTest` validates server-side request shaping for YooKassa (`amount`, `Idempotence-Key`, `return_url`, safe metadata), payment snapshot lookup (`amount`, `metadata.order_id`, `metadata.event_id`), and provider-response validation for missing `confirmation_url`.
- Comedian applications + lineup backend foundation automation now also includes `:server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`.
- Live-stage backend foundation for lineup now also relies on `:server:test --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`, which covers the new `POST /api/v1/events/{eventId}/lineup/live-state` path alongside existing submit/review/reorder coverage.
- Shared comedian applications + lineup foundation automation now also includes `:feature:lineup:allTests` and `:data:lineup:compileKotlinMetadata :shared:compileKotlinMetadata`.
- Comedian applications + lineup platform UI automation now also includes `:composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' --tests 'com.bam.incomedy.feature.lineup.ui.LineupManagementTabContentTest'`, `:composeApp:compileDebugKotlin`, and the targeted iOS lineup XCUITest path `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test CODE_SIGNING_ALLOWED=NO`, which is now green after the repo-side Xcode/KMP bridge hardening and simulator execution fixes.

## High-Risk Scenario Set

- Double-booking race on seat/inventory transitions.
- Duplicate webhook delivery.
- Expired seat hold release.
- Abandoned checkout order expiration.
- Duplicate PSP session creation for one order.
- Refund after check-in.
- Role escalation / unauthorized finance access.
- Live lineup reorder conflict.

## Ownership

- Feature owner is responsible for keeping tests updated with behavior changes.

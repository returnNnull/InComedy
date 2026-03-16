# Task Request Template Part 21

## Formalized Planning Decision (Venue/Hall Delivery Slicing)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/standup-platform-ru/04-функциональные-требования.md`
  - `docs/standup-platform-ru/05-архитектура-системы.md`
  - `docs/standup-platform-ru/06-доменная-модель-и-данные.md`
  - `docs/standup-platform-ru/08-api-и-событийная-модель.md`
  - `docs/standup-platform-ru/10-дорожная-карта-и-план-релиза.md`
  - `D-046`
  - `D-047`
- Current constraints:
  - The repository currently has no `venues` bounded context, no hall-template API, and no organizer UI for this domain.
  - The documented scope spans venue catalog, hall-template editing, event linkage, and future ticketing consistency via snapshots.
  - `hall builder v1` must stay strictly inside the MVP 2D scope and should not absorb adjacent event/ticketing complexity by accident.

## Goal

- What should be delivered:
  - decide whether `Venue management and hall template builder v1` can be implemented safely as one coding slice or should be split into bounded deliveries
  - identify the recommended split that preserves momentum and architectural safety

## Scope

- In scope:
  - planning the delivery shape
  - recommending bounded implementation slices
- Out of scope:
  - changing the product backlog order
  - redefining MVP scope

## Constraints

- Tech/business constraints:
  - slices should align with `venues -> events -> ticketing` domain boundaries
  - every slice must remain independently testable and documentable
  - event-specific inventory overrides and sales behavior should not leak into the initial venue/template slice unless explicitly required

## Definition of Done

- Functional result:
  - the recommended delivery approach is explicit: split the work into bounded slices rather than attempt the full documented venue/hall surface in one implementation change
  - the first implementation slice is identified as `Venue CRUD + HallTemplate CRUD/clone + canonical layout schema + basic organizer management UI`, with event snapshot wiring kept as the next slice

## Formalized Implementation Request (Venues/Templates Foundation)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/standup-platform-ru/03-роли-и-сценарии.md`
  - `docs/standup-platform-ru/04-функциональные-требования.md`
  - `docs/standup-platform-ru/05-архитектура-системы.md`
  - `docs/standup-platform-ru/06-доменная-модель-и-данные.md`
  - `docs/standup-platform-ru/08-api-и-событийная-модель.md`
  - `D-046`
  - `D-047`
- Current constraints:
  - The repository has no implemented `venues` bounded context yet.
  - The user explicitly asked to preserve Clean architecture, add Russian comments, use structured diagnostics for production-significant backend flows, and prefer new modules over overloading existing layers where that improves context isolation.
  - The current delivery should stay within the first bounded slice rather than absorb event snapshot binding or ticketing overrides.

## Goal

- What should be delivered:
  - backend and client foundation for organizer venue management and hall templates
  - separate `domain/data/feature` modules for the new bounded context where useful
  - basic organizer UI surface in Android/iOS main shell without overloading the existing session bounded context
  - synchronized docs, API contract, tests, and governance memory

## Scope

- In scope:
  - `Venue CRUD`
  - `HallTemplate create/update/clone`
  - canonical typed hall-layout schema for builder v1
  - backend migration, repository, service, routes, diagnostics, and tests
  - shared client service/viewmodel plus Android/iOS basic organizer management UI
- Out of scope:
  - event creation and venue/template binding
  - `EventHallSnapshot` generation
  - event-specific inventory overrides
  - ticketing/check-in behavior

## Constraints

- Tech/business constraints:
  - builder v1 stays within documented 2D scope and must not become a generic CAD editor
  - inventory state transitions remain outside this slice and stay reserved for `ticketing`
  - touched and new code must include Russian comments
  - backend route diagnostics must use the sanitized diagnostics system rather than ad-hoc logging alone

## Definition of Done

- Functional result:
  - organizers can create/list venues and create/edit/clone hall templates from the app shell
  - the new bounded context is isolated through dedicated contracts/modules instead of being merged into session/auth layers
  - docs and OpenAPI reflect the new venue/template foundation
- Required tests:
  - backend route/repository/migration coverage for the new surface
  - shared/domain/data tests for venue logic
  - Android UI coverage for the new organizer venue surface

## Implementation Outcome (Venues/Templates Foundation)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/governance/decision-traceability/decision-traceability-part-03.md`
  - `D-047`
- Delivered boundary:
  - venue catalog + reusable hall template foundation only
  - no event binding, snapshot freezing, or ticket inventory transitions in this change

## Result

- Delivered:
  - dedicated `domain/venue`, `data/venue`, and `feature/venue` client modules
  - backend migration/repository/service/routes for organizer venue management
  - sanitized diagnostics coverage for production-significant backend venue mutations
  - Android Compose and iOS SwiftUI organizer tab surfaces for venue create/list and hall-template create/update/clone
  - synchronized context docs and API contract for the new organizer venue surface

## Verification

- Automated checks executed:
  - `./gradlew :feature:venue:allTests`
  - `./gradlew :server:test --tests 'com.bam.incomedy.server.venues.VenueRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'`
  - `./gradlew :composeApp:compileDebugKotlin :shared:allTests`
  - `./gradlew :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.venue.ui.VenueManagementTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest'`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16e,OS=26.2' -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 -only-testing:iosAppUITests/iosAppUITests/testVenueTabShowsVenueManagementSurface test`

## Follow-Up

- Next bounded slice:
  - bind events to venues/templates through `EventHallSnapshot`
  - keep event-specific pricing/availability overrides outside the current venue foundation until that slice starts

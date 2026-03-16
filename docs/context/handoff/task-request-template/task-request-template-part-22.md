# Task Request Template Part 22

## Formalized Implementation Request (Events/EventHallSnapshot Foundation)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/standup-platform-ru/04-функциональные-требования.md`
  - `docs/standup-platform-ru/05-архитектура-системы.md`
  - `docs/standup-platform-ru/06-доменная-модель-и-данные.md`
  - `docs/standup-platform-ru/08-api-и-событийная-модель.md`
  - `D-047`
- Current constraints:
  - `venues/templates foundation` is already implemented as a separate bounded context.
  - The next documented dependency is `events`, specifically binding organizer events to reusable venue templates through `EventHallSnapshot`.
  - The user explicitly asked to continue with the next planned item while preserving Clean architecture, Russian comments, structured diagnostics, and bounded new modules.

## Goal

- What should be delivered:
  - first bounded `events` slice that binds organizer events to existing venues and hall templates
  - frozen `EventHallSnapshot` creation as part of the event flow
  - dedicated `domain/data/feature` event modules instead of overloading existing session or venue modules
  - synchronized docs, API contract, tests, and governance memory

## Scope

- In scope:
  - organizer event create/list/publish foundation
  - event selection of `workspace -> venue -> hall template`
  - `EventHallSnapshot` persistence using the canonical hall layout schema
  - backend migration, repository, service, routes, diagnostics, and tests
  - shared/client event feature and basic Android/iOS organizer UI surface
- Out of scope:
  - sales open/pause flows
  - event-specific inventory overrides
  - ticket inventory transitions, holds, checkout, and check-in
  - lineup/applications/live-stage behavior

## Constraints

- Tech/business constraints:
  - event binding must remain in `events`, while venues/templates stay the upstream reusable source
  - snapshot logic must preserve the documented invariant that future template edits do not break existing event seating state
  - touched and new code must include Russian comments
  - backend event mutations must use the sanitized diagnostics system rather than ad-hoc logging

## Definition of Done

- Functional result:
  - organizer can create an event bound to an existing venue/template, list events, and publish an event with a frozen `EventHallSnapshot`
  - the new event bounded context is isolated through dedicated contracts/modules
  - docs and OpenAPI reflect the new event/snapshot foundation
- Required tests:
  - backend route/repository/migration coverage for the new event surface
  - shared/domain/data tests for event logic and snapshot behavior
  - Android/iOS UI coverage for the new organizer event surface

## Implementation Outcome

- Delivered:
  - dedicated `domain/data/feature` event modules plus shared/iOS bridge surface
  - backend `events` repository/service/routes path with sanitized diagnostics and `V6__event_management_foundation.sql`
  - organizer Android/iOS event tab with `workspace -> venue -> hall template` selection, draft creation, list, and publish action
  - OpenAPI, architecture, traceability, and test-strategy sync for the new slice
- Remaining follow-up:
  - event-specific price/availability overrides
  - event update/cancel/sales-open flows
  - ticket inventory transitions and checkout/check-in integration

## Verification

- Executed:
  - `./gradlew :server:test --tests com.bam.incomedy.server.db.DatabaseMigrationRunnerTest --tests com.bam.incomedy.server.events.EventRoutesTest`
  - `./gradlew :domain:event:jvmTest`
  - `./gradlew :composeApp:testDebugUnitTest`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16e,OS=26.2' -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 -only-testing:iosAppUITests/iosAppUITests/testEventTabShowsEventManagementSurface test`

## Formalized Implementation Request (Event Price/Availability Overrides)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/standup-platform-ru/04-функциональные-требования.md`
  - `docs/standup-platform-ru/05-архитектура-системы.md`
  - `docs/standup-platform-ru/06-доменная-модель-и-данные.md`
  - `docs/standup-platform-ru/08-api-и-событийная-модель.md`
  - `D-047`
- Current constraints:
  - `events/EventHallSnapshot foundation` is already implemented and frozen snapshots exist independently from mutable hall templates.
  - The next documented gap is organizer-local configuration of prices and availability for a concrete event, while ticket inventory transitions still belong to the future `ticketing` context.
  - The user explicitly asked to proceed with implementation, while keeping Clean architecture, Russian comments, structured diagnostics, and bounded new modules.

## Goal

- What should be delivered:
  - event details read/update surface for organizer events
  - event-local price zones and mapping of snapshot elements to those price zones
  - event-local availability overrides for seats/rows/zones/tables without mutating venue templates
  - synchronized docs, API contract, tests, and governance memory

## Scope

- In scope:
  - `GET /api/v1/events/{id}` and `PATCH /api/v1/events/{id}` foundation
  - event-local `PriceZone` persistence with sales window fields
  - event-local pricing assignments for `seat`, `row`, `zone`, and `table`
  - event-local availability overrides for the same target types
  - backend migration, repository, service, routes, diagnostics, and tests
  - shared/client event models plus basic Android/iOS organizer editor surface
- Out of scope:
  - `InventoryUnit`, `SeatHold`, and sold/held/released transitions
  - checkout, PSP integration, QR issuance, and check-in
  - dynamic pricing, waitlist, and audience discovery/catalog
  - lineup/staff/live-stage behavior

## Constraints

- Tech/business constraints:
  - override logic must stay in `events` and must not mutate source venue templates
  - frozen `EventHallSnapshot` remains the immutable base; overrides are stored separately and applied on top
  - seat inventory must still change only through `ticketing`
  - touched and new code must include Russian comments
  - backend mutations must use the sanitized diagnostics system rather than ad-hoc logging

## Definition of Done

- Functional result:
  - organizer can open event details, edit base event fields, manage event-local price zones, assign them to snapshot elements, and block/unblock event-local availability targets
  - event override storage is isolated from venue templates and future ticketing inventory
  - docs and OpenAPI reflect the new event override foundation
- Required tests:
  - backend route/repository/migration coverage for the new detail/update surface
  - shared/domain/data tests for override validation and state handling
  - Android/iOS UI coverage for event details and override editing

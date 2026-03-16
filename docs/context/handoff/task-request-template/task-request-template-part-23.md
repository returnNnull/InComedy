# Task Request Template Part 23

## Formalized Context Sync Request (New Chat Baseline Confirmation)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/product/non-functional-requirements.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/product/backlog.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-058`
  - `D-059`
  - `D-060`
  - `D-061`
- Current constraints:
  - `docs/context/*` remains the repository source of truth and must be synchronized before any new implementation work.
  - Governance records are split into index files plus rolling part files, so sync must follow the latest indexed part for decisions, session memory, and traceability.
  - The carried-over implementation stream is still the organizer `events` P0 slice, with the latest recorded bounded next step focused on event-local price and availability overrides.

## Goal

- What should be delivered:
  - complete the ordered new-chat context synchronization
  - confirm the latest decision id from the governance register
  - confirm the current `P0` priority baseline relevant to the active implementation stream
  - confirm the latest `Next` step from governance session memory
  - confirm the current execution status of the key active decisions from decision traceability

## Scope

- In scope:
  - read the required context files in the prescribed order
  - follow the split indexes into the latest `decisions-log`, `session-log`, and `decision-traceability` parts
  - summarize the confirmed baseline for the next task
  - record the sync in governance memory
- Out of scope:
  - implementation work
  - reprioritizing backlog items
  - accepting new product or architecture decisions without a separate task

## Constraints

- Tech/business constraints:
  - if newly introduced information conflicts with `docs/context/*`, the docs must be updated before code in a later task
  - governance notes must stay concise, analytical, and sanitized
  - no secrets, raw diagnostics tokens, or transcript-style dumps may be stored in governance artifacts

## Definition of Done

- Functional result:
  - the ordered sync is completed before implementation continues
  - the response explicitly confirms the current `D-*` head, current `P0`, latest session `Next`, and key decision statuses
  - governance memory reflects that this chat started with a context-sync-only step
- Required tests:
  - none; documentation/governance synchronization only

## Implementation Outcome (Event Price/Availability Overrides)

- Delivered:
  - organizer event details now expose `GET /api/v1/events/{id}` and `PATCH /api/v1/events/{id}` with frozen snapshot data plus event-local `price_zones`, `pricing_assignments`, and `availability_overrides`
  - backend override persistence landed through `V7__event_override_foundation.sql`, repository/service/route updates, and sanitized diagnostics for the new `get/update` surface
  - shared/domain/data/bridge/mobile layers now support text-based organizer editing of event-local pricing and availability over frozen snapshots on Android and iOS
  - source-of-truth docs were synchronized across architecture, OpenAPI, test strategy, traceability, and governance memory
- Remaining follow-up:
  - event cancel and sales open/pause transitions
  - ticket inventory units, holds, checkout, and check-in
  - richer organizer editing beyond the current text-based override surface

## Verification

- Executed:
  - `./gradlew :domain:event:jvmTest`
  - `./gradlew :feature:event:allTests`
  - `./gradlew :server:test --tests com.bam.incomedy.server.db.DatabaseMigrationRunnerTest --tests com.bam.incomedy.server.events.EventRoutesTest`
  - `./gradlew :composeApp:testDebugUnitTest`
  - `./gradlew :composeApp:compileDebugKotlin`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16e,OS=26.2' -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 -only-testing:iosAppUITests/iosAppUITests/testEventTabShowsEventManagementSurface test`

## Formalized Implementation Request (Event Sales-State And Cancel Controls)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/standup-platform-ru/04-функциональные-требования.md`
  - `docs/standup-platform-ru/06-доменная-модель-и-данные.md`
  - `D-047`
- Current constraints:
  - `event price/availability overrides foundation` is now delivered and remains the last completed organizer event slice.
  - The next documented gap inside the active `P0` event stream is organizer control over `sales lifecycle states`, while `InventoryUnit`, holds, checkout, and check-in still belong to the future `ticketing` context.
  - The repository already stores `status` and `sales_status` separately, so the next bounded step should extend those controls rather than pulling ticketing semantics forward.

## Goal

- What should be delivered:
  - organizer controls for `sales open`, `sales pause`, and event `cancel`
  - backend, shared, Android, and iOS support for these bounded actions
  - synchronized docs, API contract, tests, and governance memory

## Scope

- In scope:
  - backend routes/services/repository support for organizer sales-state mutations and cancel
  - transition validation over current `status + sales_status` model
  - shared/domain/data contracts and mobile UI actions for the new controls
  - route/unit/UI coverage and docs sync
- Out of scope:
  - `InventoryUnit`, `SeatHold`, and checkout/check-in
  - automatic `sold_out` detection from inventory exhaustion
  - `in_progress`, `completed`, and `archived` lifecycle handling
  - refunds and released-seat return-to-sale behavior

## Constraints

- Tech/business constraints:
  - the slice must stay inside `events` and must not introduce ticketing inventory semantics
  - cancel/sales transitions must be validated centrally in shared backend service logic
  - touched and new code must include Russian comments
  - backend mutations must continue to use sanitized diagnostics rather than ad-hoc logging

## Definition of Done

- Functional result:
  - organizer can open sales for a published event, pause sales for an on-sale event, and cancel an event through the bounded organizer surface
  - the new transitions are enforced consistently across backend and mobile clients
  - docs and OpenAPI reflect the new sales-state/cancel surface
- Required tests:
  - backend route/service/repository coverage for valid and invalid transitions
  - shared ViewModel/domain tests for new mutation commands
  - Android/iOS UI coverage for the new organizer controls

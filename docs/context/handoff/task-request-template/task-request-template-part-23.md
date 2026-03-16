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

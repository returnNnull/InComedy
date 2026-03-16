# Task Request Template Part 24

## Implementation Outcome (Event Sales-State And Cancel Controls)

## Delivered

- Organizer event lifecycle now includes bounded manual actions for `sales open`, `sales pause`, and `cancel` on top of the existing `create/list/get/update/publish` surface.
- Backend `events` routes, service validation, and persistence now enforce organizer-side transition rules over `status + sales_status` without introducing `InventoryUnit`, holds, or checkout semantics.
- Shared/domain/data/ViewModel contracts, Android Compose UI, and iOS SwiftUI UI now expose lifecycle controls for published/on-sale events while keeping canceled events read-only.
- Source-of-truth docs were synchronized across OpenAPI, architecture overview, test strategy, decision traceability, and governance session memory.

## Constraints Preserved

- The slice stays inside the `events` bounded context and does not pull ticket inventory behavior forward.
- Cancel closes sales at organizer lifecycle level but does not attempt refund, release-to-sale, or `sold_out` automation logic.
- Existing frozen `EventHallSnapshot` and text-based event-local override editing remain the current organizer editing foundation.

## Verification

- Executed:
  - `./gradlew :domain:event:jvmTest`
  - `./gradlew :feature:event:allTests :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin :server:test --tests com.bam.incomedy.server.db.DatabaseMigrationRunnerTest --tests com.bam.incomedy.server.events.EventRoutesTest`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16e,OS=26.2' -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 -only-testing:iosAppUITests/iosAppUITests/testEventTabShowsEventManagementSurface test`

## Remaining Follow-Up

- `InventoryUnit + SeatHold` ticketing foundation
- automatic `sold_out` transitions from inventory exhaustion
- checkout/check-in, refunds, and released-seat return-to-sale behavior

## Formalized Implementation Request (InventoryUnit + SeatHold Foundation)

## Why This Step

- The active `P0` event/ticketing stream now has organizer event lifecycle controls through `publish/open sales/pause sales/cancel`, but still lacks protected sellable inventory transitions.
- Product and architecture docs already reserve `InventoryUnit` and `SeatHold` as the next bounded foundation needed before checkout, QR issuance, refunds, and check-in.
- The repository still has no dedicated ticketing bounded context, so this slice should establish the minimal backend/shared foundation without stretching into PSP or fulfillment concerns.

## Scope

- Add ticketing inventory read semantics for a published event based on the frozen `EventHallSnapshot` plus event-local availability and pricing overrides.
- Introduce persisted `InventoryUnit` records for snapshot targets that can be sold or temporarily reserved.
- Introduce persisted `SeatHold` records with bounded TTL, explicit release, and deterministic expiry handling when reading or mutating holds.
- Expose only the minimal protected backend surface needed for:
  - inventory listing
  - hold creation
  - hold release
- Add shared/domain/data contracts only where required to keep the client integration path ready for later checkout work.

## Explicitly Out Of Scope

- payment, PSP checkout, order capture, QR issuance, check-in, refunds, or waitlist logic
- organizer inventory editor UI, public event catalog, or unauthenticated ticket purchase flow
- automatic `sold_out` event transition orchestration beyond the persisted inventory state foundation
- multi-seat order basket semantics beyond one hold request against one inventory target at a time

## Constraints

- `docs/context/*` remains the source of truth and must stay aligned with implementation.
- Frozen `EventHallSnapshot` remains immutable; ticketing state must layer on top of the snapshot instead of mutating venue or event layout data.
- Backend production-significant flows must emit sanitized structured diagnostics with `requestId`, `stage`, and bounded metadata.
- New and materially changed code must include Russian responsibility/flow comments at class, method, and significant property level.

## Acceptance Signals

- Backend can derive or persist event inventory for sellable snapshot targets while mapping targets disabled by event-local availability overrides into unavailable inventory state.
- Authenticated caller can list event inventory, create a hold on an available inventory unit, and release an active hold.
- Expired holds are treated as inactive and no longer block the inventory unit without needing raw container intervention.
- Repository, route, and migration coverage protect the invariants around one active hold per inventory unit and no double reservation of the same target.
- Source-of-truth docs, OpenAPI, traceability, and governance logs are synchronized with the delivered slice.

## Formalized Documentation Request (HTML Project Reference Site)

## Why This Step

- The repository already contains rich code and compact context docs, but onboarding still requires opening many files and mentally stitching together module boundaries, runtime routes, and implementation status.
- The current codebase now spans Android, iOS, KMP shared layers, Ktor backend, Flyway migrations, CI/CD, and partial ticketing foundation work, so a newcomer-friendly reference site is now more valuable than another markdown-only overview.
- The user explicitly requested a detailed, modern, understandable HTML documentation package inside `docs/`, with links across modules/features, diagrams, and code references.

## Scope

- Create a static HTML documentation site under `docs/` that explains:
  - project overview and onboarding path,
  - architecture and runtime topology,
  - module catalog and dependency direction,
  - current feature/bounded-context map,
  - backend/API/schema/deploy surface,
  - quality, tests, and context-process rules.
- Use the real repository state as the primary input, including:
  - Gradle module graph,
  - Android/iOS entry points,
  - shared DI and bridges,
  - backend route registration,
  - Flyway migrations,
  - CI/CD assets,
  - existing `docs/context/*` memory.
- Include diagrams, code snippets, and direct links to relevant source files.

## Explicitly Out Of Scope

- changing product scope or implementation behavior
- replacing `docs/context/*` or `docs/standup-platform-ru/*` as the source of truth
- generating API docs from an external toolchain or introducing a docs build system

## Constraints

- Documentation must reflect the actual checked-out repository state, including partially integrated ticketing work and legacy auth leftovers that are still present in source files.
- The new site must stay readable for a newcomer and clearly separate:
  - implemented runtime surface,
  - partially wired foundation,
  - legacy or dormant code paths.
- Existing user changes in the worktree must not be reverted or overwritten.

## Acceptance Signals

- There is a browsable HTML site in `docs/` with multiple linked pages and shared styling.
- The site explains module responsibilities, feature boundaries, backend routes, migrations, deployment, and tests with direct source-code links.
- The site explicitly documents the current ticketing state as partial foundation rather than fully shipped runtime functionality.
- Governance memory is updated for this documentation task.

## Implementation Outcome (InventoryUnit + SeatHold Foundation)

## Delivered

- Added the first runtime `ticketing` foundation across `:domain:ticketing`, `:data:ticketing`, shared dependency wiring, and backend `ticketing` routes/repository/service.
- Backend now derives sellable inventory from frozen `EventHallSnapshot` plus event-local pricing/availability overrides, persists that derived state in `V8__ticketing_inventory_foundation.sql`, and exposes protected inventory list plus hold create/release routes.
- `SeatHold` semantics now include bounded TTL, automatic expiry on read/mutate, explicit release, and conflict protection against double reservation of the same inventory unit.
- Source-of-truth docs were synchronized across OpenAPI, architecture overview, test strategy, decision traceability, and governance memory.

## Constraints Preserved

- The slice stays bounded to inventory/hold foundation and does not introduce checkout, PSP, QR issuance, refunds, public catalog, or check-in behavior.
- Frozen `EventHallSnapshot` remains immutable; ticketing state layers on top through derived inventory persistence instead of mutating event or venue layout data.
- Current mobile apps receive only domain/data readiness through shared module graph updates; no organizer or audience UI surface was stretched into this infrastructure slice.

## Verification

- Executed:
  - `./gradlew :domain:ticketing:allTests`
  - `./gradlew :data:ticketing:compileKotlinMetadata`
  - `./gradlew :shared:compileKotlinMetadata :composeApp:compileDebugKotlin`
  - `./gradlew :server:test --tests com.bam.incomedy.server.db.DatabaseMigrationRunnerTest --tests com.bam.incomedy.server.ticketing.TicketingRoutesTest`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`

## Remaining Follow-Up

- public event catalog and audience purchase entry surface
- checkout/order capture, QR issuance, and check-in
- automatic `sold_out` transitions and inventory exhaustion orchestration

## Documentation Extension Request (Feature Execution Logic)

## Why This Step

- The HTML docs site already explains modules, architecture, features, and backend surface, but it still left a gap for a newcomer who needs to trace real execution flow end-to-end.
- The user explicitly requested a clearer description of feature logic: what is called first, which methods are responsible for which transition, why the call exists, and where data is passed next.
- Since the working tree now includes active ticketing route registration, the docs also need a factual correction so ticketing is no longer described as server-runtime absent.

## Scope

- Add a dedicated HTML page that documents real call chains for the implemented feature slices:
  - credentials auth,
  - VK auth,
  - session bootstrap and organizer context load,
  - role/workspace mutations,
  - venue and hall template mutations,
  - event creation/update/lifecycle controls,
  - ticketing inventory and seat-hold foundation.
- Update existing docs-site navigation so the new page is reachable from every major page.
- Correct existing ticketing statements across the docs-site to reflect the current code state:
  - server runtime routes are registered,
  - client/UI feature surface is still missing.

## Constraints

- Method chains must be derived from the checked-out code, not inferred from prior docs.
- The explanation must stay understandable for a newcomer while preserving exact class/method names and route paths.
- The docs must clearly separate:
  - platform wrappers,
  - shared ViewModel logic,
  - data adapters,
  - server routes,
  - server business services,
  - missing client-side ticketing product surface.

## Acceptance Signals

- The docs-site contains a dedicated feature-logic page with step-by-step execution chains and source links.
- Existing pages link to the new page through shared navigation and/or jump links.
- Ticketing is described as active server-side inventory/hold foundation, not as entirely absent from runtime.

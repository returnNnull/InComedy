# Task Request Template Part 29

## Formalized Implementation Request (Shared/Mobile Ticket Wallet + Checker Scan Surfaces)

## Why This Step

- `D-066` had already redirected the active ticketing path toward provider-agnostic order/ticket/check-in delivery, and the backend/shared contracts for issued tickets plus QR/check-in were already in place.
- Without buyer/staff mobile surfaces, the MVP ticketing path was still incomplete for real user flows even though the server foundation already existed.
- This increment still needed to stay provider-agnostic and avoid implying any external PSP choice.

## Scope

- Add shared ticketing presentation state for ticket loading, error/result handling, and checker scan commands.
- Wire Android Compose and iOS SwiftUI `Билеты` tab surfaces for `My Tickets`, QR presentation, and checker scan UX.
- Add executable Android/iOS UI coverage and synchronize `docs/context/*` in the same change.

## Explicitly Out Of Scope

- selecting or activating a concrete external PSP
- complimentary tickets, refund/cancel lifecycle, or `sold_out` automation
- offline scanner buffering, attendance analytics, or wallet-pass export

## Constraints

- Existing provider-specific code does not count as provider selection; the ticketing client slice must remain provider-agnostic.
- New and materially changed repository code must keep Russian comments.
- UI automation should use stable identifiers/test tags rather than brittle text-only selectors.
- Active context docs must be updated in the same change when the next bounded step moves.

## Acceptance Signals

- Android and iOS main shells expose the `Билеты` tab with issued tickets.
- Buyer can reveal a QR representation for an issued ticket.
- Staff can submit a QR payload and observe a deterministic check-in result.
- Automated verification covers the new shared/mobile ticketing slice.

## Implementation Outcome

## Delivered

- Added shared `:feature:ticketing` MVI slice plus shared/iOS bridge wiring for ticket loading and checker scan actions.
- Added Android Compose `Билеты` tab UI with ticket wallet, QR rendering, checker scan form, and main-shell wiring.
- Added iOS SwiftUI `TicketWalletView` / `TicketWalletModel`, fixture-backed scan behavior, and fixed SwiftUI accessibility identifier collisions so child controls retain stable ids for XCUITest.
- Updated active context docs so the ticketing client surfaces are marked delivered and the next bounded `P0` step moves to comedian applications + lineup ordering.

## Verification

- `./gradlew :feature:ticketing:allTests :shared:compileKotlinMetadata :composeApp:compileDebugKotlin`
- `./gradlew :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest'`
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testTicketTabShowsWalletAndCheckInSurface test CODE_SIGNING_ALLOWED=NO`

## Remaining Follow-Up

- Implement comedian applications plus organizer approve/reject/waitlist and lineup ordering as the next bounded `P0` slice.
- Return to concrete PSP selection/activation only in the final pre-publication stage after explicit user confirmation.
- Keep later ticketing follow-ups separate: complimentary issuance, refund/cancel, `sold_out` automation, wallet pass, and offline-tolerant checker tooling.

---

## Formalized Implementation Request (Comedian Applications Foundation)

### Epic

- `EPIC-067` — comedian applications and lineup foundation.

### Why This Step

- После завершения provider-agnostic ticketing foundation именно comedian applications + organizer review/ordering стали следующим bounded `P0` slice.
- В кодовой базе пока нет ни server persistence, ни API surface для заявок комиков, поэтому безопаснее начать с backend foundation без одновременного захвата shared/mobile UI.
- Этот срез additive, локален по blast radius и подготавливает следующий шаг: связь `approved -> lineup entry`.

### Decomposition

- `TASK-067` — backend foundation для comedian applications: migration, persistence, submit/list/status change, tests.
- `TASK-068` — lineup entry foundation и автосвязка `approved -> lineup draft entry` с order index.
- `TASK-069` — shared/data/feature integration для organizer/comedian applications surfaces.
- `TASK-070` — Android/iOS UI wiring и executable coverage для applications/lineup management.

### Scope For This Run

- Добавить backend migration и persistence-модели для заявок комиков.
- Добавить comedian submit route и organizer list/status-change routes.
- Добавить минимально релевантные tests для migration и route behavior.

### Explicitly Out Of Scope

- live lineup reordering/status
- shared/mobile modules и UI
- push notifications по статусам заявок
- donations/payout dependencies

### Constraints

- Статусы заявки должны следовать спецификации: `submitted`, `shortlisted`, `approved`, `waitlisted`, `rejected`, `withdrawn`.
- Reviewer-side доступ к заявкам должен оставаться в owner/manager scope.
- Новый backend flow обязан писать structured diagnostics без секретов.
- Если в ходе среза появится необходимость в irreversible migration/backfill beyond additive schema, задача должна быть остановлена как blocker.

### Acceptance Signals

- Комик может создать заявку на опубликованное событие.
- Organizer owner/manager может видеть список заявок события.
- Organizer owner/manager может перевести заявку в review-статус.
- Regression coverage проверяет happy path, forbidden/error path и migration surface.

### Implementation Outcome

#### Delivered

- Добавлены `comedian_applications` migration/persistence foundation и отдельный backend repository/service слой без смешивания с `event` или `ticketing`.
- Добавлены authenticated routes: comedian submit (`POST /api/v1/events/{eventId}/applications`), organizer list (`GET /api/v1/events/{eventId}/applications`) и organizer review status change (`PATCH /api/v1/events/{eventId}/applications/{applicationId}`).
- Добавлены structured diagnostics, API-contract запись в OpenAPI, targeted migration coverage и route regression tests.

#### Verification

- `./gradlew :server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`

#### Remaining Follow-Up

- `TASK-068`: lineup-entry foundation и `approved -> lineup draft entry` с `order_index`.
- `TASK-069`: shared/data/feature integration для organizer/comedian applications surfaces.
- `TASK-070`: Android/iOS UI wiring и executable coverage для applications/lineup management.

---

## Formalized Implementation Request (Lineup Entry Foundation)

### Epic

- `EPIC-067` — comedian applications and lineup foundation.

### Task

- `TASK-068` — lineup entry foundation и автосвязка `approved -> lineup draft entry` с explicit `order_index`.

### Why This Step

- После `TASK-067` backend уже умел принимать и ревьювить заявки, но approved-заявка еще не переходила в lineup и organizer не мог управлять порядком выступлений.
- Без backend lineup foundation дальнейший shared/mobile slice был бы вынужден строиться поверх временных read-model или ручных фикстур.
- Нужен был безопасный additive bridge без live-state и destructive cleanup semantics в этом же запуске.

### Scope For This Run

- Добавить migration и persistence для `lineup_entries`.
- Материализовать ровно одну draft lineup entry при переводе заявки в `approved`.
- Добавить organizer/host lineup list + reorder API с явным `order_index`.
- Добавить targeted migration/route coverage и синхронизировать `docs/context/*`.

### Explicitly Out Of Scope

- comedian-facing lineup visibility
- Android/iOS/shared UI
- live-stage status (`up_next` / `on_stage` / `done` / `delayed` / `dropped`) как отдельный workflow
- автоматическое удаление/rebuild lineup entry при обратной смене review-статуса

### Constraints

- Approved-заявка должна создавать lineup entry идемпотентно, без дублей при повторном PATCH.
- Порядок lineup должен храниться явно через `order_index`.
- Reorder должен работать только на полном текущем lineup наборе, чтобы не плодить неявные partial semantics.
- Backend flow обязан писать structured diagnostics без секретов.

### Acceptance Signals

- Organizer review `approved` создает draft lineup entry.
- Organizer/host может получить текущий lineup события.
- Organizer/host может переставить lineup через явные `order_index`.
- Regression coverage проверяет migration, happy path, validation/error path и diagnostics.

### Implementation Outcome

#### Delivered

- Добавлены `lineup_entries` migration/persistence model и отдельный backend repository/service слой для lineup.
- Organizer review `approved` теперь идемпотентно создает одну draft lineup entry с автоназначением следующего `order_index`.
- Добавлены authenticated organizer/host routes: `GET /api/v1/events/{eventId}/lineup` и `PATCH /api/v1/events/{eventId}/lineup`.
- Обновлены OpenAPI и контекст-документы; зафиксировано решение `D-067` про one-way materialization без auto-delete в этом slice.

#### Verification

- `./gradlew :server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`

#### Remaining Follow-Up

- `TASK-069`: shared/data/feature integration для organizer/comedian applications и lineup surfaces.
- `TASK-070`: Android/iOS UI wiring и executable coverage для applications/lineup management.
- Отдельно позднее: live-stage semantics и richer lineup editing rules.

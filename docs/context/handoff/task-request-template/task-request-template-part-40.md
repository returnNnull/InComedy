# Task Request Template Part 40

## Implementation Outcome (EPIC-070 TASK-089 Android/iOS Donations Surface)

### Epic

- `EPIC-070` — donations/payout foundation.

### Task

- `TASK-089` — Android/iOS donation и comedian payout surfaces с executable verification для delivered foundation без выбора внешнего PSP.

### Status

- `completed`

### Delivered

- Добавлен новый shared `:feature:donations` модуль с `DonationsState`, `DonationsViewModel` и common tests для donation history и comedian payout profile surface.
- `shared` теперь экспортирует `DonationsBridge` и snapshot-модели для Swift-слоя, а `InComedyKoin` регистрирует общий `DonationsViewModel`.
- Android main shell получил вкладку `Донаты` с `DonationHubTab`, `DonationsAndroidViewModel`, payout-profile формой и sent/received donation history.
- iOS main shell получил `DonationHubView` и `DonationHubModel`; SwiftUI форма payout profile теперь синхронизируется по `updatedAtIso`, чтобы backend-normalized update с тем же `id` не оставлял stale beneficiary значение в поле.
- Scope остаётся provider-agnostic/manual-settlement-ready и по-прежнему не включает external checkout, webhook ingestion, operator verification workflow или payout automation.

### Verification

- `Passed: ./gradlew :feature:donations:allTests :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.donations.ui.DonationHubTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' :composeApp:compileDebugKotlin`
- `Passed: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testDonationTabShowsPayoutAndHistorySurface test`

### Review-Driven Follow-Up

- После commit `1059945` найден и устранён donations/session lifecycle regression: `DonationsViewModel` теперь сбрасывает весь `DonationsState`, если активная сессия пропала, чтобы sent/received history и payout profile не оставались в process-lived памяти после logout или потери access token.
- `DonationsAndroidViewModel` больше не инициирует eager refresh в `init`; Android donations tab использует уже существующий session-driven `LaunchedEffect(accessToken)` внутри `DonationHubTab`.
- `DonationHubModel` больше не делает eager refresh при создании, а `MainGraphView` запускает `donationModel.refresh()` только когда `isAuthorized == true` и `isLoadingContext == false`, чтобы initial iOS donations load не опережал готовность session context.
- Добавлены regression checks для shared state reset и Android session-bootstrap refresh path; iOS путь повторно подтверждён через targeted donations XCUITest.

### Follow-Up Verification

- `Passed: ./gradlew :feature:donations:allTests`
- `Passed: ./gradlew :composeApp:compileDebugKotlin :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.donations.ui.DonationHubTabContentTest.refreshesWhenAccessTokenBecomesAvailable'`
- `Passed: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testDonationTabShowsPayoutAndHistorySurface test`

### Notes

- Security review verdict: базовый client/shared slice и review-driven follow-up остаются внутри authenticated donation history и comedian payout-profile UI поверх уже существующего provider-agnostic backend surface; provider secrets, checkout activation, webhook ingestion, payout execution и implicit reuse ticketing PSP credentials не добавлялись.
- `R-005` остаётся open: platform UX уже доставлен, но legal/financial scheme, operator verification workflow и explicit donation/payout provider choice всё ещё не подтверждены.

### User Confirmation Outcome

- Пользователь явно подтвердил review и запросил merge/push finished branch.
- `EPIC-070` переведён из `awaiting_user_review` в `done`; reopen допустим только для post-merge regression или нового explicit follow-up request.

### Next

- `После merge/push считать следующим future candidate EPIC-071 из next-epic-queue, но не открывать новый epic автоматически без отдельного user request.`

## Implementation Outcome (EPIC-071 TASK-090 Backend Announcements Foundation)

### Epic

- `EPIC-071` — notifications / announcements delivery foundation.

### Task

- `TASK-090` — backend foundation для organizer announcements/event feed с public read route, protected create route, websocket publication и server coverage.

### Status

- `completed`

### Delivered

- Добавлен новый `:domain:notifications` модуль с audience-safe `EventAnnouncement`, `EventAnnouncementAuthorRole` и provider-agnostic `NotificationService` seam для дальнейшего shared/data wiring.
- На backend-е добавлены миграция `V16__event_announcements_foundation.sql`, `AnnouncementRepository`/`PostgresAnnouncementRepository` и persistence foundation для `event_announcements`.
- Зарегистрированы public `GET /api/v1/public/events/{eventId}/announcements` и protected `POST /api/v1/events/{eventId}/announcements` с published/public event gating, owner/manager/host publish policy, rate limiting и diagnostics hooks.
- Public `/ws/events/{eventId}` теперь публикует `announcement.created` с audience-safe payload-ом, а `data:lineup` transport научен игнорировать неподдерживаемые live-event типы, чтобы existing lineup consumer не ломался до появления dedicated notifications UI.

### Verification

- `Passed: ./gradlew --no-daemon --no-build-cache --max-workers=1 :domain:notifications:compileKotlinJvm :server:compileKotlin`
- `Passed: ./gradlew --no-daemon --no-build-cache --max-workers=1 :server:test --tests 'com.bam.incomedy.server.notifications.AnnouncementRoutesTest' --tests 'com.bam.incomedy.server.lineup.EventLiveChannelRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'`
- `Passed: ./gradlew --no-build-cache :data:lineup:allTests`

### Notes

- Security review verdict: delivered slice добавляет только organizer-authenticated announcement create path и public audience-safe feed/read delivery; push-provider secrets, device-token registration, `/api/v1/me/notifications`, staff-private payloads и background delivery в этом шаге не появлялись.
- `R-014` остаётся open: backend foundation уже доставлен, но shared/data contract, Android/iOS surfaces, moderation/reporting, durable outbox и push-provider activation всё ещё отсутствуют.

### Next

- `Ровно одна следующая подзадача: TASK-091 — shared/data announcement service contract и transport integration для public event feed без platform UI и без push-provider activation.`

## Active Request (EPIC-071 Notifications / Announcements Delivery Foundation)

### Epic

- `EPIC-071` — notifications / announcements delivery foundation.

### Status

- `in_progress`

### Why Now

- Scheduled automation после закрытия `EPIC-070` зафиксировал следующий `P0` epic из `product/next-epic-queue.md`.
- Внешний push provider всё ещё не подтверждён, поэтому первый шаг должен был остаться provider-agnostic и не трактовать FCM/APNs как уже выбранный runtime path.

### Ordered Subtask Plan

1. `TASK-090` — backend foundation для organizer announcements/event feed с public read route, protected create route, websocket publication и server coverage.
   - Status: `completed`
2. `TASK-091` — shared/data announcement service contract и transport integration для public event feed без platform UI.
   - Status: `planned`
3. `TASK-092` — Android/iOS announcement/feed surfaces и executable verification без push-provider activation.
   - Status: `planned`

### Scope Rules

- Этот epic сначала покрывает provider-agnostic organizer announcements/event feed foundation.
- `/api/v1/me/notifications`, moderation/reporting, durable outbox, FCM/APNs activation и background delivery не входят в `TASK-090`.
- Появление push SDK, config example или candidate integration не считать подтверждением active/default push provider без отдельного user confirmation.

### Current Next

- `TASK-091` — shared/data announcement service contract и transport integration для public event feed без platform UI и без push-provider activation.

### Current Recovery State

- `EPIC-071` остаётся на ветке `codex/epic-071-notifications-announcements-delivery-foundation`; `TASK-090` уже completed и verified, а следующий recovery target — `TASK-091` после локальной commit boundary текущего шага.

### Recovery Guardrail

- `Если verification снова затронет Gradle/Kotlin build harness, не запускать параллельные daemon-сессии по одному worktree; использовать repair path из I-002, а не повторять cache-racing commands.`

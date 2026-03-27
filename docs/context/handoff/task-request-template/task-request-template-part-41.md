# Task Request Template Part 41

## Implementation Outcome (EPIC-071 TASK-091 Shared/Data Notifications Transport)

### Epic

- `EPIC-071` — notifications / announcements delivery foundation.

### Task

- `TASK-091` — shared/data announcement service contract и transport integration для public event feed без platform UI.

### Status

- `completed`

### Delivered

- Добавлен новый `:data:notifications` модуль с `NotificationBackendApi`, transport DTO для `GET /api/v1/public/events/{eventId}/announcements` и `POST /api/v1/events/{eventId}/announcements`, а также `BackendNotificationService`, реализующий shared `NotificationService`.
- Добавлен `notificationsDataModule`, а `shared` и `InComedyKoin` теперь регистрируют `:data:notifications` и `:domain:notifications`, чтобы следующий Android/iOS шаг получил готовый shared DI seam без дополнительной модульной подготовки.
- Добавлены common transport tests для public list и protected create announcement flow, чтобы shared/data contract был зафиксирован исполняемым покрытием до platform UI.
- Scope остаётся provider-agnostic и по-прежнему не включает Android/iOS surfaces, `/api/v1/me/notifications`, device-token registration, moderation/reporting, durable outbox или activation FCM/APNs.

### Verification

- `Passed: ./gradlew --no-daemon --no-build-cache --max-workers=1 :data:notifications:allTests :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64`

### Notes

- Security review verdict: delivered slice добавляет только audience-safe/shared transport и DI wiring поверх уже существующего backend announcements surface; push-provider secrets, background delivery, device-token registration, staff/private payloads и implicit provider choice не появлялись.
- `R-014` остаётся open: shared/data gap закрыт, но Android/iOS feed surfaces, `/api/v1/me/notifications`, moderation, durable outbox и push/background path всё ещё отсутствуют.

### Next

- `Ровно одна следующая подзадача: TASK-092 — Android/iOS announcement/feed surfaces и executable verification без push-provider activation.`

## Implementation Outcome (EPIC-071 TASK-092 Android/iOS Announcement Feed Surfaces)

### Epic

- `EPIC-071` — notifications / announcements delivery foundation.

### Task

- `TASK-092` — Android/iOS announcement/feed surfaces и executable verification без push-provider activation.

### Status

- `completed`

### Delivered

- Добавлен новый `:feature:notifications` модуль с `NotificationsState`, `NotificationsViewModel` и common test coverage, чтобы provider-agnostic announcement/feed logic жила в shared KMP-слое поверх уже delivered `NotificationService`.
- `shared` теперь экспортирует `NotificationsViewModel`, `NotificationsBridge` и `NotificationsStateSnapshot`, поэтому Android и iOS получают общий DI/bridge seam без platform-specific network duplication.
- Android main shell теперь показывает вкладку `Анонсы`: `MainScreen`, `MainGraph`, `AppNavHost` и `AndroidViewModelFactories` подключают `NotificationsAndroidViewModel`, а Compose `AnnouncementFeedTab` показывает eligible public event selector, refresh/publish controls и announcement history.
- iOS main shell теперь показывает `AnnouncementFeedView` через `AnnouncementFeedModel` в `MainGraphView`; targeted `iosAppUITests` покрывают read/publish flow, а overflow-safe `openTab()` helper учитывает, что поздние вкладки `TabView` открываются через `More`.
- Scope остаётся provider-agnostic и по-прежнему не включает `/api/v1/me/notifications`, device-token registration, moderation/reporting, durable outbox, push-provider activation или background delivery.

### Verification

- `Passed: ./gradlew --no-daemon --no-build-cache --max-workers=1 :feature:notifications:allTests :shared:compileCommonMainKotlinMetadata :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.notifications.ui.AnnouncementFeedTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' --tests 'com.bam.incomedy.viewmodel.AndroidViewModelFactoriesTest' :composeApp:compileDebugKotlin`
- `Passed: JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -quiet -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
- `Passed: JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testAnnouncementsTabShowsFeedAndPublishSurface test CODE_SIGNING_ALLOWED=NO`

### Notes

- Security review verdict: delivered mobile slice остаётся provider-agnostic, использует только organizer read/publish surface поверх public event feed и не добавляет `/api/v1/me/notifications`, device-token registration, background delivery, staff/private payloads или implicit FCM/APNs selection.
- `R-014` остаётся open: Android/iOS surfaces уже delivered, но user-specific inbox, moderation/reporting, durable outbox/fanout и push/background path всё ещё отсутствуют.

### Next

- `Ровно один следующий шаг: дождаться explicit user review confirmation по EPIC-071; без него не открывать EPIC-072.`

## Active Request (EPIC-071 Notifications / Announcements Delivery Foundation)

### Epic

- `EPIC-071` — notifications / announcements delivery foundation.

### Status

- `awaiting_user_review`

### Why Now

- Scheduled automation продолжает активный `P0` epic из `product/next-epic-queue.md`.
- Внешний push provider всё ещё не подтверждён, поэтому shared/data follow-up по-прежнему должен оставаться provider-agnostic и не трактовать FCM/APNs как уже выбранный runtime path.

### Ordered Subtask Plan

1. `TASK-090` — backend foundation для organizer announcements/event feed с public read route, protected create route, websocket publication и server coverage.
   - Status: `completed`
2. `TASK-091` — shared/data announcement service contract и transport integration для public event feed без platform UI.
   - Status: `completed`
3. `TASK-092` — Android/iOS announcement/feed surfaces и executable verification без push-provider activation.
   - Status: `completed`

### Scope Rules

- Этот epic сначала покрывает provider-agnostic organizer announcements/event feed foundation.
- `/api/v1/me/notifications`, moderation/reporting, durable outbox, FCM/APNs activation и background delivery не входят в `TASK-092`.
- Появление push SDK, config example или candidate integration не считать подтверждением active/default push provider без отдельного user confirmation.

### Current Next

- `Дождаться explicit user review confirmation по EPIC-071; без него не открывать EPIC-072.`

### Current Recovery State

- `EPIC-071` остаётся на ветке `codex/epic-071-notifications-announcements-delivery-foundation`; весь ordered plan уже delivered, локальная commit boundary этого run должна закрыть `TASK-092`, а recovery posture после commit — review boundary `awaiting_user_review`.

### Recovery Guardrail

- `Если verification снова затронет Gradle/Kotlin build harness, не запускать параллельные daemon-сессии по одному worktree; использовать repair path из I-002, а не повторять cache-racing commands.`

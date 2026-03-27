# Task Request Template Part 42

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

- `Passed: ./gradlew --no-daemon --no-build-cache --max-workers=1 :feature:notifications:allTests :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.notifications.ui.*' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' :composeApp:compileDebugKotlin`
- `Passed: JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -derivedDataPath /tmp/incomedy-uitest-deriveddata-task092 -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 -only-testing:iosAppUITests/iosAppUITests/testAnnouncementsTabShowsFeedAndPublishSurface test CODE_SIGNING_ALLOWED=NO`

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

- `Product work остановлен на review boundary: до explicit user review confirmation по EPIC-071 не открывать EPIC-072 и не расширять notifications scope.`

### Current Recovery State

- `EPIC-071` остаётся на ветке `codex/epic-071-notifications-announcements-delivery-foundation`; весь ordered plan уже delivered, а `TASK-090`, `TASK-091` и `TASK-092` могут переоткрываться только как regression/review follow-up поверх review boundary `awaiting_user_review`.

### Recovery Guardrail

- `Если verification снова затронет Gradle/Kotlin build harness, не запускать параллельные daemon-сессии по одному worktree; использовать repair path из I-002, а не повторять cache-racing commands.`
- `Если targeted iOS UITest после успешной сборки снова молчит до старта runner-а, сначала переиспользовать repair path из I-001: открыть/перезапустить Xcode и Simulator, а уже потом углубляться в host-level simulator triage.`

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

### Review-Driven Follow-Up

- `NotificationsViewModel` теперь не смешивает announcement feed разных event-ов: при переключении event-а stale список очищается, а late async results от предыдущего `load/create` игнорируются, если пользователь уже выбрал другой event.
- Common regression coverage расширена проверками на cross-event publish/load isolation и на failure path, который не должен оставлять feed предыдущего event-а после неуспешной загрузки нового.
- `iosAppUITests` больше не зависят от жёсткого текста `"More"`: overflow tab helper теперь тапает последнюю кнопку `UITabBar`, поэтому navigation остаётся стабильной даже при системной локализации или alias-ах системной вкладки.

### Follow-Up Verification

- `Passed: ./gradlew --no-daemon --no-build-cache --max-workers=1 :feature:notifications:allTests`
- `Passed: JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -derivedDataPath /tmp/incomedy-uitest-deriveddata-task092-review -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 -only-testing:iosAppUITests/iosAppUITests/testAnnouncementsTabShowsFeedAndPublishSurface test CODE_SIGNING_ALLOWED=NO`

### Notes

- Security review verdict: delivered mobile slice и review-driven follow-up остаются provider-agnostic, ограничены shared in-memory feed orchestration и UITest navigation helper-ом, не добавляют `/api/v1/me/notifications`, device-token registration, background delivery, staff/private payloads или implicit FCM/APNs selection.
- `R-014` остаётся open: Android/iOS surfaces уже delivered, но user-specific inbox, moderation/reporting, durable outbox/fanout и push/background path всё ещё отсутствуют.

### User Confirmation Outcome

- Пользователь явно подтвердил review и запросил commit/merge/push finished branch.
- `EPIC-071` переведён из `awaiting_user_review` в `done`; reopen допустим только для post-merge regression или нового explicit follow-up request.

### Next

- `После merge/push считать следующим future candidate EPIC-072 из next-epic-queue, но не открывать новый epic автоматически без отдельного user request.`

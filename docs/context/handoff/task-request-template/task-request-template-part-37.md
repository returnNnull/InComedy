# Task Request Template Part 37

## Implementation Outcome (EPIC-069 TASK-086 Android/iOS Realtime Wiring)

### Epic

- `EPIC-069` — realtime/WebSocket delivery для live stage updates.

### Task

- `TASK-086` — Android/iOS wiring на новый realtime feed и executable verification delivered live-update behavior.

### Status

- `completed`

### Delivered

- Shared `LineupViewModel` теперь включает public realtime feed только при активном platform lifecycle через `setLiveUpdatesActive(...)`.
- Audience-safe live summary из `/ws/events/{eventId}` теперь напрямую обновляет organizer lineup state без повторного full reload-а.
- При `application_approved` realtime event-е shared слой выполняет authenticated organizer refresh заявок, чтобы applications surface не застывал на старом review status.
- Android `LineupManagementTab` и `MainScreen` теперь включают feed на `ON_START` и выключают его на `ON_STOP` / dispose.
- iOS `LineupScreenModel`, `LineupBridge` и `LineupManagementView` теперь активируют feed только пока вкладка видима и `scenePhase == .active`.
- Scope не расширялся в staff/private channel, push fallback, durable outbox или multi-instance fanout.

### Verification

- `Passed: ./gradlew :data:lineup:allTests :feature:lineup:allTests :shared:compileKotlinMetadata :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.lineup.ui.LineupManagementTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' :composeApp:compileDebugKotlin`
- `Passed: JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
- `Passed: JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -derivedDataPath /tmp/incomedy-uitest-deriveddata-task086 -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test CODE_SIGNING_ALLOWED=NO`

### Notes

- Security review verdict: delivered client realtime slice остаётся public/read-only и audience-safe; platform lifecycle лишь ограничивает длительность активной подписки, а access token используется только для organizer-side refresh заявок после публичного approval update.
- `R-013` остаётся open: mobile consumption delivered, но rollout-ready verdict всё ещё блокируется отсутствием durable outbox/multi-instance fanout и reconnect/push fallback.

### Next

- `EPIC-069` переведён в `awaiting_user_review`; ровно один следующий шаг — дождаться явного user review/confirmation, не открывая `EPIC-070` до этого подтверждения.

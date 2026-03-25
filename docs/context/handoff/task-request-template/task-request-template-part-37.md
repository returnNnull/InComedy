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

### User Confirmation Outcome

- Пользователь явно подтвердил review и запросил merge/push finished branch.
- `EPIC-069` переведён из `awaiting_user_review` в `done`; reopen допустим только для post-merge regression или нового explicit follow-up request.

### Next

- После merge/push считать следующим future candidate `EPIC-070` из next-epic-queue, но не открывать новый epic автоматически без отдельного user request.

## Active Request (EPIC-070 Donations/Payout Foundation)

### Epic

- `EPIC-070` — donations/payout foundation.

### Status

- `in_progress`

### Why Now

- Пользователь явно запросил старт `EPIC-070` после закрытия и merge/push `EPIC-069`.
- По `product/backlog.md` и `product/next-epic-queue.md` donations/payout foundation остаётся следующим `P0` bounded context.
- Выбор внешнего donation/payout provider всё ещё не подтверждён, поэтому первый шаг должен остаться provider-agnostic и не трактовать существующий ticketing PSP adapter как готовое решение для нового домена.

### Ordered Subtask Plan

1. `TASK-087` — backend foundation для comedian payout profile и donation intents с verified-payout gate, protected self-service routes и server coverage.
   - Status: `completed`
2. `TASK-088` — shared/data donation service contract и transport integration для payout profile, donation history и intent creation без platform UI.
   - Status: `in_progress`
3. `TASK-089` — Android/iOS donation и comedian payout surfaces с executable verification для delivered foundation.
   - Status: `planned`

### Scope Rules

- Этот epic покрывает provider-agnostic foundation домена donations/payouts.
- Donation flow не должен смешиваться с ticket checkout и не должен считать существующий `YooKassa` adapter для ticketing подтверждением donation/payout provider choice.
- Первый bounded step может создать payout profile / donation ledger foundation и manual-settlement-ready persistence без включения полноценного external checkout, webhook processing или payout automation в том же run.

### Current Next

- `Ровно одна текущая продуктовая подзадача — TASK-088: shared/data donation transport integration поверх уже delivered backend foundation, без platform UI и без активации конкретного PSP.`

### Current Recovery State

- `EPIC-070 открыт по явному user request на ветке codex/epic-070-donations-payout-foundation; TASK-087 уже закрыт, а текущий active bounded step — TASK-088.`

### Recovery Guardrail

- `До отдельного user confirmation нельзя считать donation/payout provider подтверждённым по коду, env или существующему ticketing checkout adapter-у; первый шаг EPIC-070 остаётся provider-agnostic foundation only.`

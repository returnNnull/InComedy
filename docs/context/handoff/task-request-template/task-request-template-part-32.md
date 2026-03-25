# Task Request Template Part 32

## Implementation Outcome (EPIC-068 Shared/Data/Feature Live Stage Integration)

### Epic

- `EPIC-068` — live stage status foundation.

### Task

- `TASK-072` — shared/data/feature integration для live-stage read/write semantics и общего экспорта lineup live-state статусов.

### Status

- `completed`

### Delivered

- Domain lineup contract now supports the full live-stage status set `draft/up_next/on_stage/done/delayed/dropped` and exposes `LineupManagementService.updateLineupEntryStatus(...)` for a bounded shared mutation path.
- `LineupBackendApi` and `BackendLineupManagementService` now map all lineup live-state statuses and call backend `POST /api/v1/events/{eventId}/lineup/live-state` without leaking transport details into shared/domain layers.
- `LineupViewModel`, `LineupIntent`, and `LineupState` now support live-stage read/write semantics in the shared feature layer: organizer state can derive `current performer` / `next up`, and live-stage mutations update the lineup state without introducing platform UI code.
- `LineupBridge` and `LineupStateSnapshot` now export current/next performer summary plus a shared live-stage mutation entry point for later Android/iOS UI wiring.
- Bootstrap, backlog, architecture, test strategy, session/task memory, and recovery checkpoint are synchronized so the next bounded step is `TASK-073`, not another shared-layer slice.

### Verification

- `Passed: ./gradlew :feature:lineup:allTests :data:lineup:compileKotlinMetadata :shared:compileKotlinMetadata :composeApp:compileDebugKotlin --console=plain`

### Notes

- Android/iOS UI wiring, WebSocket delivery, push notifications, and announcements/feed remain out of scope for this subtask.
- No new governance decision was needed; the result continues existing `D-046` MVP prioritization and `D-068` bounded-task completion rules.

### Next

- `Ровно одна следующая подзадача: TASK-073 — Android/iOS UI wiring для current performer / next up / organizer live controls поверх уже доставленного shared live-stage foundation, без realtime/WebSocket delivery.`

## Implementation Outcome (EPIC-068 Platform Live Stage UI Wiring)

### Epic

- `EPIC-068` — live stage status foundation.

### Task

- `TASK-073` — Android/iOS UI wiring для current performer / next up и organizer live controls поверх delivered shared live-stage foundation.

### Status

- `in_progress`

### Delivered

- Android `LineupManagementTab` now renders live-stage summary (`current performer` / `next up`) and organizer live-control buttons for `draft/up_next/on_stage/done/delayed/dropped`, wired to the existing shared mutation path without adding backend scope.
- `LineupAndroidViewModel` and `MainScreen` now expose `updateLineupEntryStatus(...)` into the lineup tab bindings so Compose UI uses the already delivered shared/data/feature contract instead of introducing platform-local state.
- SwiftUI `LineupScreenModel` and `LineupManagementView` now mirror the same live-stage summary and organizer controls, including fixture-mode local status transitions for UI previews/tests and stable accessibility identifiers for XCUITest.
- Android UI tests now cover live-stage summary plus organizer live-control callback wiring, and the iOS lineup XCUITest path has been extended with live-stage assertions for the same bounded surface.
- `iosApp/scripts/build-shared.sh` now bootstrap-s a repo-local Kotlin/Native bundle under `.gradle/xcode/konan-bootstrap` and reuses it for the Xcode shared build, so the generic iOS simulator build no longer fails early on `~/.konan` lock contention or duplicate KLIB resolution.
- Remaining SwiftUI `#Preview` macro blocks in auth/main/root shell files were replaced with `PreviewProvider` fallbacks, so sandboxed generic `xcodebuild` no longer depends on `PreviewsMacros` / `swift-plugin-server` for this slice.

### Verification

- `Passed: ./gradlew :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.lineup.ui.LineupManagementTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' :composeApp:compileDebugKotlin --console=plain`
- `Blocked by environment: xcrun simctl list devices available` failed with `CoreSimulatorService connection became invalid` / `Failed to initialize simulator device set`, so the sandbox still has no usable simulator device set for the targeted XCUITest path.
- `Blocked by environment: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -showdestinations` on `2026-03-25 12:04 MSK` still reported `CoreSimulatorService connection became invalid`, `Simulator device support disabled`, and only placeholder destinations (`My Mac`, `Any iOS Device`, `Any iOS Simulator Device`), so the repo-local rerun still cannot select or boot a real iOS simulator on this host.
- `Blocked by environment: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test CODE_SIGNING_ALLOWED=NO` now fails with `Unable to find a device matching the provided destination specifier`, because the sandbox has no available iOS Simulator device set / `CoreSimulatorService` access.
- `Passed: JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/incomedy-deriveddata-buildcheck8 build CODE_SIGNING_ALLOWED=NO` -> `BUILD SUCCEEDED`

### Notes

- No new governance decision was needed; the result continues `D-067` lineup/live-stage delivery and `D-068` same-task verification policy.
- Because iOS verification still cannot complete reliably in this sandbox and the latest reruns still lose `CoreSimulatorService` before simulator selection, `TASK-073` must remain the active subtask and must not be marked `completed` or followed by a new epic step yet.
- Legacy `partial` wording in older automation/session memory is historical only; after the executor runbook sync, the current active status for `TASK-073` must be treated as `in_progress` until targeted verification either completes or is closed through a finished docs-only blocker verdict.

### Next

- `Ровно одна следующая подзадача: продолжить тот же TASK-073 и добить targeted XCUITest verification для lineup live-stage UI на unrestricted host с рабочим CoreSimulatorService; новый epic/task не выбирать.`

## Documentation Outcome (Executor Prompt Centralization)

### Epic

- `EPIC-068` — текущий активный delivery epic сохранён без переключения ветки; governance sync выполнен как docs-only follow-up поверх уже активного recovery context.

### Task

- `TASK-074` — вынести executor process rules в отдельный repo-side runbook и перевести все `InComedy Executor` automations на ссылку к этому документу.

### Status

- `docs_only`

### Delivered

- Добавлен `docs/context/handoff/automation-executor-prompt.md` как единый runbook для scheduled `InComedy Executor` automation runs.
- `docs/context/handoff/context-protocol.md` и `docs/context/README.md` теперь явно ссылаются на этот runbook, когда речь идет об automation governance или executor runs.
- `docs/context/00-current-state.md`, `docs/context/handoff/context-protocol.md` и `docs/context/README.md` синхронизированы с новым правилом: executor automation runs больше не должны завершаться terminal `partial`, legacy `awaiting_verification` / `partial` теперь трактуются только как recovery aliases старых записей, а mandatory security review явно закреплен и для executor-driven closure.
- Семантика daily limit уточнена: в одном cycle доступно максимум 10 automation run slots, а `AutomationState` теперь использует числовое поле `run_slots_used_in_cycle` вместо списка завершенных подзадач.
- Все automation TOML в `/Users/abetirov/.codex/automations/incomedy-executor-*/automation.toml` переведены на короткий prompt со ссылкой к repo-side runbook вместо длинной встроенной копии process-правил.

### Verification

- `Passed: rg -n --glob 'automation.toml' 'automation-executor-prompt.md' /Users/abetirov/.codex/automations/incomedy-executor-*`
- `Passed: выборочный re-read automation.toml для incomedy-executor-00-00 после update подтвердил, что prompt теперь ссылается на repo-side runbook`

### Notes

- Продуктовый следующий шаг не менялся: `TASK-073` остаётся фактической следующей implementation-задачей для `EPIC-068`.
- Legacy `partial` state для `TASK-073` теперь сохранён только как исторический snapshot в старых записях; актуальная recovery semantics должна считать `TASK-073` `in_progress` до финального verification/blocker verdict.
- Текущий cycle usage теперь считается по числу запусков, а не по числу завершенных subtasks; это уточнение не меняет следующий продуктовый шаг, но меняет planning semantics для automation day budget.
- Security review verdict: docs-only governance sync не менял security-impacting runtime surface.

### Next

- `Ровно одна следующая подзадача: вернуться к TASK-073 и пере-запустить targeted iOS XCUITest verification для lineup live-stage UI на хосте с рабочим CoreSimulatorService; новый epic/task не выбирать.`

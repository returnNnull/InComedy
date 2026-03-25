# Task Request Template Part 31

## EPIC-067 Review-Ready Completion

### Epic

- `EPIC-067` — comedian applications and lineup foundation.

### Task

- `TASK-070` — Android/iOS UI wiring и executable platform coverage для comedian submit, organizer review и lineup reorder поверх готового shared foundation.

### Completion Outcome

#### Outcome Status

- `completed`

#### Delivered

- Android surface остается delivered и green: отдельный lineup tab, `LineupManagementTab`, `LineupAndroidViewModel`, main-shell wiring, shared fixtures и targeted Robolectric coverage.
- iOS surface delivered end-to-end: `LineupScreenModel`, `LineupManagementView`, новый main-shell tab, targeted XCUITest `testLineupTabShowsApplicationsAndReorderSurface`, plus earlier Swift compile fixes in `MainGraphView.swift`.
- Repo-side Xcode/KMP bridge stabilized in `iosApp/scripts/build-shared.sh`: Xcode script phase reuses repo-local `.gradle/xcode` and launches `./gradlew --no-daemon --console=plain`, which removes the old cold-cache / empty-bundle build blocker.
- Final runtime fixes for trustworthy iOS verification:
  - `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosAppUITests.xcscheme` disables parallelizable cloned-device execution so targeted UI tests stay on the already booted primary simulator instead of the unstable Xcode clone path.
  - `LineupManagementView.swift` preserves child accessibility identifiers inside application/entry cards with `.accessibilityElement(children: .contain)`, so XCUITest can address `lineup.application.status.*`, `lineup.entry.moveDown.*`, and `lineup.entry.order.*` directly.
  - `LineupScreenModel.swift` explicitly reassigns fixture arrays to guarantee SwiftUI publishes review/reorder updates in fixture mode.

#### Root Cause Summary

- The last remaining failure was no longer product logic or app packaging.
- Xcode UI-test runtime still used a cloned simulator path that died before a terminal result, and even when the test reached real UI logic, card-level accessibility identifiers were collapsing child identifiers so the test could not see updated status/order elements.
- Disabling clone-style parallel execution plus preserving child accessibility identifiers removed the final blockers.

#### Verification

- `Previously passed: ./gradlew :feature:lineup:allTests :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' --tests 'com.bam.incomedy.feature.lineup.ui.LineupManagementTabContentTest' :composeApp:compileDebugKotlin`
- `Passed: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test CODE_SIGNING_ALLOWED=NO`

#### Review Summary

- `EPIC-067` now covers the full intended bounded slice: backend comedian applications foundation, organizer review -> lineup materialization, shared/data/feature client wiring, Android lineup shell, iOS lineup shell, and executable verification on both platforms.
- No new epic may start automatically from automation after this point; the branch stays on `codex/epic-067-comedian-applications-foundation` until explicit user review/confirmation.

#### User Confirmation Outcome

- Пользователь явно подтвердил, что задача завершена и ее можно merge-нуть и push-нуть.
- `EPIC-067` переведен из `awaiting_user_review` в `done`; reopen допустим только для нового follow-up request или post-merge regression.

#### Next

- `Ровно одна следующая подзадача: merge/push результата в main и затем выбрать следующий highest-priority unfinished epic из backlog.`

## Formalized Implementation Request (EPIC-068 Live Stage Backend Foundation)

### Epic

- `EPIC-068` — live stage status foundation.

### Task

- `TASK-071` — backend foundation для organizer/host live-stage status mutation поверх существующего lineup slice.

### Request Context

- `EPIC-067` уже полностью завершен, смержен в `main` и больше не должен оставаться активным epic.
- Следующий highest-priority unfinished `P0` item по `product/backlog.md` и `docs/standup-platform-ru/10-дорожная-карта-и-план-релиза.md` — `live stage status` поверх уже доставленного lineup foundation.
- Product/domain source of truth для этого шага:
  - `LINEUP-008` / `LINEUP-009` в `docs/standup-platform-ru/04-функциональные-требования.md`;
  - `POST /api/v1/events/{id}/lineup/live-state`, `stage.current_changed`, и live event channel contract в `docs/standup-platform-ru/08-api-и-событийная-модель.md`;
  - realtime/push остаются частью последующих bounded steps, не первого backend mutation foundation.

### Scope

- Добавить backend mutation surface для перевода одного lineup entry между live-stage статусами `draft`, `up_next`, `on_stage`, `done`, `delayed`, `dropped`.
- Зафиксировать явные server-side transition rules, чтобы organizer/host не могли создать нелогичное состояние lineup.
- Синхронизировать `OpenAPI`, active context docs и traceability с новым backend slice.
- Покрыть шаг targeted server regression tests.

### Out Of Scope

- WebSocket / realtime delivery клиентам.
- Push notifications, announcements/event feed.
- Android/iOS/shared client UI wiring для live stage.
- Donation/payout зависимости.

### Planned Decomposition

- `TASK-071` — backend live-stage mutation foundation: repository/service/route/OpenAPI/tests.
- `TASK-072` — shared/data/feature integration для live-stage read/write flows и общих domain statuses.
- `TASK-073` — Android/iOS UI wiring для current performer / next up / organizer live controls.
- `TASK-074` — realtime/WebSocket delivery и platform verification для live-stage updates.

### Status

- `completed`

### Completion Outcome

#### Delivered

- Backend lineup persistence now supports live-stage mutation through `LineupRepository.updateLineupEntryStatus`, `PostgresLineupRepository`, and the in-memory test double without introducing a new migration.
- `LineupService` now validates live-stage transitions before persistence, including:
  - rejection of no-op transitions;
  - terminal guard for `done` / `dropped`;
  - uniqueness of `up_next` and `on_stage` per event.
- `LineupRoutes` now exposes organizer/host `POST /api/v1/events/{eventId}/lineup/live-state` with bounded payload size, UUID/status validation, structured diagnostics, and safe HTTP error mapping.
- `OpenAPI`, backlog, architecture overview, test strategy, bootstrap snapshot, and handoff/task memory are synchronized with the new live-stage backend foundation.

#### Verification

- `Passed: ./gradlew :server:test --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`

#### Notes

- Realtime/WebSocket delivery, push, announcements, and Android/iOS/shared UI wiring were intentionally kept out of this subtask.
- No new governance decision was needed; the work stays under existing `D-046` MVP prioritization and `D-068` bounded-task completion rules.

### Next

- `Ровно одна следующая подзадача: TASK-072 — shared/data/feature integration для live-stage read/write semantics и общего экспорта lineup live-state статусов без platform UI и без realtime delivery.`

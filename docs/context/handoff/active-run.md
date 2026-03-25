# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-25T17:45:52+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-069`
- Active Subtask: `TASK-086`
- Branch: `codex/epic-069-live-stage-realtime-delivery`
- Epic Status: `awaiting_user_review`
- Run Status: `awaiting_user_review`

## Цель

- `Удерживать EPIC-069 на review boundary после завершения TASK-086 и не открывать следующий epic до явного user confirmation.`

## Итог

- `TASK-086` завершён: shared `LineupViewModel` теперь lifecycle-gated подписывается на public `/ws/events/{eventId}` feed, применяет audience-safe live summary к organizer lineup state и подтягивает organizer applications после `application_approved`, а Android/iOS surface-ы включают/выключают feed по platform lifecycle.`
- `Verification completed: ./gradlew :data:lineup:allTests :feature:lineup:allTests :shared:compileKotlinMetadata :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.lineup.ui.LineupManagementTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' :composeApp:compileDebugKotlin`, generic iOS simulator build и targeted `iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface` на `iPhone 17 Pro (iOS 26.2)` завершились успешно 2026-03-25.`
- `Локальная commit boundary TASK-086 закрыта текущим локальным commit-ом; ordered plan EPIC-069 полностью выполнен, поэтому epic переведён в posture awaiting_user_review.`

## Возобновление

- `Если чат оборвется, сверить branch и git status, затем сохранить posture EPIC-069/TASK-086 как awaiting_user_review. Не продолжать новый epic/task автоматически; ждать явного user confirmation по review boundary текущего epic-а.`

## Если сессия оборвётся

- Check `git status`.
- Confirm branch is still `codex/epic-069-live-stage-realtime-delivery`.
- Treat active recovery state as `EPIC-069/TASK-086` in posture `awaiting_user_review`.
- Do not start `EPIC-070` or any новый task без explicit user confirmation.
- Reopen `TASK-086` only if a concrete regression is found in delivered realtime lifecycle wiring or executable verification evidence.

## Следующий шаг

- `Ровно один следующий шаг после этого запуска: дождаться явного user review/confirmation по EPIC-069; только после этого выбирать следующий epic из next-epic-queue.`

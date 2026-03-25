# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-25T17:07:38+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-069`
- Active Subtask: `TASK-086`
- Branch: `codex/epic-069-live-stage-realtime-delivery`
- Epic Status: `in_progress`
- Run Status: `in_progress`

## Цель

- `Выполнить TASK-086 — Android/iOS wiring на новый realtime feed и executable verification delivered live-update behavior без расширения в staff/private channel, push fallback или durable outbox.`

## Итог

- `TASK-085` завершён: в KMP слоях доставлены доменные realtime-модели `LineupLiveUpdate/*`, `LineupManagementService.observeEventLiveUpdates(eventId)` и Ktor WebSocket transport adapter для public `/ws/events/{eventId}` channel-а без platform lifecycle wiring.`
- `Targeted verification `./gradlew :data:lineup:allTests :feature:lineup:allTests :shared:compileKotlinMetadata :composeApp:compileDebugKotlin` завершился успешно.`
- `Локальная commit boundary TASK-085 закрыта текущим локальным commit-ом; recovery переключён на TASK-086 как единственный следующий bounded step.`

## Возобновление

- `Если чат оборвется, сверить branch и git status, затем продолжить ровно с TASK-086/in_progress. Стартовать от уже доставленного shared/data realtime subscription contract и закрыть Android/iOS wiring/executable verification без расширения scope в staff/private channel, push fallback или durable outbox.`

## Если сессия оборвётся

- Check `git status`.
- Confirm branch is still `codex/epic-069-live-stage-realtime-delivery`.
- Treat active recovery state as `EPIC-069/TASK-086` in posture `in_progress`.
- Continue from the delivered shared/data realtime subscription contract of `TASK-085`.
- Keep the scope limited to Android/iOS realtime wiring and executable verification.
- Do not reopen `TASK-085` unless a concrete regression is found in the delivered contract/transport layer.

## Следующий шаг

- `Ровно один следующий шаг после этого запуска: выполнить TASK-086 — Android/iOS wiring на новый realtime feed и executable verification delivered live-update behavior.`

# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-25T16:37:32+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-069`
- Active Subtask: `TASK-085`
- Branch: `codex/epic-069-live-stage-realtime-delivery`
- Epic Status: `in_progress`
- Run Status: `in_progress`

## Цель

- `Выполнить TASK-085 — shared/data realtime subscription contract для lineup live updates на базе уже закоммиченного backend WebSocket foundation из TASK-084.`

## Итог

- `Локальная commit boundary TASK-084 закрыта commit-ом ecb5b96 (`TASK-084 EPIC-069: backend live-event WebSocket channel`).`
- `Targeted server verification принудительно rerun-нут: `./gradlew :server:test --rerun-tasks --tests 'com.bam.incomedy.server.lineup.EventLiveChannelRoutesTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'` завершился успешно; websocket regression coverage теперь включает rejection недоступного event channel-а.`
- `Recovery переключён на TASK-085; следующий bounded run должен продолжать shared/data realtime subscription contract, не перепрыгивая к Android/iOS wiring, staff/private channel, push fallback или durable outbox.`

## Возобновление

- `Если чат оборвется, сверить branch и git status, затем продолжить ровно с TASK-085/in_progress. Стартовать от уже доставленного backend WebSocket foundation и закрыть shared/data realtime subscription contract без расширения scope в Android/iOS wiring, staff/private channel, push fallback или durable outbox.`

## Если сессия оборвётся

- Check `git status`.
- Confirm branch is still `codex/epic-069-live-stage-realtime-delivery`.
- Treat active recovery state as `EPIC-069/TASK-085` in posture `in_progress`.
- Continue from the committed backend WebSocket foundation delivered in `ecb5b96`.
- Keep the scope limited to shared/data realtime subscription contract.
- Start from the delivered backend WebSocket foundation; do not reopen `TASK-084` unless a concrete regression is found.

## Следующий шаг

- `Ровно один следующий шаг после этого запуска: выполнить TASK-085 — shared/data realtime subscription contract для lineup live updates.`

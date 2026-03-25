# Очередь следующих epic

Короткая очередь следующих epic-ов после завершения review текущего active epic.

Используй этот файл, когда:

- текущий epic уже в `awaiting_user_review`;
- нужно быстро выбрать следующий `P0` epic без восстановления шага из backlog, current-state и task history;
- требуется зафиксировать порядок ближайших epic-ов.

## Текущая очередь

1. `EPIC-070` — donations/payout foundation
   - Status: `planned`
   - Why next: donations остаются следующим `P0` слоем после завершения текущего realtime epic-а.
2. `EPIC-071` — notifications / announcements delivery foundation
   - Status: `planned`
   - Why after EPIC-070: важный `P0` слой, но идёт после donations/payout foundation.

## Активированный epic

- `EPIC-069` — realtime/WebSocket delivery для live stage updates
  - Status: `awaiting_user_review`
  - Activated because: пользователь явно подтвердил review `EPIC-068`, а после completion всего ordered realtime plan этот epic теперь ждёт явного user confirmation перед продвижением очереди к `EPIC-070`.

## Правило

- Не начинай следующий epic из этого списка, пока пользователь явно не подтвердит review текущего epic.
- Если порядок меняется, синхронно обновляй этот файл, `00-current-state.md`, `task-request-log.md` и session memory.

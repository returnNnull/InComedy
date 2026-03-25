# Очередь следующих epic

Короткая очередь следующих epic-ов после завершения review текущего active epic.

Используй этот файл, когда:

- текущий epic уже в `awaiting_user_review`;
- нужно быстро выбрать следующий `P0` epic без восстановления шага из backlog, current-state и task history;
- требуется зафиксировать порядок ближайших epic-ов.

## Текущая очередь

1. `EPIC-069` — realtime/WebSocket delivery для live stage updates
   - Status: `planned`
   - Why next: это ближайший оставшийся `P0` gap после уже доставленного live-stage foundation и platform UI.
2. `EPIC-070` — donations/payout foundation
   - Status: `planned`
   - Why after EPIC-069: donations остаются `P0`, но зависят меньше от текущего live-stage continuity, чем realtime delivery.
3. `EPIC-071` — notifications / announcements delivery foundation
   - Status: `planned`
   - Why after EPIC-070: важный `P0` слой, но не следующий immediate gap после EPIC-068.

## Правило

- Не начинай следующий epic из этого списка, пока пользователь явно не подтвердит review текущего epic.
- Если порядок меняется, синхронно обновляй этот файл, `00-current-state.md`, `task-request-log.md` и session memory.

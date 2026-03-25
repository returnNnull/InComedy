# Очередь следующих epic

Короткая очередь следующих epic-ов после завершения review текущего active epic.

Используй этот файл, когда:

- review boundary предыдущего epic-а уже закрыт и нужно держать под рукой следующий `P0` кандидат;
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

- `none`
  - Last completed: `EPIC-069` явно подтверждён пользователем и смержен в `main` как завершённый realtime/WebSocket delivery slice.
  - Next candidate on future request: `EPIC-070` — donations/payout foundation.

## Правило

- Не начинай следующий epic из этого списка автоматически сразу после merge/push завершённого epic-а; нужен новый явный user request или отдельный automation step, который зафиксирует новый active epic.
- Если порядок меняется, синхронно обновляй этот файл, `00-current-state.md`, `task-request-log.md` и session memory.

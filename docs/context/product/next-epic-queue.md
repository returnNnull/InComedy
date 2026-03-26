# Очередь следующих epic

Короткая очередь следующих epic-ов после завершения review текущего active epic.

Используй этот файл, когда:

- review boundary предыдущего epic-а уже закрыт и нужно держать под рукой следующий `P0` кандидат;
- нужно быстро выбрать следующий `P0` epic без восстановления шага из backlog, current-state и task history;
- требуется зафиксировать порядок ближайших epic-ов.

## Текущая очередь

1. `EPIC-071` — notifications / announcements delivery foundation
   - Status: `in_progress`
   - Why next: важный `P0` слой уже активирован отдельным automation step-ом после закрытого donations/payout foundation.
2. `EPIC-072` — analytics foundation
   - Status: `planned`
   - Why after EPIC-071: полезный следующий `P0/P1` слой, но он не должен обгонять активный notifications epic.

## Активированный epic

- `EPIC-071`
  - Status: `in_progress`
  - Current next task: `TASK-092` — Android/iOS announcement/feed surfaces и executable verification без push-provider activation.
  - Last completed epic: `EPIC-070` явно подтверждён пользователем и смержен в `main` как завершённый donations/payout foundation slice.

## Правило

- Не начинай следующий epic из этого списка автоматически сразу после завершения `EPIC-071`; нужен новый явный user request или отдельный automation step, который зафиксирует новый active epic.
- Если порядок меняется, синхронно обновляй этот файл, `00-current-state.md`, `task-request-log.md` и session memory.

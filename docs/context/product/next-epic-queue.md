# Очередь следующих epic

Короткая очередь следующих epic-ов после завершения review текущего active epic.

Используй этот файл, когда:

- review boundary предыдущего epic-а уже закрыт и нужно держать под рукой следующий `P0` кандидат;
- нужно быстро выбрать следующий `P0` epic без восстановления шага из backlog, current-state и task history;
- требуется зафиксировать порядок ближайших epic-ов.

## Текущая очередь

1. `EPIC-072` — analytics foundation
   - Status: `planned`
   - Why next: ближайший `P0/P1` кандидат после закрытого notifications / announcements foundation.

## Активированный epic

- `none`
  - Last completed: `EPIC-071` явно подтверждён пользователем, review-driven follow-up закрыт и epic смержен в `main` как provider-agnostic notifications / announcements foundation slice.
  - Next candidate on future request: `EPIC-072` — analytics foundation.

## Правило

- Не начинай следующий epic из этого списка автоматически сразу после завершения `EPIC-071`; нужен новый явный user request или отдельный automation step, который зафиксирует новый active epic.
- Если порядок меняется, синхронно обновляй этот файл, `00-current-state.md`, `task-request-log.md` и session memory.

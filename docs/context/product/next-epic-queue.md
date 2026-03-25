# Очередь следующих epic

Короткая очередь следующих epic-ов после завершения review текущего active epic.

Используй этот файл, когда:

- review boundary предыдущего epic-а уже закрыт и нужно держать под рукой следующий `P0` кандидат;
- нужно быстро выбрать следующий `P0` epic без восстановления шага из backlog, current-state и task history;
- требуется зафиксировать порядок ближайших epic-ов.

## Текущая очередь

1. `EPIC-071` — notifications / announcements delivery foundation
   - Status: `planned`
   - Why next after active epic: важный `P0` слой, который идёт сразу после donations/payout foundation.
2. `EPIC-072` — analytics foundation
   - Status: `planned`
   - Why after EPIC-071: полезный следующий `P0/P1` слой, но он не должен обгонять notifications и активный donations epic.

## Активированный epic

- `EPIC-070`
  - Current state: `TASK-087` и `TASK-088` завершены на ветке `codex/epic-070-donations-payout-foundation`; следующий bounded step — `TASK-089`, Android/iOS donation и comedian payout surfaces с executable verification.
  - Guardrail: donations epic остаётся provider-agnostic/manual-settlement-ready и не трактует существующий ticketing PSP adapter как подтверждённый donation/payout provider.

## Правило

- Не начинай следующий epic из этого списка автоматически сразу после merge/push завершённого epic-а; нужен новый явный user request или отдельный automation step, который зафиксирует новый active epic.
- Если порядок меняется, синхронно обновляй этот файл, `00-current-state.md`, `task-request-log.md` и session memory.

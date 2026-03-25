# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-25T18:23:37+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-070`
- Active Subtask: `TASK-088`
- Branch: `codex/epic-070-donations-payout-foundation`
- Epic Status: `in_progress`
- Run Status: `completed`

## Цель

- `Завершить TASK-087: backend foundation для comedian payout profile и donation intents без активации конкретного PSP и синхронизировать активную governance memory под текущие executor rules.`

## Итог

- `TASK-087` завершён: доставлены `:domain:donations`, миграция `V15`, persistence и защищённые backend routes для payout profile self-service, donation intent create/list и verified-payout gate.`
- Handoff/governance ссылки синхронизированы с текущей семантикой счётчика запусков; `run_slots_used_in_cycle` остаётся только счётчиком фактических запусков текущего cycle.
- `EPIC-070` остаётся активным; следующий bounded шаг — `TASK-088`, shared/data transport integration для delivered backend foundation.`

## Возобновление

- `Если чат оборвется, продолжить только TASK-088 на текущей ветке; TASK-087 уже закрыт и должен переоткрываться только при regression.`

## Если сессия оборвётся

- Check `git status`.
- Keep `EPIC-069` closed; do not reopen it without a concrete regression or explicit follow-up request.
- Continue only `TASK-088` for `EPIC-070`; do not skip straight to platform UI or payout automation.
- Do not treat any existing ticketing PSP adapter or env config as confirmed donation/payout provider selection.
- Keep the delivered `manual_settlement` foundation provider-agnostic until explicit user confirmation of the external donation/payout path.

## Следующий шаг

- `Ровно один следующий шаг: TASK-088 — shared/data donation service contract и transport integration для payout profile, donation history и intent creation без platform UI и без выбора внешнего PSP.`

# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-25T20:06:58+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-070`
- Active Subtask: `TASK-089`
- Branch: `codex/epic-070-donations-payout-foundation`
- Epic Status: `in_progress`
- Run Status: `completed`

## Цель

- `Завершить TASK-088: shared/data donation service contract и transport integration для payout profile, donation history и intent creation без platform UI и без выбора внешнего PSP.`

## Итог

- `TASK-088` завершён: доставлены `:data:donations`, Ktor transport/DTO mapping для payout profile и donation history/create flows, shared Koin wiring и common tests поверх уже existing provider-agnostic backend foundation.`
- `EPIC-070` остаётся активным; следующий bounded шаг — `TASK-089`, Android/iOS donation и comedian payout surfaces с executable verification для delivered foundation.`

## Возобновление

- `Если чат оборвется, продолжить только TASK-089 на текущей ветке; TASK-087` и `TASK-088` уже закрыты и должны переоткрываться только при regression.`

## Если сессия оборвётся

- Check `git status`.
- Keep `EPIC-069` closed; do not reopen it without a concrete regression or explicit follow-up request.
- Continue only `TASK-089` for `EPIC-070`; do not skip straight to external checkout, webhook automation, or payout automation.
- Do not treat any existing ticketing PSP adapter or env config as confirmed donation/payout provider selection.
- Keep the delivered `manual_settlement` foundation provider-agnostic until explicit user confirmation of the external donation/payout path.

## Следующий шаг

- `Ровно один следующий шаг: TASK-089 — Android/iOS donation и comedian payout surfaces с executable verification для delivered foundation без выбора внешнего PSP.`

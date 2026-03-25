# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-25T16:29:41+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-069`
- Active Subtask: `TASK-084`
- Branch: `codex/epic-069-live-stage-realtime-delivery`
- Epic Status: `in_progress`
- Run Status: `ready_to_commit`

## Цель

- `Закрыть local commit boundary для уже завершённого TASK-084; только после локального commit переключить recovery на TASK-085 — shared/data realtime subscription contract для lineup live updates.`

## Итог

- `User confirmation по EPIC-068 получен, поэтому epic закрыт как done и работа продолжена без review boundary.`
- `TASK-084 завершён: backend live-event channel `/ws/events/{eventId}` доставлен вместе с initial snapshot, audience-safe realtime payload-ами, server-local broadcaster и publish hooks из approve/reorder/live-state path-ов.`
- `Targeted server verification green и docs sync завершён, но локальный commit для TASK-084 ещё не создан, поэтому recovery остаётся на commit boundary этого task-а в статусе ready_to_commit.`
- `Принят D-079: dirty worktree после completed/docs_only task считается незакрытой commit boundary; до локального commit recovery нельзя переключать на новый TASK.`

## Возобновление

- `Если чат оборвется, сначала сверить recovery docs с git status: текущий активный checkpoint остаётся на TASK-084/ready_to_commit. Нужно сначала создать локальный commit для TASK-084 и только потом переключать recovery на TASK-085. После этого не перепрыгивать к Android/iOS wiring, staff channel, push fallback или durable outbox, пока shared/data слой не закрыт.`

## Если сессия оборвётся

- Check `git status`.
- Confirm branch is still `codex/epic-069-live-stage-realtime-delivery`.
- Treat active recovery state as `EPIC-069/TASK-084` in posture `ready_to_commit`.
- Close the local commit boundary for `TASK-084` before any new implementation step.
- Start from the delivered backend WebSocket foundation; do not reopen `TASK-084` unless a concrete regression is found.

## Следующий шаг

- `Ровно один следующий шаг после этого запуска: сначала создать локальный commit для TASK-084, затем переключить active recovery на TASK-085 — shared/data realtime subscription contract для lineup live updates.`

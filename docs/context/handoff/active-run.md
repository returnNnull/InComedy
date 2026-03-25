# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-25T15:23:45+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-068`
- Active Subtask: `TASK-083`
- Branch: `codex/epic-068-live-stage-status-foundation`
- Epic Status: `awaiting_user_review`
- Run Status: `docs_only`

## Цель

- `Рефакторинг структуры docs/context для ускорения bootstrap: разделить checklist/policy/status/history и убрать лишний контекст из snapshot/recovery файлов.`

## Итог

- `Созданы отдельные executor checklist/policy, implementation-status, verification-memory и next-epic-queue.`
- `00-current-state.md` сжат до bootstrap snapshot; active-run.md сжат до recovery checkpoint.`
- `test-strategy.md` и `architecture-overview.md` очищены от delivery/verification history в пользу новых специализированных документов.`
- `issue-resolution-log.md` получил symptom index.`
- `EPIC-068` остаётся в awaiting_user_review; product scope не менялся.`

## Возобновление

- `Если чат оборвется, не выбирать новую product-задачу: EPIC-068 уже завершён технически и ждёт user review. Оставаться на той же ветке и не стартовать новый epic, пока пользователь явно не подтвердит review. Для повторяющихся blocker-ов сначала смотреть issue log; для Xcode/simulator симптомов первым делом запускать или перезапускать Xcode.`

## Если сессия оборвётся

- Check `git status`.
- Confirm branch is still `codex/epic-068-live-stage-status-foundation`.
- Treat active recovery state as `EPIC-068 awaiting_user_review`; do not pick a new epic or task.
- Keep the branch checked out and wait for explicit user confirmation before moving to the next `P0` epic.

## Следующий шаг

- `Ровно одна следующая подзадача после этого запуска: получить явное user review/confirmation по EPIC-068; только после этого выбирать следующий P0 epic (первый кандидат — realtime/WebSocket delivery для live stage updates).`

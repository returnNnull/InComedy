# Активный запуск

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Используй этот файл как короткий overwrite-only snapshot.
Не добавляй сюда историю. Исторический контекст хранится в:

- `../governance/session-log.md`
- `task-request-log.md`

## Снимок

- Timestamp: `2026-03-26T18:18:08+03:00`
- Cycle ID: `2026-03-24-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-071`
- Active Subtask: `TASK-091`
- Branch: `codex/epic-071-notifications-announcements-delivery-foundation`
- Epic Status: `in_progress`
- Run Status: `completed`

## Цель

- Открыть `EPIC-071` отдельным automation step-ом и закрыть ровно один bounded backend-first шаг: `TASK-090` для organizer announcements/event feed foundation без `/me/notifications` и без выбора push provider.

## Итог

- `TASK-090` delivered `:domain:notifications`, migration `V16__event_announcements_foundation.sql`, `AnnouncementRepository`/`PostgresAnnouncementRepository`, public `GET /api/v1/public/events/{eventId}/announcements` and protected `POST /api/v1/events/{eventId}/announcements` routes, plus `announcement.created` fanout on public `/ws/events/{eventId}`.
- Existing lineup consumer compatibility preserved: `data/lineup` transport now ignores unsupported live-event types, so public channel expansion to `announcement.created` does not break current lineup subscribers.
- Verification is green after local repair of Gradle/Kotlin cache collisions via `./gradlew --stop`, cache cleanup and sequential `--no-daemon --max-workers=1` reruns; active residual risk now also includes `R-014` for incomplete notifications rollout scope.

## Возобновление

- Если чат оборвется, сверить branch и `git status`. Если локальная commit boundary этого run уже закрыта, продолжать `TASK-091` на той же ветке; если worktree еще dirty, сначала завершить docs sync + local commit boundary `TASK-090`, а не стартовать новую подзадачу поверх него.

## Если сессия оборвётся

- Check `git status`.
- Keep `EPIC-071` active on `codex/epic-071-notifications-announcements-delivery-foundation`.
- Do not widen scope from `TASK-091` into Android/iOS surfaces, `/api/v1/me/notifications`, moderation or push-provider activation in the same follow-up without updating the recorded plan.
- Do not treat any FCM/APNs code, config example, or future SDK stub as confirmed push-provider selection.
- If Gradle verification shows `Storage ... already registered`, `CorruptedException`, or Kotlin daemon cache collisions, reuse `I-002` instead of repeating parallel Gradle runs.

## Следующий шаг

- Ровно одна следующая подзадача: `TASK-091` — shared/data announcement service contract и transport integration для public event feed без platform UI и без push-provider activation.

# Decisions Log Part 08

## D-082

- Date: 2026-03-26
- Status: accepted
- Decision: `EPIC-071` должен стартовать provider-agnostic backend foundation для organizer announcements/event feed: первый delivery шаг может добавить публичный announcement feed, protected organizer publish route и audience-safe `announcement.created` payload в существующий public live-event channel, но не должен одновременно внедрять `/api/v1/me/notifications`, device-token registration, background delivery или трактовать FCM/APNs как подтверждённый push provider.
- Rationale: После закрытия `EPIC-070` ближайшим `P0` gap стал communication layer, однако стек push providers в `tooling-stack.md` по-прежнему находится только в candidate state и не подтверждён пользователем. Если начать epic сразу с provider-specific/background delivery, один bounded run смешает product communication surface, provider choice и mobile integration в слишком широкий scope. Backend-first organizer announcements/feed foundation даёт immediate product value, расширяет уже существующий public `/ws/events/{eventId}` channel и не нарушает правило explicit confirmation для внешних push providers.
- Consequences: Первый bounded step `TASK-090` добавляет только `:domain:notifications`, migration-backed `event_announcements` persistence, public `GET /api/v1/public/events/{eventId}/announcements`, protected `POST /api/v1/events/{eventId}/announcements`, audience-safe `announcement.created` live payload и compatibility hardening для существующего lineup transport-а; `risk-log.md` должен открыть `R-014`, а следующие `TASK-091`/`TASK-092` обязаны продолжать shared/data и Android/iOS feed layers без активации FCM/APNs до отдельного user confirmation.

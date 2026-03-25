# Task Request Template Part 36

## Active Request (EPIC-069 Realtime Delivery Kickoff)

### Epic

- `EPIC-069` — realtime/WebSocket delivery для live stage updates.

### Status

- `in_progress`

### Why Now

- Пользователь явно подтвердил review `EPIC-068`, значит прежний review boundary снят и можно двигаться к следующему `P0`.
- По `product/next-epic-queue.md` и `product/backlog.md` ближайший приоритет после live-stage foundation — realtime delivery для live stage updates.

### Ordered Subtask Plan

1. `TASK-084` — backend WebSocket live-event channel `/ws/events/{eventId}` для audience-safe lineup/live-stage updates, включая event envelope, server-local broadcaster и route coverage.
   - Status: `completed`
2. `TASK-085` — shared/data realtime subscription contract и transport integration для lineup live updates в KMP-слоях.
   - Status: `in_progress`
3. `TASK-086` — Android/iOS wiring на новый realtime feed и executable verification delivered live-update behavior.
   - Status: `planned`

### Scope Rules

- Этот epic покрывает только realtime/WebSocket delivery live-stage/update потока.
- `sales.status_changed`, `inventory.changed`, `announcement.created`, staff channel и push fallback не входят в `TASK-084`; их можно планировать только отдельными следующими bounded шагами.
- Первый delivery шаг может использовать server-local in-memory broadcaster внутри одного backend process-а; durable outbox/multi-instance fanout остаются последующим расширением, а не частью текущего bounded run.

### Current Next

- `Ровно одна следующая продуктовая подзадача: TASK-085 — shared/data realtime subscription contract для lineup live updates, без Android/iOS wiring в том же bounded шаге.`

### Current Recovery State

- `Локальная commit boundary TASK-084 закрыта commit-ом ecb5b96, поэтому active recovery checkpoint переключён на TASK-085 в статусе in_progress.`

### Recovery Guardrail

- `Если новая сессия увидит dirty worktree после already-completed TASK-084 или другой completed/docs_only подзадачи, она обязана сначала закрыть local commit boundary и только потом продолжать TASK-085. Переключать active recovery на новый TASK до commit нельзя.`

## Implementation Outcome (EPIC-069 TASK-084 Backend Live Event WebSocket Channel)

### Epic

- `EPIC-069` — realtime/WebSocket delivery для live stage updates.

### Task

- `TASK-084` — backend WebSocket live-event channel `/ws/events/{eventId}` для audience-safe lineup/live-stage updates.

### Status

- `completed`

### Delivered

- Добавлен public WebSocket route `/ws/events/{eventId}` для published public events.
- Route сразу отдает initial snapshot и затем стримит audience-safe envelopes двух типов:
  - `lineup.changed`
  - `stage.current_changed`
- Добавлен server-local in-memory broadcaster, который публикует события внутри текущего backend process-а без durable outbox/multi-instance fanout.
- Publish hooks встроены в три mutation path-а:
  - approve comedian application -> `lineup.changed`
  - lineup reorder -> `lineup.changed`
  - live-stage status update -> `lineup.changed` + `stage.current_changed`
- Payload сознательно ограничен audience-safe summary:
  - `comedian_display_name`
  - `order_index`
  - `status`
  - `current_performer`
  - `next_up`
- JSON payload стабилизирован через explicit default fields, чтобы пустой snapshot и последующие сообщения имели детерминированную форму.

### Verification

- `Passed: ./gradlew :server:test --tests 'com.bam.incomedy.server.lineup.EventLiveChannelRoutesTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`
- `Passed: ./gradlew :server:test --rerun-tasks --tests 'com.bam.incomedy.server.lineup.EventLiveChannelRoutesTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'` (forced rerun после добавления regression coverage для rejection недоступного event channel-а)

### Notes

- Принят `D-078`: transport choice зафиксирован как `Ktor WebSockets`, а первый realtime шаг intentionally ограничен single-process broadcaster.
- Security review verdict: новый public WebSocket surface доступен только для published public events, rate-limited по peer fingerprint и не экспортирует usernames/application ids/tokens или другой internal organizer payload.

### Next

- `Ровно одна следующая продуктовая подзадача — TASK-085: shared/data realtime subscription contract для lineup live updates, без Android/iOS wiring в том же bounded шаге.`

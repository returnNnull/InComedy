# Task Request Template Part 36

## Active Request (EPIC-069 Realtime Delivery Kickoff)

### Epic

- `EPIC-069` — realtime/WebSocket delivery для live stage updates.

### Status

- `awaiting_user_review`

### Why Now

- Пользователь явно подтвердил review `EPIC-068`, значит прежний review boundary снят и можно двигаться к следующему `P0`.
- По `product/next-epic-queue.md` и `product/backlog.md` ближайший приоритет после live-stage foundation — realtime delivery для live stage updates.

### Ordered Subtask Plan

1. `TASK-084` — backend WebSocket live-event channel `/ws/events/{eventId}` для audience-safe lineup/live-stage updates, включая event envelope, server-local broadcaster и route coverage.
   - Status: `completed`
2. `TASK-085` — shared/data realtime subscription contract и transport integration для lineup live updates в KMP-слоях.
   - Status: `completed`
3. `TASK-086` — Android/iOS wiring на новый realtime feed и executable verification delivered live-update behavior.
   - Status: `completed`

### Scope Rules

- Этот epic покрывает только realtime/WebSocket delivery live-stage/update потока.
- `sales.status_changed`, `inventory.changed`, `announcement.created`, staff channel и push fallback не входят в `TASK-084`; их можно планировать только отдельными следующими bounded шагами.
- Первый delivery шаг может использовать server-local in-memory broadcaster внутри одного backend process-а; durable outbox/multi-instance fanout остаются последующим расширением, а не частью текущего bounded run.

### Current Next

- `Ordered plan полностью выполнен; ровно один следующий шаг — дождаться явного user review/confirmation по EPIC-069 и не открывать новый epic до этого подтверждения.`

### Current Recovery State

- `Локальная commit boundary TASK-086 закрыта текущим локальным commit-ом; после completion всего ordered plan epic удерживается в posture awaiting_user_review.`

### Recovery Guardrail

- `Если новая сессия возобновит этот epic, она обязана сохранить review boundary EPIC-069 и не открывать новый epic/task без explicit user confirmation. Если обнаружится dirty worktree после completed/docs_only подзадачи, сначала нужно закрыть local commit boundary.`

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

## Implementation Outcome (EPIC-069 TASK-085 Shared/Data Realtime Subscription Contract)

### Epic

- `EPIC-069` — realtime/WebSocket delivery для live stage updates.

### Task

- `TASK-085` — shared/data realtime subscription contract и transport integration для lineup live updates в KMP-слоях.

### Status

- `completed`

### Delivered

- В `:domain:lineup` добавлен отдельный public live-update contract:
  - `LineupLiveUpdate`
  - `LineupLiveUpdateType`
  - `LineupLiveSummary`
  - `LineupLiveEntry`
- `LineupManagementService` получил `observeEventLiveUpdates(eventId)` как transport-agnostic KMP seam для подписки на published public event channel без access token.
- `:data:lineup` теперь содержит Ktor WebSocket transport adapter, который:
  - строит `ws/wss` URL из текущего backend base URL;
  - читает text frames public `/ws/events/{eventId}` feed-а;
  - поднимает явную ошибку при non-normal close reason от backend-а.
- JSON envelope `lineup.changed` / `stage.current_changed` маппится в новые доменные модели без утечки transport DTO за пределы data-слоя.
- Добавлен test seam для realtime transport-а, чтобы KMP data tests могли проверять contract mapping без реального сокета.

### Verification

- `Passed: ./gradlew :data:lineup:allTests :feature:lineup:allTests :shared:compileKotlinMetadata :composeApp:compileDebugKotlin`

### Notes

- Security review verdict: новый client-side realtime surface остаётся read-only и public-only, не использует access token и продолжает потреблять только audience-safe payload published public event channel-а без organizer/private данных.
- Android/iOS lifecycle wiring, runtime subscription start/stop и executable platform verification намеренно оставлены следующей bounded подзадаче `TASK-086`.

### Next

- `Ровно одна следующая продуктовая подзадача — TASK-086: Android/iOS wiring на новый realtime feed и executable verification delivered live-update behavior, без staff/private channel, push fallback или durable outbox в том же bounded шаге.`

# Task Request Template Part 32

## Implementation Outcome (EPIC-068 Shared/Data/Feature Live Stage Integration)

### Epic

- `EPIC-068` — live stage status foundation.

### Task

- `TASK-072` — shared/data/feature integration для live-stage read/write semantics и общего экспорта lineup live-state статусов.

### Status

- `completed`

### Delivered

- Domain lineup contract now supports the full live-stage status set `draft/up_next/on_stage/done/delayed/dropped` and exposes `LineupManagementService.updateLineupEntryStatus(...)` for a bounded shared mutation path.
- `LineupBackendApi` and `BackendLineupManagementService` now map all lineup live-state statuses and call backend `POST /api/v1/events/{eventId}/lineup/live-state` without leaking transport details into shared/domain layers.
- `LineupViewModel`, `LineupIntent`, and `LineupState` now support live-stage read/write semantics in the shared feature layer: organizer state can derive `current performer` / `next up`, and live-stage mutations update the lineup state without introducing platform UI code.
- `LineupBridge` and `LineupStateSnapshot` now export current/next performer summary plus a shared live-stage mutation entry point for later Android/iOS UI wiring.
- Bootstrap, backlog, architecture, test strategy, session/task memory, and recovery checkpoint are synchronized so the next bounded step is `TASK-073`, not another shared-layer slice.

### Verification

- `Passed: ./gradlew :feature:lineup:allTests :data:lineup:compileKotlinMetadata :shared:compileKotlinMetadata :composeApp:compileDebugKotlin --console=plain`

### Notes

- Android/iOS UI wiring, WebSocket delivery, push notifications, and announcements/feed remain out of scope for this subtask.
- No new governance decision was needed; the result continues existing `D-046` MVP prioritization and `D-068` bounded-task completion rules.

### Next

- `Ровно одна следующая подзадача: TASK-073 — Android/iOS UI wiring для current performer / next up / organizer live controls поверх уже доставленного shared live-stage foundation, без realtime/WebSocket delivery.`

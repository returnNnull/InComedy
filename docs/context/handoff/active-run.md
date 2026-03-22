# Active Run

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Use this file as a short overwrite-only state snapshot.
Do not append history here. Historical context belongs in:

- `../governance/session-log.md`
- `task-request-log.md`

## Current State

- Timestamp: `2026-03-23T00:08:35+03:00`
- Cycle ID: `2026-03-22-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-067`
- Active Subtask: `TASK-069`
- Branch: `codex/epic-067-comedian-applications-foundation`
- Epic Status: `in_progress`
- Run Status: `completed`

## Goal

- `Продвинуть EPIC-067 по одному безопасному шагу за запуск. В этом запуске завершен TASK-068: backend lineup foundation поверх approved applications с explicit order_index и organizer reorder API.`

## Current Outcome

- `TASK-068 completed: добавлены migration V14, lineup persistence/service/routes, idempotent approved->draft lineup entry bridge, organizer/host lineup list+reorder API, OpenAPI/context sync и targeted server regression coverage.`

## Files Touched

- `docs/context/00-current-state.md`
- `docs/context/engineering/api-contracts/v1/openapi.yaml`
- `docs/context/engineering/architecture-overview.md`
- `docs/context/engineering/test-strategy.md`
- `docs/context/governance/decision-traceability/decision-traceability-part-05.md`
- `docs/context/governance/decisions-log/decisions-log-part-05.md`
- `docs/context/governance/session-log/session-log-part-17.md`
- `docs/context/handoff/active-run.md`
- `docs/context/handoff/task-request-template/task-request-template-part-29.md`
- `server/src/main/kotlin/com/bam/incomedy/server/Application.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/db/LineupRepository.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/db/PostgresLineupRepository.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsRoutes.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsService.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/LineupApiModels.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/LineupRoutes.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/LineupService.kt`
- `server/src/main/resources/db/migration/V14__lineup_entries_foundation.sql`
- `server/src/test/kotlin/com/bam/incomedy/server/db/DatabaseMigrationRunnerTest.kt`
- `server/src/test/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsRoutesTest.kt`
- `server/src/test/kotlin/com/bam/incomedy/server/support/InMemoryLineupRepository.kt`

## Verification

- `./gradlew :server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`

## Uncommitted Changes Expected

- `yes`

## Last Safe Checkpoint

- `Verification green; docs/context synchronized to completed TASK-068 and next TASK-069.`

## Resume From

- `Начать TASK-069 в этой же ветке: shared/data/feature integration для organizer/comedian applications + lineup surfaces поверх готового backend foundation.`

## If Crash

- Check `git status`.
- Check the current branch.
- Compare repository state with this file and `../00-current-state.md`.
- Continue the same `Active Epic` and `Active Subtask` until the state becomes consistent.

## Next

- `Ровно одна следующая подзадача: TASK-069 — shared/data/feature integration для comedian applications и lineup surfaces без Android/iOS UI wiring в этом же запуске.`

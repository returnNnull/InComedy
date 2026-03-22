# Active Run

Crash-safe recovery checkpoint for the current automation run or the latest interrupted chat.

Use this file as a short overwrite-only state snapshot.
Do not append history here. Historical context belongs in:

- `../governance/session-log.md`
- `task-request-log.md`

## Current State

- Timestamp: `2026-03-22T22:12:30+03:00`
- Cycle ID: `2026-03-22-10-04`
- Cycle Window: `10:00-04:00 Europe/Moscow`
- Active Epic: `EPIC-067`
- Active Subtask: `TASK-068`
- Branch: `codex/epic-067-comedian-applications-foundation`
- Epic Status: `in_progress`
- Run Status: `completed`

## Goal

- `Продвинуть EPIC-067 по одному безопасному шагу за запуск. В этом запуске закрыт TASK-067: backend foundation для заявок комиков.`

## Current Outcome

- `TASK-067 completed: добавлены migration V13, persistence/service/routes для comedian applications, API-contract запись и server regression coverage.`

## Files Touched

- `docs/context/00-current-state.md`
- `docs/context/engineering/api-contracts/v1/openapi.yaml`
- `docs/context/engineering/architecture-overview.md`
- `docs/context/engineering/test-strategy.md`
- `docs/context/governance/decision-traceability/decision-traceability-part-05.md`
- `docs/context/governance/session-log/session-log-part-17.md`
- `docs/context/handoff/active-run.md`
- `docs/context/handoff/task-request-template/task-request-template-part-29.md`
- `server/src/main/kotlin/com/bam/incomedy/server/Application.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/db/ComedianApplicationRepository.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/db/PostgresComedianApplicationRepository.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsApiModels.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsRoutes.kt`
- `server/src/main/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsService.kt`
- `server/src/main/resources/db/migration/V13__comedian_applications_foundation.sql`
- `server/src/test/kotlin/com/bam/incomedy/server/db/DatabaseMigrationRunnerTest.kt`
- `server/src/test/kotlin/com/bam/incomedy/server/lineup/ComedianApplicationsRoutesTest.kt`
- `server/src/test/kotlin/com/bam/incomedy/server/support/InMemoryComedianApplicationRepository.kt`

## Verification

- `./gradlew :server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.lineup.ComedianApplicationsRoutesTest'`

## Uncommitted Changes Expected

- `no`

## Last Safe Checkpoint

- `Verification green; docs/context synchronized to completed TASK-067 and next TASK-068.`

## Resume From

- `Начать TASK-068 в этой же ветке: lineup-entry foundation поверх approved applications без захвата UI в этом же запуске.`

## If Crash

- Check `git status`.
- Check the current branch.
- Compare repository state with this file and `../00-current-state.md`.
- Continue the same `Active Epic` and `Active Subtask` until the state becomes consistent.

## Next

- `Ровно одна следующая подзадача: TASK-068 — добавить lineup-entry persistence и organizer reorder foundation, чтобы approved заявка могла становиться lineup draft entry с явным order index.`

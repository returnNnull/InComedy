# Current State Snapshot

Updated: `2026-03-25`

```yaml
AutomationState:
  cycle_id: "2026-03-24-10-04"
  cycle_window: "10:00-04:00 Europe/Moscow"
  active_epic_id: "EPIC-068"
  active_subtask_id: "TASK-082"
  active_branch: "codex/epic-068-live-stage-status-foundation"
  epic_status: "awaiting_user_review"
  run_slots_used_in_cycle: 12
  last_run_at: "2026-03-25T15:18:34+03:00"
  last_run_result: "docs_only"
```

If `handoff/active-run.md` exists, read it first as the crash-recovery checkpoint.
Use this file as the bootstrap entry point for every new chat/session after that recovery check and before reading deeper context docs.

## Source of Truth

- `docs/context/*` is the operational source of truth for ongoing work.
- `docs/standup-platform-ru/*` is the detailed target-state specification layer.
- Repository code is the source of truth for the exact current implementation surface and constraints.

## Current Snapshot

- Latest accepted decision: `D-076`
- Latest decisions part: `governance/decisions-log/decisions-log-part-06.md`
- Latest session-log part: `governance/session-log/session-log-part-21.md`
- Latest decision-traceability part: `governance/decision-traceability/decision-traceability-part-06.md`
- Latest task-request log part: `handoff/task-request-template/task-request-template-part-35.md`
- Active auth baseline: `login + password` plus `VK ID`
- Current `P0` focus: provider-agnostic MVP delivery remains the active product direction. `EPIC-068` больше не заблокирован средой: повторный rerun на `2026-03-25 15:10-15:15 MSK` показал, что `xcrun simctl list devices available` и `xcodebuild -showdestinations` снова видят реальные simulator destinations, а targeted iOS XCUITest `testLineupTabShowsApplicationsAndReorderSurface` на `iPhone 17 Pro (iOS 26.2)` прошёл успешно. Это закрыло `TASK-073`, поэтому весь `EPIC-068` переведён в `awaiting_user_review`.
- Current next bounded step: провести review `EPIC-068` и получить явное user confirmation перед переходом к следующему `P0` epic; после review ближайшим продуктовым кандидатом остаётся realtime/WebSocket delivery для live stage updates

## Active Constraints

- New and materially changed repository code must keep required comments in Russian.
- Backend production-significant flow diagnostics must use the sanitized diagnostics system rather than ad-hoc console logging.
- External provider choices must not be treated as active/default/confirmed without explicit user confirmation; assistant inference, existing code, and example config/docs do not count as approval.
- `docs/context/handoff/context-protocol.md` is the general cross-chat bootstrap checklist; no separate bootstrap-template file is maintained.
- `docs/context/handoff/automation-executor-prompt.md` is the mandatory runbook for `InComedy Executor` automations, and the automation TOML prompt should reference that document instead of duplicating long process rules inline.
- `AutomationState.run_slots_used_in_cycle` tracks how many executor launches were used in the current cycle.
- Verification/test-runtime issues discovered while closing the active task must be solved inside that same task by default; для `TASK-073` этот цикл завершён успешным targeted iOS XCUITest rerun на текущем host.
- Active epic execution should follow a documented ordered subtask plan; new product subtasks or plan reordering require an explicit update in task/governance memory before implementation continues.
- New and materially updated project documentation in `docs/context/*`, `docs/README.md`, and adjacent governance/handoff indexes must be written in Russian; legacy English fragments are normalized when the touched document is updated.
- Repeated technical problems and confirmed repair paths must be recorded in `engineering/issue-resolution-log.md` so future runs can resume troubleshooting without starting from zero.
- Перед новой диагностикой blocker-а сначала нужно проверять `engineering/issue-resolution-log.md` на уже существующие записи с теми же симптомами и использовать их repair path.
- Для iOS simulator / Xcode destination проблем первым repair step нужно считать запуск Xcode или его перезапуск, если приложение зависло.
- Every meaningful task, including executor governance/docs-only sync, requires a proportional mandatory security review before closure; if the change is docs/process only, the zero-impact security verdict must still be recorded explicitly.
- После перевода epic в `awaiting_user_review` новый epic не должен начинаться, пока пользователь явно не подтвердит review текущего результата.
- Changes that move scope, decisions, current focus, next step, or active constraints must update `docs/context/*` in the same change.

## Required Read Path After This File

1. `product/product-brief.md`
2. `product/backlog.md`
3. `engineering/tooling-stack.md`
4. `engineering/engineering-standards.md`
5. `engineering/quality-rules.md`
6. `product/non-functional-requirements.md`
7. `engineering/architecture-overview.md`
8. `engineering/test-strategy.md`
9. `governance/decisions-log.md` plus the latest part listed above
10. `governance/session-log.md` plus the latest part listed above
11. `governance/decision-traceability.md` plus the latest part listed above
12. `../standup-platform-ru/README.md` and relevant detailed spec files when the task needs domain/product clarification

## Where To Write

- Major task structure: `handoff/task-request-template.md`
- Historical formalized requests and outcomes: `handoff/task-request-log.md`
- Crash-safe active run checkpoint: `handoff/active-run.md`
- Automation executor runbook: `handoff/automation-executor-prompt.md`
- Per-session analytical memory: `governance/session-log.md`
- Production diagnostics workflow: `engineering/server-diagnostics-runbook.md`
- Repeated technical problems and repair paths: `engineering/issue-resolution-log.md`

## Update Rule

- Update this file whenever the latest decision id, current `P0` focus, next step, latest relevant part files, or active cross-cutting constraints change.

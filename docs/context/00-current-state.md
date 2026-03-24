# Current State Snapshot

Updated: `2026-03-24`

```yaml
AutomationState:
  cycle_id: "2026-03-22-10-04"
  cycle_window: "10:00-04:00 Europe/Moscow"
  active_epic_id: ""
  active_subtask_id: ""
  active_branch: "main"
  epic_status: "done"
  completed_subtasks_in_cycle:
    - "TASK-067"
    - "TASK-068"
    - "TASK-069"
    - "TASK-070"
  last_run_at: "2026-03-24T14:27:47+03:00"
  last_run_result: "done"
```

If `handoff/active-run.md` exists, read it first as the crash-recovery checkpoint.
Use this file as the bootstrap entry point for every new chat/session after that recovery check and before reading deeper context docs.

## Source of Truth

- `docs/context/*` is the operational source of truth for ongoing work.
- `docs/standup-platform-ru/*` is the detailed target-state specification layer.
- Repository code is the source of truth for the exact current implementation surface and constraints.

## Current Snapshot

- Latest accepted decision: `D-068`
- Latest decisions part: `governance/decisions-log/decisions-log-part-05.md`
- Latest session-log part: `governance/session-log/session-log-part-17.md`
- Latest decision-traceability part: `governance/decision-traceability/decision-traceability-part-05.md`
- Latest task-request log part: `handoff/task-request-template/task-request-template-part-31.md`
- Active auth baseline: `login + password` plus `VK ID`
- Current `P0` focus: provider-agnostic MVP delivery remains the active product direction, and `EPIC-067` is now explicitly completed after user review confirmation. Backend comedian applications foundation, shared KMP contracts, Android Compose lineup tab, iOS SwiftUI lineup tab, repo-side Xcode/KMP bridge hardening, and targeted executable verification are fully delivered end-to-end. The final targeted iOS verification for `testLineupTabShowsApplicationsAndReorderSurface` succeeded on `iPhone 17 Pro (iOS 26.2)`, so this epic no longer stays active in delivery memory
- Current next bounded step: after merge/push of branch `codex/epic-067-comedian-applications-foundation`, choose the next highest-priority unfinished epic from `product/backlog.md` and `handoff/task-request-log.md`; do not reopen `EPIC-067` unless a post-merge regression or explicit follow-up request appears

## Active Constraints

- New and materially changed repository code must keep required comments in Russian.
- Backend production-significant flow diagnostics must use the sanitized diagnostics system rather than ad-hoc console logging.
- External provider choices must not be treated as active/default/confirmed without explicit user confirmation; assistant inference, existing code, and example config/docs do not count as approval.
- Verification/test-runtime issues discovered while closing the active task must be solved inside that same task by default; they may leave the task `partial`, but they do not justify taking a new task unless a true external blocker or explicit user-decision boundary appears.
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
- Per-session analytical memory: `governance/session-log.md`
- Production diagnostics workflow: `engineering/server-diagnostics-runbook.md`

## Update Rule

- Update this file whenever the latest decision id, current `P0` focus, next step, latest relevant part files, or active cross-cutting constraints change.

# Current State Snapshot

Updated: `2026-03-25`

```yaml
AutomationState:
  cycle_id: "2026-03-24-10-04"
  cycle_window: "10:00-04:00 Europe/Moscow"
  active_epic_id: "EPIC-068"
  active_subtask_id: "TASK-073"
  active_branch: "codex/epic-068-live-stage-status-foundation"
  epic_status: "blocked_external"
  run_slots_used_in_cycle: 6
  last_run_at: "2026-03-25T13:19:29+03:00"
  last_run_result: "docs_only"
```

If `handoff/active-run.md` exists, read it first as the crash-recovery checkpoint.
Use this file as the bootstrap entry point for every new chat/session after that recovery check and before reading deeper context docs.

## Source of Truth

- `docs/context/*` is the operational source of truth for ongoing work.
- `docs/standup-platform-ru/*` is the detailed target-state specification layer.
- Repository code is the source of truth for the exact current implementation surface and constraints.

## Current Snapshot

- Latest accepted decision: `D-071`
- Latest decisions part: `governance/decisions-log/decisions-log-part-05.md`
- Latest session-log part: `governance/session-log/session-log-part-20.md`
- Latest decision-traceability part: `governance/decision-traceability/decision-traceability-part-06.md`
- Latest task-request log part: `handoff/task-request-template/task-request-template-part-33.md`
- Active auth baseline: `login + password` plus `VK ID`
- Current `P0` focus: provider-agnostic MVP delivery remains the active product direction, and `EPIC-068` stays the active epic after the already merged `EPIC-067`. Android/iOS lineup live-stage UI wiring is implemented on top of the delivered shared foundation; `iosApp/scripts/build-shared.sh` now bootstrap-s a repo-local Kotlin/Native bundle for Xcode, and the remaining SwiftUI `#Preview` macro blocks were replaced with `PreviewProvider` fallbacks so the generic iOS simulator build is green in this sandbox. After the 2026-03-25 13:18-13:19 MSK rerun, the active `TASK-073` is now blocked externally on this host because `CoreSimulatorService` still cannot provide a usable simulator device set for the targeted iOS XCUITest
- Current next bounded step: continue the same `TASK-073` in `codex/epic-068-live-stage-status-foundation` by re-running the targeted iOS lineup XCUITest verification for the live-stage controls on an unrestricted local host with working `CoreSimulatorService` / simulator device set, while keeping WebSocket delivery, push, announcements, and any next epic selection out of scope

## Active Constraints

- New and materially changed repository code must keep required comments in Russian.
- Backend production-significant flow diagnostics must use the sanitized diagnostics system rather than ad-hoc console logging.
- External provider choices must not be treated as active/default/confirmed without explicit user confirmation; assistant inference, existing code, and example config/docs do not count as approval.
- `docs/context/handoff/context-protocol.md` is the general cross-chat bootstrap checklist; no separate bootstrap-template file is maintained.
- `docs/context/handoff/automation-executor-prompt.md` is the mandatory runbook for `InComedy Executor` automations, and the automation TOML prompt should reference that document instead of duplicating long process rules inline.
- `AutomationState.run_slots_used_in_cycle` tracks used execution slots in the current daily cycle; the 10-slot limit applies to automation runs, not only to completed subtasks.
- Verification/test-runtime issues discovered while closing the active task must be solved inside that same task by default; new automation runs must not finish with `partial`, legacy `awaiting_verification` should be treated as `verifying`, and any legacy `partial` recovery state should be normalized into a finished `docs_only` blocker verdict or a completed outcome when the same task is resumed.
- Every meaningful task, including executor governance/docs-only sync, requires a proportional mandatory security review before closure; if the change is docs/process only, the zero-impact security verdict must still be recorded explicitly.
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

## Update Rule

- Update this file whenever the latest decision id, current `P0` focus, next step, latest relevant part files, or active cross-cutting constraints change.

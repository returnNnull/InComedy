# Context Protocol

This protocol defines how to use and transfer project context between chats.

## Source of Truth

- The folder `docs/context/` is the single source of truth for product and engineering context.
- `handoff/automation-executor-prompt.md` is the mandatory runbook for scheduled `InComedy Executor` automations; automation TOML prompts should reference it instead of duplicating long process rules.
- Every new chat must start from `active-run.md` when it exists, then continue from `../00-current-state.md`.
- If chat discussion conflicts with these docs, update docs first, then continue implementation.
- Verification/test-runtime issues found while finishing the active task stay inside that same task by default; the next bounded run must resume the recorded local repair path for that blocker before reclassifying it as `blocked_external` or redirecting the work to another host.
- Mandatory security review remains part of DoD for every meaningful task, including automation-delivered and docs-only governance/process sync work.
- Legacy `awaiting_verification` / `partial` labels in older memory are recovery aliases only: interpret them through the current status model and normalize them when the same task is resumed.
- Active epics should follow a documented ordered subtask plan; future product subtasks should come from that plan unless the plan itself is explicitly updated in governance/task memory.

## Read Order (for any new chat/session)

0. `active-run.md` when it exists, then compare it with `git status` and the current branch before choosing work
0a. `automation-executor-prompt.md` for scheduled `InComedy Executor` automation runs; from there read `executor-checklist.md`, and open `executor-policy.md` only when the short checklist is not enough
1. `../00-current-state.md`
2. `../product/product-brief.md`
3. `../product/backlog.md`
3a. `../product/next-epic-queue.md` when the active epic is already in `awaiting_user_review` or when you need to choose the next epic quickly
4. `../engineering/tooling-stack.md`
5. `../engineering/engineering-standards.md`
6. `../engineering/quality-rules.md`
7. `../product/non-functional-requirements.md`
8. `../engineering/architecture-overview.md`
8a. `../engineering/implementation-status.md` when the task needs current delivery state rather than stable architecture
9. `../engineering/test-strategy.md`
9a. `../engineering/verification-memory.md` when the task needs current executable coverage or recent verification outcomes
10. `../governance/decisions-log.md` + latest part referenced by `../00-current-state.md`
11. `../governance/session-log.md` + latest part referenced by `../00-current-state.md`
12. `../governance/decision-traceability.md` + latest part referenced by `../00-current-state.md`
13. `../../standup-platform-ru/README.md` and relevant detailed spec files when the task needs product/domain clarification

## Standing Bootstrap Rules

Any cross-chat handoff/bootstrap message must preserve these rules up front:

- `docs/context/*` stays the primary source of truth; if new input conflicts with the docs, update docs first, then continue implementation.
- New and materially changed repository code comments must stay in Russian and explain responsibility/flow rather than restating syntax.
- New and materially updated project documentation in `docs/context/*`, `docs/README.md`, and adjacent governance/handoff indexes must stay in Russian; untouched historical text may remain in English until that document is edited.
- Backend production-significant flows must use structured logging through the sanitized diagnostics system; raw host/container logs are a fallback path, not the primary observability layer.
- Repeated technical problems and confirmed repair paths should be written into `../engineering/issue-resolution-log.md` so future sessions can resume troubleshooting without replaying the same diagnostics from scratch.
- Before starting new blocker diagnostics, first check `../engineering/issue-resolution-log.md` for an existing entry with the same symptom pattern and reuse that repair path instead of restarting from zero.
- For iOS simulator / Xcode destination failures (`CoreSimulatorService`, placeholder destinations, `showdestinations`, similar symptoms), the first repair action should be to launch Xcode or restart it if it is hung before deeper simulator-service troubleshooting begins.
- External auth/payment/push/PSP providers may be implemented in code, but they become active/default/confirmed only after explicit user confirmation; assistant inference, existing code, and config/examples do not count as approval.
- Every meaningful work session must leave a concise sanitized `Context / Changes / Decisions / Next` entry in `../governance/session-log.md`; raw transcript dumping and secrets are forbidden.
- Every meaningful task still requires a proportional security review; docs/process-only sync must explicitly record a zero-impact verdict when no security surface changed.
- Server diagnostics or production triage must use `../engineering/server-diagnostics-runbook.md`.

## Update Policy

- Current bootstrap snapshot changes (`latest decision`, `current P0 focus`, `next step`, `latest relevant part files`, `active cross-cutting constraints`) -> update `../00-current-state.md`.
- Strategy/scope changes -> update `../product/product-brief.md`.
- Technology choice changes -> update `../engineering/tooling-stack.md`.
- Engineering process/quality changes -> update `../engineering/engineering-standards.md` and/or `../engineering/quality-rules.md`.
- Any major decision -> append entry to the latest decisions-log part referenced by `../governance/decisions-log.md`.
- After each major work block -> append short entry to the latest session-log part referenced by `../governance/session-log.md`.
- For major implementation changes -> update `../governance/decision-traceability.md`.
- For major tasks -> structure the request through `task-request-template.md` and record historical request/outcome context in `task-request-log.md`.
- For active epics -> keep an ordered subtask plan in `task-request-log.md` / latest task-request part, and update that plan explicitly when order or scope changes.
- For repeated technical blockers / non-obvious repair playbooks -> update `../engineering/issue-resolution-log.md`.
- Maintain `active-run.md` as a short overwrite-only recovery checkpoint for the current run; do not use it as a historical log.
- If the active task recovery posture changes between local repair and `blocked_external`, synchronize `active-run.md`, `../00-current-state.md`, and the relevant governance/task memory in the same change.
- External-provider selections may be recorded as active/default/confirmed only after explicit user confirmation; assistant inference, existing code, or sample config/docs do not count as approval.
- Before major tasks, assistant must remind to refresh:
  - `../product/backlog.md` (current priorities),
  if either is outdated for the requested work.
- Default workflow: product owner can provide tasks in free form; assistant must formalize them into task template structure and update context docs.

## Document Split Rule (Context Size Control)

- If a context document grows above ~8,000 characters or becomes hard to scan in one pass, split it.
- Store parts in a dedicated sibling folder to avoid clutter in the parent folder.
- Use an index file plus parts naming:
  - `name.md` (index/overview),
  - `name/name-part-01.md`,
  - `name/name-part-02.md`, etc.
- The index file must contain:
  - short summary,
  - exact ordered list of part files,
  - rule for where new entries should be appended.
- Split examples:
  - `../governance/decisions-log.md` + `../governance/decisions-log/decisions-log-part-01.md`, `../governance/decisions-log/decisions-log-part-02.md`
  - `../governance/session-log.md` + `../governance/session-log/session-log-part-01.md`, `../governance/session-log/session-log-part-02.md`
- Historical formalized task requests follow the same index-plus-parts rule through `task-request-log.md`.
- In handoff, always share index file first, then only relevant parts.

## Chat Handoff Pack

When moving to another chat, share:

1. Absolute path to repo root.
2. Rule: "`docs/context/*` is the primary source of truth."
3. Path to `active-run.md` when it exists.
4. Path to `../00-current-state.md`.
5. Path to `context-protocol.md`.
6. Path to `automation-executor-prompt.md` when the task is a scheduled executor run or automation-governance/prompt maintenance work.
7. Latest relevant part files from decisions/session/traceability referenced by `../00-current-state.md`.
8. Current `P0` focus, next action, and active cross-cutting constraints from `../00-current-state.md`.
9. `../product/next-epic-queue.md` when the current epic is already at review boundary.

Template sync rule:
- If read order, bootstrap rules, responsibilities, or handoff checks change in this protocol, `../00-current-state.md`, `../README.md`, and any affected governance references must be updated in the same change when applicable.
- If the automation runbook changes, keep `automation-executor-prompt.md`, `../00-current-state.md`, and the actual `InComedy Executor` automation TOML prompts aligned in the same work block.

## Definition of Context Sync

Before considering context synchronized in a new chat:

- `active-run.md` is checked when it exists and reconciled with the current git state,
- bootstrap snapshot is read,
- read order is completed,
- latest decision ID is acknowledged,
- current P0 focus is acknowledged,
- next action from the latest part referenced by `session-log.md` is acknowledged.

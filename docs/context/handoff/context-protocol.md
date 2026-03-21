# Context Protocol

This protocol defines how to use and transfer project context between chats.

## Source of Truth

- The folder `docs/context/` is the single source of truth for product and engineering context.
- Every new chat must start from `../00-current-state.md`.
- If chat discussion conflicts with these docs, update docs first, then continue implementation.

## Read Order (for any new chat/session)

1. `../00-current-state.md`
2. `../product/product-brief.md`
3. `../product/backlog.md`
4. `../engineering/tooling-stack.md`
5. `../engineering/engineering-standards.md`
6. `../engineering/quality-rules.md`
7. `../product/non-functional-requirements.md`
8. `../engineering/architecture-overview.md`
9. `../engineering/test-strategy.md`
10. `../governance/decisions-log.md` + latest part referenced by `../00-current-state.md`
11. `../governance/session-log.md` + latest part referenced by `../00-current-state.md`
12. `../governance/decision-traceability.md` + latest part referenced by `../00-current-state.md`
13. `../../standup-platform-ru/README.md` and relevant detailed spec files when the task needs product/domain clarification

## Update Policy

- Current bootstrap snapshot changes (`latest decision`, `current P0 focus`, `next step`, `latest relevant part files`, `active cross-cutting constraints`) -> update `../00-current-state.md`.
- Strategy/scope changes -> update `../product/product-brief.md`.
- Technology choice changes -> update `../engineering/tooling-stack.md`.
- Engineering process/quality changes -> update `../engineering/engineering-standards.md` and/or `../engineering/quality-rules.md`.
- Any major decision -> append entry to the latest decisions-log part referenced by `../governance/decisions-log.md`.
- After each major work block -> append short entry to the latest session-log part referenced by `../governance/session-log.md`.
- For major implementation changes -> update `../governance/decision-traceability.md`.
- For major tasks -> structure the request through `task-request-template.md` and record historical request/outcome context in `task-request-log.md`.
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
3. Path to `../00-current-state.md`.
4. Path to `chat-handoff-template.md`.
5. Latest relevant part files from decisions/session/traceability referenced by `../00-current-state.md`.
6. Current `P0` focus and next action from `../00-current-state.md`.

Ready-to-use message template:
- `chat-handoff-template.md`

Template sync rule:
- If read order, responsibilities, or handoff checks change in this protocol, `chat-handoff-template.md` and `../00-current-state.md` must be updated in the same change when applicable.

## Definition of Context Sync

Before considering context synchronized in a new chat:

- bootstrap snapshot is read,
- read order is completed,
- latest decision ID is acknowledged,
- current P0 focus is acknowledged,
- next action from the latest part referenced by `session-log.md` is acknowledged.

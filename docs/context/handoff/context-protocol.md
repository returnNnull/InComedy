# Context Protocol

This protocol defines how to use and transfer project context between chats.

## Source of Truth

- The folder `docs/context/` is the single source of truth for product and engineering context.
- If chat discussion conflicts with these docs, update docs first, then continue implementation.

## Read Order (for any new chat/session)

1. `../product/product-brief.md`
2. `../engineering/tooling-stack.md`
3. `../engineering/engineering-standards.md`
4. `../engineering/quality-rules.md`
5. `../product/non-functional-requirements.md`
6. `../engineering/architecture-overview.md`
7. `../engineering/test-strategy.md`
8. `../governance/decisions-log.md`
9. `../product/backlog.md`
10. `../governance/session-log.md` (latest entries first)
11. `../governance/decision-traceability.md` (when evaluating execution status)

## Update Policy

- Strategy/scope changes -> update `../product/product-brief.md`.
- Technology choice changes -> update `../engineering/tooling-stack.md`.
- Engineering process/quality changes -> update `../engineering/engineering-standards.md` and/or `../engineering/quality-rules.md`.
- Any major decision -> append entry to the latest decisions-log part referenced by `../governance/decisions-log.md`.
- After each major work block -> append short entry to the latest session-log part referenced by `../governance/session-log.md`.
- For major implementation changes -> update `../governance/decision-traceability.md`.
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
- In handoff, always share index file first, then only relevant parts.

## Chat Handoff Pack

When moving to another chat, share:

1. Absolute path to repo root.
2. Rule: "`docs/context/*` is the primary source of truth."
3. Links/paths to all context files.
4. Last 3 entries from the latest part referenced by `../governance/session-log.md`.
5. Open priorities from `../product/backlog.md` (P0 first).
6. Current decision execution status from `../governance/decision-traceability.md`.

Ready-to-use message template:
- `chat-handoff-template.md`

Template sync rule:
- If read order, responsibilities, or handoff checks change in this protocol, `chat-handoff-template.md` must be updated in the same change.

## Definition of Context Sync

Before considering context synchronized in a new chat:

- read order is completed,
- latest decision ID is acknowledged,
- current P0 focus is acknowledged,
- next action from the latest part referenced by `session-log.md` is acknowledged.

# Project Context

This folder stores product context and decisions so the team and assistant do not lose state between sessions.

## Structure

- `product/`: product scope, priorities, glossary, NFR, and risks.
- `engineering/`: stack, architecture, quality rules, tests, and API contracts.
- `governance/`: decisions and running session memory.
- `handoff/`: cross-chat sync protocol and handoff template.

## Files

- `product/product-brief.md`: stable product vision and core flows.
- `product/backlog.md`: prioritized feature backlog.
- `product/glossary.md`: domain terms and definitions.
- `product/non-functional-requirements.md`: performance, reliability, security, and operability targets.
- `product/risk-log.md`: current risks with mitigation and owners.
- `engineering/tooling-stack.md`: approved and planned technology stack.
- `engineering/engineering-standards.md`: mandatory architecture, MVI, and testing rules.
- `engineering/quality-rules.md`: DoD, quality gates, test minimums, and engineering constraints.
- `engineering/architecture-overview.md`: high-level module and data-flow map.
- `engineering/test-strategy.md`: test levels, ownership, and CI expectations.
- `engineering/api-contracts/README.md`: API contract storage and versioning guide.
- `governance/decisions-log.md`: ADR-like decision register.
- `governance/session-log.md`: short running notes after each significant work session.
- `handoff/context-protocol.md`: standard for reading, updating, and handing off context across chats.
- `handoff/chat-handoff-template.md`: copy-paste message to bootstrap a new chat with full context sync.

## Update Rules

- Keep files in the proper subfolder; avoid creating many unrelated files in one directory.
- Update `product/product-brief.md` only when strategy, roles, or core scope changes.
- Add a new entry to `governance/decisions-log.md` for every architectural or product-level decision.
- Keep `product/backlog.md` ordered by priority (P0/P1/P2).
- After each major work block, append 3-7 lines to `governance/session-log.md`:
  - what changed
  - why
  - next action
- If any context file becomes too large (about 8,000+ characters), split it using the rule in `handoff/context-protocol.md`.

## Operating Rule

- For all future product and implementation tasks, treat files in `docs/context/` as the primary project context.
- If new input conflicts with these docs, update the docs first, then proceed with implementation.

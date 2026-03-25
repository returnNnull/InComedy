# Project Context

This folder stores the compact operational context for product, engineering, governance memory, and cross-chat continuity.

## Quick Start

- For every new chat/session, read `00-current-state.md` first.
- Then follow the ordered onboarding path in `handoff/context-protocol.md`.
- For scheduled executor automations or automation-governance/prompt work, also read `handoff/automation-executor-prompt.md`.
- Treat `docs/context/*` as the primary source of truth for ongoing work.
- Use `../standup-platform-ru/*` as the detailed target-state specification layer when a task needs deeper domain/product clarification.

## Structure

- `00-current-state.md`: compact bootstrap snapshot for the latest decision id, current `P0` focus, next step, latest relevant part files, and active cross-cutting constraints.
- `product/`: product scope, priorities, glossary, NFR, and risks.
- `engineering/`: stack, architecture, quality rules, tests, API contracts, and operational runbooks.
- `governance/`: decisions, traceability, and rolling session memory.
- `handoff/`: cross-chat sync protocol, executor automation runbook, active task template, and historical task-request log.
- `../standup-platform-ru/`: detailed Russian-language product and technical specification package for the current standup-event platform direction.

## Key Files

- `product/product-brief.md`: stable product vision and core flows.
- `product/backlog.md`: prioritized feature backlog.
- `product/glossary.md`: domain terms and definitions.
- `product/non-functional-requirements.md`: performance, reliability, security, and operability targets.
- `product/risk-log.md`: current risks with mitigation and owners.
- `engineering/tooling-stack.md`: approved and planned technology stack.
- `engineering/engineering-standards.md`: mandatory architecture, MVI, governance-memory, and commenting rules.
- `engineering/quality-rules.md`: DoD, quality gates, test minimums, and engineering constraints.
- `engineering/architecture-overview.md`: high-level module and data-flow map.
- `engineering/test-strategy.md`: test levels, ownership, and CI expectations.
- `engineering/server-diagnostics-runbook.md`: operator-only diagnostics retrieval and correlation workflow.
- `governance/decisions-log.md`: decisions-log index with links to part files.
- `governance/session-log.md`: session-log index with links to part files.
- `governance/decision-traceability.md`: split mapping from decisions to code and tests.
- `governance/context-integrity-checklist.md`: pre-merge context consistency checks.
- `handoff/context-protocol.md`: standard for reading, updating, and handing off context across chats.
- `handoff/automation-executor-prompt.md`: canonical runbook for scheduled `InComedy Executor` automations and automation-governance prompt rules.
- `handoff/task-request-template.md`: active reusable structure for new major tasks.
- `handoff/task-request-log.md`: historical formalized requests and implementation outcomes.
- `../standup-platform-ru/README.md`: entry point to the full Russian product/technical handoff package.
- `../standup-platform-ru/11-статус-реализации-на-2026-03-10.md`: current repo-to-spec alignment snapshot.

## Update Rules

- Keep files in the proper subfolder; avoid creating many unrelated files in one directory.
- Product owner responsibility:
  - keep priorities in `product/backlog.md` clear (`P0`/`P1`/`P2`),
  - provide free-form task requests.
- Assistant responsibility:
  - keep engineering/governance docs in sync with implementation changes,
  - maintain decisions/session/traceability records,
  - keep `00-current-state.md` aligned with the latest decision id, current `P0` focus, next step, and active cross-cutting constraints,
  - use `handoff/task-request-template.md` to structure major tasks and record outcomes in `handoff/task-request-log.md`,
  - immediately communicate discovered security vulnerabilities and maintain remediation records in `product/risk-log.md`,
  - remind product owner to refresh priorities in `product/backlog.md` when context is stale.
- Update `product/product-brief.md` only when strategy, roles, or core scope changes.
- Add a new entry to the latest part referenced by `governance/decisions-log.md` for every architectural or product-level decision.
- Keep `product/backlog.md` ordered by priority (`P0`/`P1`/`P2`).
- After each major work block, append 3-7 analytical lines to the latest part referenced by `governance/session-log.md`:
  - what changed
  - why
  - next action
- If any context file becomes too large (about 8,000+ characters), split it using the rule in `handoff/context-protocol.md`.
- Store split parts in a dedicated subfolder named after the index file (example: `session-log/session-log-part-01.md`).
- If `handoff/context-protocol.md` or `handoff/automation-executor-prompt.md` changes, sync `00-current-state.md`, relevant README/navigation references, and governance docs in the same update.

## Operating Rule

- For all future product and implementation tasks, treat files in `docs/context/` as the primary project context.
- Start every new chat from `00-current-state.md`, then continue with the deeper read order from `handoff/context-protocol.md`.
- When implementation requires detailed feature/domain clarification, use `docs/standup-platform-ru/` as the primary detailed specification layer and keep `docs/context/` as the compact memory/index layer.
- When reconciling target-state specs with the current repository, check `../standup-platform-ru/11-статус-реализации-на-2026-03-10.md` first.
- If new input conflicts with these docs, update the docs first, then proceed with implementation.

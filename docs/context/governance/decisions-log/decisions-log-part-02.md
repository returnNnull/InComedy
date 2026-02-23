# Decisions Log Part 02

## D-016

- Date: 2026-02-23
- Status: accepted
- Decision: Add context-operability artifacts (task template, integrity checklist, rollout plan, and decision traceability matrix).
- Rationale: Reduces context drift and clarifies responsibility split between product input and engineering execution.
- Consequences: New governance docs are now part of default operating protocol for major tasks.

## D-017

- Date: 2026-02-23
- Status: accepted
- Decision: Require proactive assistant reminders to refresh task input/backlog before major implementation blocks.
- Rationale: Prevents stale priorities and under-specified tasks from propagating incorrect context into code changes.
- Consequences: Reminder rule added to context protocol and project context responsibilities.

## D-018

- Date: 2026-02-23
- Status: accepted
- Decision: Use free-form task intake by default; assistant is responsible for formalizing requests into project templates and context updates.
- Rationale: Reduces user overhead while preserving documentation discipline and context integrity.
- Consequences: Task-template usage remains mandatory for major work, but it is filled and maintained by assistant unless user asks otherwise.

## D-019

- Date: 2026-02-23
- Status: accepted
- Decision: Keep `chat-handoff-template.md` auto-synced with `context-protocol.md`.
- Rationale: Prevents transfer-template drift when protocol rules evolve.
- Consequences: Any protocol change affecting handoff behavior must include template update in the same change.

## D-020

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize Android app navigation on `androidx.navigation:navigation-compose`.
- Rationale: Provides a stable and well-supported route graph foundation for upcoming multi-screen Android flows.
- Consequences: `composeApp` must define screen routing through `NavHost`; new Android screens should register routes in the navigation graph.

## D-021

- Date: 2026-02-23
- Status: accepted
- Decision: When splitting oversized context documents, store parts in a dedicated subfolder (`name/name-part-XX.md`) instead of the parent folder root.
- Rationale: Keeps context directories clean and improves scan/readability in large governance docs.
- Consequences: Existing and future split context files must follow folder-based part storage; index files must reference subfolder paths.

## D-022

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize Android navigation architecture as root graph + feature-owned nested subgraphs.
- Rationale: Flat navigation becomes hard to maintain as feature count grows; subgraphs preserve modular boundaries and predictable ownership.
- Consequences: New Android features must expose navigation registration via dedicated `feature/<name>/navigation/*` graph files and integrate through root app graph.

# Task Request Template Part 03

## Latest Formalized Request (Spec-to-Repo Sync)

## Context

- Related docs/decisions:
  - full package `docs/standup-platform-ru/*`
  - `docs/context/README.md`
  - `D-045`, `D-046`, `D-047`, `D-048`
- Current constraints:
  - `docs/context/*` stays the primary compact source of truth.
  - `docs/standup-platform-ru/*` remains target-state specification and must not be rewritten as if unimplemented domains already exist in code.
  - Any status sync must distinguish `implemented`, `partial`, and `planned`.

## Goal

- What should be delivered:
  - Study the full Russian specification package.
  - Synchronize repository-facing documentation with the actual implementation state.

## Scope

- In scope:
  - `README.md`
  - `docs/standup-platform-ru/*`
  - relevant `docs/context/*` product/engineering/governance files
  - decision execution status for `D-045`-`D-048`
- Out of scope:
  - Implementing missing organizer/ticketing/lineup/donation domains in code
  - Changing accepted product scope without an explicit new decision

## Constraints

- Tech/business constraints:
  - Preserve the target-state specification while adding explicit current-state sync.
  - Split oversized context files according to `context-protocol.md` if the sync pushes them past the readability threshold.
  - Do not claim production-ready VK/Google/Apple/donation support where the code does not provide it.
- Deadlines or milestones:
  - Complete in the current session as a documentation synchronization pass.

## Definition of Done

- Functional result:
  - There is an explicit repo-to-spec status snapshot and updated project-facing documentation.
- Required tests:
  - Manual consistency review of changed docs and file structure.
- Required docs updates:
  - `README.md`
  - `docs/standup-platform-ru/README.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`

---

## Latest Formalized Request (Telegram-First Identity Foundation)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `D-045`, `D-048`, `D-049`
- Current constraints:
  - Telegram is the only implemented login provider for now.
  - Telegram-only is an interim delivery choice, not the final auth strategy.
  - Future `VK` / `Google` / `Apple` providers must plug into the same internal user model without refactoring organizer/product domains.

## Goal

- What should be delivered:
  - Build the next P0 foundation slice around provider-agnostic identity, roles, and organizer workspace membership while keeping Telegram as the sole active provider temporarily.

## Scope

- In scope:
  - internal `User + AuthIdentity` model
  - role assignments and active role context
  - organizer workspace membership and permission roles
  - session/profile decoupling from `telegram_id`
  - doc and governance updates for phased auth rollout
- Out of scope:
  - immediate implementation of `VK`, `Google`, or `Sign in with Apple`
  - venues, events, ticketing, lineup, donations

## Constraints

- Tech/business constraints:
  - Preserve accepted MVP scope: additional providers are still mandatory before public release.
  - Do not let provider-specific ids become domain identifiers for profile, RBAC, or workspace logic.
  - Keep existing Telegram login flow working while evolving the internal model.
- Deadlines or milestones:
  - This is the default next implementation slice after the current auth/session foundation.

## Definition of Done

- Functional result:
  - Telegram-authenticated users map into a provider-agnostic internal identity with roles and workspace membership foundations ready for future provider linking.
- Required tests:
  - unit tests for role/context logic
  - integration tests for session/user identity mapping
  - permission tests for workspace role boundaries
- Required docs updates:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

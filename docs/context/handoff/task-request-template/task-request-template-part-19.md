# Task Request Template Part 19

## Formalized Refactoring Request (Auth/Session Modular Split)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-031`
  - `D-038`
  - `D-039`
  - `D-045`
  - `D-049`
  - `D-051`
- Current constraints:
  - Recent organizer workspace/team-management work expanded the amount of role and workspace logic consumed after sign-in.
  - `auth` should remain focused on entry flows and internal session lifecycle, while post-auth role/workspace behavior must stay evolvable without bloating provider-specific code.
  - Backend environment/config and shared HTTP helper logic are cross-cutting concerns and should not force `data:session` to depend on `data:auth`.

## Goal

- What should be delivered:
  - analysis of the recent auth/session growth after organizer-role changes
  - refactoring that moves role/workspace context into dedicated session modules
  - refactoring that moves backend environment/config + shared transport helpers into a dedicated core module
  - no functional regressions in credential auth, VK auth, session restore, or organizer workspace/team flows
  - synchronized docs/context and governance memory for the new module boundaries

## Scope

- In scope:
  - keep `:feature:auth` + `:data:auth` bounded to sign-in, provider launch/verify, refresh, logout, and session user fetch
  - keep `:feature:session` + `:data:session` bounded to roles, active role, workspaces, invitations, and membership role management
  - introduce a shared backend module for mobile environment/config and common HTTP error/authorization helpers
  - update imports, DI wiring, tests, and context documents to match the new layout
- Out of scope:
  - changing auth business behavior or provider priorities
  - changing server API contracts beyond what is required by existing mobile behavior
  - introducing new organizer product scope beyond the already implemented invitations/permission-role slice

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth and must be updated in the same change when module boundaries move
  - new and materially changed code must keep Russian comments at class/method/property level
  - `data:session` must not depend on `data:auth` only for base URL or HTTP helper reuse after the refactor
  - verification must cover the affected auth/session/mobile module graph

## Definition of Done

- Functional result:
  - mobile backend environment/config lives outside `auth`
  - post-auth roles/workspaces/invitations live outside `auth`
  - auth code size/coupling is reduced without changing user-facing behavior
  - docs and governance traceability reflect the new modular boundaries
- Required tests:
  - `./gradlew :core:backend:allTests :data:auth:allTests :data:session:allTests :shared:allTests :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`

## Formalized Refactoring Request (Explicit Domain Layer And Architecture Rule)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-026`
  - `D-038`
  - `D-045`
  - `D-049`
- Current constraints:
  - After the previous auth/session split, `data` no longer depended on another `data` module, but business contracts were still stored under `feature/*/domain`, which made the compile-time graph look like `data -> feature`.
  - The repository needs an explicit architectural standard that separates `core`, `domain`, `data`, and `feature` responsibilities so future modularization does not regress into mixed layers.

## Goal

- What should be delivered:
  - move auth and session business contracts into dedicated `domain/*` modules
  - remove the remaining `data -> feature` compile-time smell by switching to `data -> domain`
  - codify `core / domain / data / feature` responsibilities and dependency rules in engineering docs
  - update governance traceability and session memory to reflect the new standard

## Scope

- In scope:
  - create `:domain:auth` and `:domain:session`
  - move auth/session business models, ports, and use-case helpers from `feature/*/domain` into `domain/*`
  - remove the obsolete `:feature:session` module now that it only contained domain contracts
  - rewire `feature`, `data`, `shared`, and app modules to the explicit domain layer
  - update architecture standards and active traceability paths
- Out of scope:
  - changing auth/session business behavior
  - introducing new organizer scope
  - large presentation-layer moves beyond dependency cleanup required by the domain extraction

## Constraints

- Tech/business constraints:
  - `core` must stay technical and must not absorb business terms or product rules
  - `data` may depend on `domain`, but must not depend on `feature/presentation`
  - `domain` must not depend on `data` or `feature`
  - new and materially changed code must keep Russian comments

## Definition of Done

- Functional result:
  - auth and session contracts live in explicit `domain` modules
  - `data` modules depend only on `domain` and `core`, not on `feature`
  - engineering standards describe the architecture in the same terms as the code
  - active traceability and session governance documents reference the new module layout
- Required tests:
  - `./gradlew :domain:auth:allTests :domain:session:allTests :core:backend:allTests :data:auth:allTests :data:session:allTests :shared:allTests :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin`

## Formalized Planning Update (Drop Workspace Smoke From Active Queue)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/governance/session-log.md`
- Current constraints:
  - The previously carried operational follow-up for a live smoke of the organizer workspace invitation/role-management flow is no longer considered part of the active task queue.
  - The active product plan should move directly to the next documented `P0` slice instead of blocking on an explicit smoke-validation task.

## Goal

- What should be delivered:
  - remove the workspace live-smoke follow-up from the active near-term plan
  - make the next project step explicit in governance memory
  - keep the product sequence aligned with the existing `P0` backlog ordering

## Scope

- In scope:
  - update governance memory so the current `Next` no longer points to workspace smoke
  - make `Venue management and hall template builder v1` the active next product slice
- Out of scope:
  - changing the `P0` backlog ordering itself
  - adding new implementation work in this planning-only update

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the source of truth and must reflect the updated plan
  - the change should stay in governance/planning docs only unless a broader reprioritization is requested

## Definition of Done

- Functional result:
  - the latest governance `Next` no longer includes workspace live smoke
  - the next active project slice is documented as `Venue management and hall template builder v1`

## Formalized Refactoring Request (Backend Legacy Naming And Repository Ports)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-035`
  - `D-039`
  - `D-049`
- Current constraints:
  - The backend persistence layer still exposed old Telegram-specific naming in classes that now serve provider-agnostic user/session/workspace responsibilities.
  - Organizer and session surfaces were depending on the full `UserRepository`, even when they only needed a narrow subset of persistence operations.

## Goal

- What should be delivered:
  - remove misleading Telegram-specific naming from generic backend repository implementations
  - narrow backend compile-time dependencies for session and organizer flows to smaller repository ports
  - keep all auth/session/workspace behavior unchanged
  - sync governance/traceability after the backend refactor

## Scope

- In scope:
  - rename the generic PostgreSQL repository and in-memory test repository away from Telegram-specific names
  - introduce narrow repository ports for session lifecycle and organizer workspace bounded contexts
  - rewire backend routes/services/tests to the new names and narrower ports
  - update governance traceability paths affected by the rename
- Out of scope:
  - removing the legacy Telegram auth slice itself
  - changing database schema/table names
  - changing auth provider behavior or organizer business rules

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - materially changed backend code must keep Russian comments
  - the refactor must stay behavior-preserving and validate with backend automated tests

## Definition of Done

- Functional result:
  - generic backend persistence classes no longer look Telegram-only
  - organizer/session code depends on bounded repository ports instead of the whole persistence surface
  - governance and decision traceability reference the updated backend paths
- Required tests:
  - `./gradlew :server:test`

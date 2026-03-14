# Task Request Template Part 12

## Formalized Analysis Request (Phone OTP Provider Evaluation And Integration Plan)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/product/non-functional-requirements.md`
  - `D-057`
- Current constraints:
  - The active MVP auth standard is `phone number + one-time code`, with `VK` planned next.
  - The repository already has a provider-agnostic internal auth/session foundation and must keep backend-issued internal sessions as the source of truth.
  - The target market is RU-first, so OTP delivery reliability, sender registration rules, diagnostics, and anti-abuse controls must be evaluated for that market rather than assuming a generic global default.

## Goal

- What should be delivered:
  - Analyze realistic third-party providers for phone OTP delivery/verification.
  - Recommend a primary provider and at least one fallback/backup option.
  - Describe how phone auth should be implemented in the current Ktor + KMP architecture without collapsing the provider-agnostic model.

## Scope

- In scope:
  - compare candidate OTP/SMS providers using official public docs
  - assess product/engineering fit for RU-market onboarding
  - outline backend contracts, mobile UX flow, secure storage/session issuance, abuse controls, and observability requirements
  - identify provider onboarding or operational risks that affect delivery timing
- Out of scope:
  - committing to a vendor contract without product/ops approval
  - implementing the OTP flow in code in the same task
  - introducing a hosted identity platform that replaces the repository's internal user/session model

## Constraints

- Tech/business constraints:
  - The mobile app should collect phone number and code, while backend remains responsible for OTP issuance/verification and internal session issuance.
  - Provider credentials, API keys, and message-signing secrets must stay out of the repository.
  - The solution must support strong rate limiting, request correlation, replay protection, and secure token rotation.
  - Recommendations should account for RU delivery realities, not just generic global SDK convenience.

## Definition of Done

- Functional result:
  - provider comparison memo identifies a preferred primary provider, fallback provider, and any rejected options
  - implementation plan fits the current provider-agnostic auth architecture and session model
  - next execution step is clear enough to formalize into a coding task
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

## Formalized Analysis Request (Phone OTP Versus Email Code Authentication)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/product/non-functional-requirements.md`
  - `D-057`
- Current constraints:
  - The active documented MVP standard is still `phone number + one-time code`.
  - The user requested a comparison against email-based code delivery before implementation starts.
  - Any recommendation must distinguish between delivery-channel cost and the broader product/security impact on the current RU-first event platform.

## Goal

- What should be delivered:
  - Compare phone OTP and email-code auth for the current product.
  - Highlight tradeoffs in cost, UX, identity strength, abuse resistance, and implementation fit.
  - Recommend whether the project should stay with phone-first or pivot to email-first for MVP.

## Scope

- In scope:
  - compare delivery costs using public pricing where available
  - compare implementation approach within the current backend-owned auth/session architecture
  - assess user friction and operational impact for RU-first onboarding
- Out of scope:
  - changing product docs/ADRs to email-first unless the user explicitly decides to pivot
  - implementing email auth in code in the same task

## Constraints

- Tech/business constraints:
  - The comparison must account for the current one-account multi-role model and ticketing/check-in flows.
  - Recommendations should separate objective pricing facts from product inferences.
  - If email is considered, it should still be implemented as backend-owned code verification, not as a hosted identity replacement.

## Definition of Done

- Functional result:
  - comparison clearly states which channel is cheaper
  - comparison clearly states which channel better fits the current MVP
  - next decision point is explicit
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

## Formalized Analysis Request (Auth Pivot To Login Password Plus VK ID)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `D-059`
- Current constraints:
  - The user introduced a new product decision that supersedes the current phone-first auth direction.
  - The repository must now treat first-party credential auth (`login + password`) as the primary auth baseline.
  - VK ID remains in scope, but only after documentation is resynchronized and the official integration requirements/costs are analyzed.

## Goal

- What should be delivered:
  - Sync docs and governance to the new auth standard.
  - Analyze official VK ID documentation for what is required to integrate mobile + backend auth.
  - Estimate direct and indirect costs introduced by credential auth plus VK ID.

## Scope

- In scope:
  - update product/engineering/governance docs for the new auth pivot
  - inspect official VK ID integration docs and business verification requirements
  - identify the implementation path that best fits the current architecture
  - summarize direct provider costs and operational costs
- Out of scope:
  - implementing credential auth in code in the same task
  - implementing VK ID in code in the same task

## Constraints

- Tech/business constraints:
  - The internal `User + AuthIdentity + session` foundation remains mandatory.
  - Password-based auth must be designed as a first-party backend flow with secure hashing and abuse controls.
  - VK ID analysis should rely on official VK documentation only.

## Definition of Done

- Functional result:
  - docs and governance reflect the new auth standard
  - VK ID prerequisites and costs are clearly summarized before implementation starts
  - next implementation slice is explicit
- Required docs updates:
  - `docs/context/product/*`
  - `docs/context/engineering/*`
  - `docs/context/governance/*`

## Formalized Operational Request (New Chat Context Synchronization)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/product/non-functional-requirements.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/product/backlog.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-058`
  - `D-059`
- Current constraints:
  - `docs/context/*` is the primary source of truth for this repository.
  - If new information conflicts with context docs, documentation must be updated before code changes start.
  - Before any implementation, the current chat must confirm the latest decision id, active `P0`, next step from session governance memory, and execution status of key decisions.

## Goal

- What should be delivered:
  - Read and synchronize the required context documents in the specified order.
  - Confirm the current governance/product baseline before taking a new implementation task.
  - Leave a short sanitized trace that this new chat performed context sync first.

## Scope

- In scope:
  - review the required product, engineering, and governance context files
  - extract the currently active decision and backlog priority
  - identify the next documented execution step and current decision-traceability status
  - update governance memory to reflect that context sync was completed
- Out of scope:
  - implementing new product code in the same task
  - changing product or engineering decisions unless a conflict is discovered

## Constraints

- Tech/business constraints:
  - The active auth baseline remains `login + password` first, then `VK ID`, unless docs are explicitly changed again.
  - Session log updates must stay concise, analytical, and sanitized.
  - No secrets, tokens, or raw transcript content may be written into governance docs.

## Definition of Done

- Functional result:
  - the current chat confirms the latest `decision ID`
  - the current `P0` priority is confirmed from backlog
  - the next step is confirmed from session governance memory
  - key decision-traceability execution state is confirmed before further work
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

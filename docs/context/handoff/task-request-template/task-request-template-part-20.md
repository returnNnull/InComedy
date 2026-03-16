# Task Request Template Part 20

## Formalized Context Sync Request (New Chat Baseline Confirmation)

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
  - `D-060`
  - `D-061`
- Current constraints:
  - `docs/context/*` is the primary source of truth and must be synchronized before any new implementation starts.
  - Governance registers are split into index files plus latest part files, so confirmation requires reading both the index and the active tail.
  - The previous session left a technical next step around deeper backend cleanup, while the product backlog still carries `Venue management and hall template builder v1` inside `P0`.

## Goal

- What should be delivered:
  - complete the mandatory new-chat context synchronization
  - confirm the latest decision id from governance decisions
  - confirm the currently relevant `P0` priority from the product backlog
  - confirm the latest `Next` step from governance session memory
  - confirm the current execution status of the key recent decisions from decision traceability

## Scope

- In scope:
  - read the required context documents in the prescribed order
  - follow split indexes into the active `decisions-log`, `session-log`, and `decision-traceability` parts
  - summarize the confirmed baseline that future implementation must follow
  - record this chat start in governance memory
- Out of scope:
  - code implementation
  - backlog reprioritization
  - accepting new architecture or product decisions

## Constraints

- Tech/business constraints:
  - if any newly provided instruction conflicts with `docs/context/*`, the docs must be updated before code in a later implementation task
  - session-memory updates must stay concise, analytical, and sanitized
  - no secrets, tokens, or raw diagnostics output may be stored in governance memory

## Definition of Done

- Functional result:
  - the new chat has a confirmed shared baseline from the source-of-truth docs
  - the latest decision id, current `P0`, current `Next`, and recent decision statuses are explicitly confirmed
  - governance memory reflects that this chat started with a context-sync-only step
- Required tests:
  - none; documentation/governance synchronization only

## Formalized Discovery Request (Venue Management And Hall Template Builder v1 Context)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/product/backlog.md`
  - `docs/context/product/glossary.md`
  - `docs/context/product/non-functional-requirements.md`
  - `docs/context/product/risk-log.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/decisions-log/decisions-log-part-03.md`
  - `docs/context/governance/decision-traceability/decision-traceability-part-03.md`
  - `docs/standup-platform-ru/03-роли-и-сценарии.md`
  - `docs/standup-platform-ru/04-функциональные-требования.md`
  - `docs/standup-platform-ru/05-архитектура-системы.md`
  - `docs/standup-platform-ru/06-доменная-модель-и-данные.md`
  - `docs/standup-platform-ru/08-api-и-событийная-модель.md`
  - `docs/standup-platform-ru/09-nfr-безопасность-и-риски.md`
  - `docs/standup-platform-ru/10-дорожная-карта-и-план-релиза.md`
  - `docs/standup-platform-ru/11-статус-реализации-на-2026-03-10.md`
  - `D-046`
  - `D-047`
- Current constraints:
  - `Venue management and hall template builder v1` is the current `P0` product slice after auth/session/workspace foundation work.
  - Compact `docs/context/*` establishes priorities and rules, while `docs/standup-platform-ru/*` contains the detailed target-state specification for this domain.
  - The current repository has no implemented venues/hall bounded context and no active OpenAPI contract for it yet.

## Goal

- What should be delivered:
  - gather the documented scope for `Venue management and hall template builder v1`
  - distinguish required MVP behavior from deferred complexity
  - identify domain entities, user scenarios, API surface, architecture boundaries, risks, and test expectations that the implementation must follow

## Scope

- In scope:
  - read the venue/hall requirements across compact context and detailed product specification
  - produce an implementation-oriented summary for the next coding step
  - record this discovery pass in governance memory
- Out of scope:
  - writing the venue/hall implementation itself
  - reprioritizing `P0`
  - inventing unsupported product scope beyond the documented builder v1

## Constraints

- Tech/business constraints:
  - hall builder v1 must stay limited to a 2D template editor and must not drift into a generic CAD tool
  - hall templates must remain reusable while events use frozen hall snapshots for ticketing consistency
  - future implementation must respect the repository `core / domain / data / feature` layering and Russian comment requirements

## Definition of Done

- Functional result:
  - the venue/hall slice is described in a way that can drive concrete implementation planning
  - required scope, deferred scope, API placeholders, and domain invariants are explicit
  - governance memory captures that the current work was a documentation-context discovery step
- Required tests:
  - none in this discovery-only step

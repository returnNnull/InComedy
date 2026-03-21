# Current State Snapshot

Updated: `2026-03-21`

Use this file as the bootstrap entry point for every new chat/session before reading deeper context docs.

## Source of Truth

- `docs/context/*` is the operational source of truth for ongoing work.
- `docs/standup-platform-ru/*` is the detailed target-state specification layer.
- Repository code is the source of truth for the exact current implementation surface and constraints.

## Current Snapshot

- Latest accepted decision: `D-065`
- Latest decisions part: `governance/decisions-log/decisions-log-part-05.md`
- Latest session-log part: `governance/session-log/session-log-part-17.md`
- Latest decision-traceability part: `governance/decision-traceability/decision-traceability-part-05.md`
- Latest task-request log part: `handoff/task-request-template/task-request-template-part-28.md`
- Active auth baseline: `login + password` plus `VK ID`
- Current `P0` focus: audience ticketing path on top of public catalog, public inventory, and provider-agnostic checkout-order / paid-order lifecycle foundations, while the concrete external PSP remains unconfirmed
- Current next bounded step: obtain explicit user confirmation before treating any external PSP as the selected checkout path; only after that continue provider-specific payment confirmation toward QR issuance and check-in

## Active Constraints

- New and materially changed repository code must keep required comments in Russian.
- Backend production-significant flow diagnostics must use the sanitized diagnostics system rather than ad-hoc console logging.
- External provider choices must not be treated as active/default/confirmed without explicit user confirmation; assistant inference, existing code, and example config/docs do not count as approval.
- Changes that move scope, decisions, current focus, next step, or active constraints must update `docs/context/*` in the same change.

## Required Read Path After This File

1. `product/product-brief.md`
2. `product/backlog.md`
3. `engineering/tooling-stack.md`
4. `engineering/engineering-standards.md`
5. `engineering/quality-rules.md`
6. `product/non-functional-requirements.md`
7. `engineering/architecture-overview.md`
8. `engineering/test-strategy.md`
9. `governance/decisions-log.md` plus the latest part listed above
10. `governance/session-log.md` plus the latest part listed above
11. `governance/decision-traceability.md` plus the latest part listed above
12. `../standup-platform-ru/README.md` and relevant detailed spec files when the task needs domain/product clarification

## Where To Write

- Major task structure: `handoff/task-request-template.md`
- Historical formalized requests and outcomes: `handoff/task-request-log.md`
- Per-session analytical memory: `governance/session-log.md`
- Production diagnostics workflow: `engineering/server-diagnostics-runbook.md`

## Update Rule

- Update this file whenever the latest decision id, current `P0` focus, next step, latest relevant part files, or active cross-cutting constraints change.

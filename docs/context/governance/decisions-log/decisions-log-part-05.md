# Decisions Log Part 05

## D-065

- Date: 2026-03-21
- Status: accepted
- Decision: Assistant inference, already implemented code, example configuration, or draft documentation must not be treated as user approval of an external auth/payment/push/PSP provider. Until the user explicitly confirms a provider choice, provider-specific integrations remain unapproved candidates and must not be described as the selected current path.
- Rationale: The repository already allowed disabled-by-default provider implementations, but the earlier YooKassa wording still drifted into “current step” and “active path” language based on implementation momentum. That creates governance ambiguity: a future chat can incorrectly assume the user already chose a provider when only optional code exists. The rule needs to distinguish “present in the repository” from “explicitly approved by the user.”
- Consequences: `00-current-state.md`, tooling/architecture docs, handoff instructions, and provider-facing README/env notes must describe unconfirmed providers as candidate-only; no active `P0` focus or next-step wording may imply a concrete provider was selected without explicit user confirmation; and if a chat makes that mistake, the correction must be recorded in decisions/session/task memory in the same change.

## D-066

- Date: 2026-03-21
- Status: accepted
- Decision: Defer concrete external PSP selection and activation until the final pre-publication stage. Until then, continue the `P0` ticketing path only through provider-agnostic order, ticket issuance, QR, and check-in foundations.
- Rationale: The user explicitly does not want provider selection to be auto-carried forward from implementation momentum, and also does not want to choose a PSP yet. Blocking all ticketing progress on that future choice would stall core product delivery unnecessarily, while the repository already has enough provider-agnostic order semantics to continue internal ticket/check-in work first.
- Consequences: `00-current-state.md` and `product/backlog.md` must stop treating PSP confirmation as the immediate next step; the next active task becomes provider-agnostic ticket issuance plus checker-facing QR/check-in foundations; and any existing PSP adapter remains disabled candidate-only infrastructure until the final pre-publication decision point.

## D-067

- Date: 2026-03-23
- Status: accepted
- Decision: Organizer review transition to `approved` must idempotently materialize a draft `lineup entry` with explicit `order_index`, while the same slice must not auto-delete or auto-rebuild lineup entries on later status changes.
- Rationale: `EPIC-067` needed a safe backend-only bridge from comedian applications into lineup ordering before shared/mobile UI work. Creating draft entries exactly once on approval enables organizer reorder semantics immediately, while avoiding destructive reverse-sync keeps the blast radius additive until later live-state and organizer edit rules are explicitly delivered.
- Consequences: backend application review flow now owns the one-way `approved -> draft lineup entry` bridge; lineup persistence must keep stable explicit ordering plus organizer reorder API; and future work on lineup editing/live-state must treat deletion/reconciliation behavior as a separate step rather than an implicit side effect of review-status changes.

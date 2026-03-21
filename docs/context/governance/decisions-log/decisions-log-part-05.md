# Decisions Log Part 05

## D-065

- Date: 2026-03-21
- Status: accepted
- Decision: Assistant inference, already implemented code, example configuration, or draft documentation must not be treated as user approval of an external auth/payment/push/PSP provider. Until the user explicitly confirms a provider choice, provider-specific integrations remain unapproved candidates and must not be described as the selected current path.
- Rationale: The repository already allowed disabled-by-default provider implementations, but the earlier YooKassa wording still drifted into “current step” and “active path” language based on implementation momentum. That creates governance ambiguity: a future chat can incorrectly assume the user already chose a provider when only optional code exists. The rule needs to distinguish “present in the repository” from “explicitly approved by the user.”
- Consequences: `00-current-state.md`, tooling/architecture docs, handoff instructions, and provider-facing README/env notes must describe unconfirmed providers as candidate-only; no active `P0` focus or next-step wording may imply a concrete provider was selected without explicit user confirmation; and if a chat makes that mistake, the correction must be recorded in decisions/session/task memory in the same change.

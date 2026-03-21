# Task Request Template Part 28

## Formalized Governance Correction Request (External Provider Approval Semantics)

## Why This Step

- The previous documentation correction still left YooKassa phrased as part of the active `P0` path, even though the user had not explicitly approved YooKassa as the chosen PSP.
- That wording makes a future chat likely to confuse “implemented optional adapter” with “user-confirmed provider decision,” which is exactly the governance drift the user wants to prevent.
- The repository needs a durable rule that assistant inference, local code presence, and sample config do not count as provider approval.

## Scope

- Add an explicit governance decision clarifying that assistant/chat inference is not user confirmation for external providers.
- Re-align `00-current-state.md` so active `P0` and next-step wording stay provider-agnostic until the user explicitly chooses a provider.
- Update tooling, architecture, handoff, checklist, README, and env docs so YooKassa is described only as an unapproved disabled-by-default candidate.
- Roll decisions/session/task memory into new part files because the previous latest parts already exceeded the context-size threshold.

## Explicitly Out Of Scope

- removing the existing YooKassa implementation from the repository
- selecting a different PSP automatically
- adding new payment-provider code or runtime behavior

## Constraints

- `docs/context/*` remains the source of truth and must be updated in the same change.
- The correction must distinguish clearly between `implemented` and `approved by the user`.
- Existing provider-specific code may remain in the repository, but the active documentation must not present it as selected without explicit user confirmation.
- Context-size split rules from `context-protocol.md` must be respected while updating governance memory.

## Acceptance Signals

- Active context docs no longer describe YooKassa as the chosen current PSP path.
- Governance docs explicitly state that earlier assistant inference, code presence, or env examples do not equal user approval.
- Latest decision/session/task memory and handoff instructions are synchronized around the corrected rule.

## Implementation Outcome

## Delivered

- Added `D-065` to record that assistant inference, existing code, and example config/docs do not count as user approval of an external provider.
- Rewrote `00-current-state.md` so the active `P0` ticketing focus is provider-agnostic again and the next bounded step requires explicit user confirmation before any provider-specific PSP path is treated as selected.
- Updated tooling, architecture, context protocol, handoff template, integrity checklist, README, and env examples so YooKassa is documented only as an unapproved disabled-by-default candidate.
- Split rolling governance/task memory forward into `decisions-log-part-05.md`, `session-log-part-17.md`, and `task-request-template-part-28.md`.

## Verification

- Manual consistency review across the updated governance/current-state/handoff files.
- Search review for `YooKassa`, `provider`, `confirmed`, and `user confirmation` wording in the active context and server docs.

## Remaining Follow-Up

- Wait for explicit user confirmation before treating any external PSP as selected in future implementation planning.
- Continue ticketing work only through provider-agnostic slices until such confirmation exists.

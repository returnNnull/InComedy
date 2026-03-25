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

## D-068

- Date: 2026-03-24
- Status: accepted
- Decision: Verification, test harness, simulator/runtime, and analogous execution problems discovered while finishing an active task must be resolved inside that same task by default and must not be promoted into a separate blocker/task/epic unless they require explicit user confirmation, destructive or irreversible action, or work outside the allowed task boundary.
- Rationale: Reclassifying every verification/runtime issue as a new blocker fragments delivery history, hides the real completion state of the active slice, and encourages unfinished work to accumulate across multiple pseudo-tasks even when the failure is tightly coupled to the current implementation.
- Consequences: active context docs and handoff rules must treat these issues as part of the current task's completion path; such issues may keep the task in `partial`, but they should not trigger a new task selection by default; and future chats should continue the same epic/subtask until the terminal verification issue is resolved or a true external blocker is reached.

## D-069

- Date: 2026-03-25
- Status: accepted
- Decision: All scheduled `InComedy Executor` automations must use a dedicated repository runbook at `docs/context/handoff/automation-executor-prompt.md`, and new automation-driven implementation runs must not finish with `partial`; after bounded local repair attempts they must end either as a completed outcome or as a finished `docs_only` blocker verdict with evidence and the exact next action.
- Rationale: The previous executor prompt was duplicated inline across multiple automation TOML files and had already drifted from the newer governance rules proposed in chat, especially around `partial` as a terminal result. Centralizing the executor runbook inside the repository reduces prompt drift, makes process changes reviewable in git, and gives future automations one canonical place to follow.
- Consequences: `automation-executor-prompt.md` becomes the primary process source for executor automations; the automation TOML prompt should be shortened to a stable reference to that file; `00-current-state.md`, handoff docs, and quality rules must stay aligned with the runbook; and any legacy `partial` recovery state should be normalized the next time the same implementation task is resumed by automation.

## D-070

- Date: 2026-03-25
- Status: accepted
- Decision: `AutomationState` должен считать фактические executor launches через `run_slots_used_in_cycle`, а не только число завершенных подзадач; каждый automation launch увеличивает этот счетчик в пределах текущего cycle.
- Rationale: Формулировка про “10 completed subtasks” была двусмысленной и занижала реальное потребление executor launches, особенно когда в cycle были docs-only sync-слоты или historical `partial` recovery. Нужен был именно корректный launch counter, а не surrogate-метрика по закрытым подзадачам.
- Consequences: `automation-executor-prompt.md` и `00-current-state.md` должны использовать `run_slots_used_in_cycle` как наблюдаемый счетчик запусков текущего cycle; это поле не должно трактоваться как отдельный жёсткий продуктовый cap поверх фактического расписания automation-ов и ручных rerun-ов.

## D-071

- Date: 2026-03-25
- Status: accepted
- Decision: Remove the standalone bootstrap document and keep its surviving rules in the canonical handoff sources: `docs/context/handoff/context-protocol.md` for general cross-chat guidance and `docs/context/handoff/automation-executor-prompt.md` for scheduled executor/automation-governance runs.
- Rationale: The separate bootstrap document duplicated protocol and standing-rule content, which created another drift point on top of the newer repository-side executor runbook. Consolidating bootstrap guidance into the protocol plus the automation runbook reduces maintenance overhead, removes ambiguous source selection, and keeps future prompt/process changes reviewable in one place.
- Consequences: the redundant bootstrap document should be deleted; existing references must be replaced with `context-protocol.md`, `automation-executor-prompt.md`, or neutral “bootstrap guidance” wording as appropriate; and `00-current-state.md`, README navigation, governance memory, and task history must be synchronized in the same change.

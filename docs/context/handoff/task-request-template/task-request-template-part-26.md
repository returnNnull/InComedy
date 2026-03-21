# Task Request Template Part 26

## Formalized Context Sync Request (New Chat Baseline Refresh)

## Why This Step

- The user explicitly requested a fresh context synchronization before any new implementation work.
- Recent repository activity touched event and ticketing foundations, so starting the next task from stale governance or backlog state would risk conflicting changes.
- Current collaboration rules require each meaningful request to be formalized in `task-request-template` and reflected in governance memory.

## Scope

- Read the required context documents in the prescribed order:
  - product brief
  - tooling stack
  - engineering standards
  - quality rules
  - non-functional requirements
  - architecture overview
  - test strategy
  - decisions log
  - backlog
  - latest session-log entries
  - decision traceability
- Confirm from source documents:
  - latest decision id
  - current `P0` priority set
  - current next step from session memory
  - current execution status of key traceability decisions
- Record the sync in governance memory without changing implementation scope.

## Explicitly Out Of Scope

- feature implementation or refactoring
- API/schema/runtime configuration changes
- production diagnostics fetching or server mutations
- reprioritizing backlog items beyond what is already documented

## Constraints

- `docs/context/*` remains the primary source of truth.
- If a rolling governance file has already crossed the size threshold, continue in a new part instead of appending to the oversized file.
- No raw transcript, secrets, tokens, or sensitive runtime values may be written into governance documents.

## Acceptance Signals

- The assistant can state the current decision id, `P0` backlog focus, next implementation step, and key decision-traceability statuses directly from repository docs.
- Governance memory reflects that the sync was completed in this chat.
- No source-of-truth document changes are made unless an actual inconsistency is found.

## Implementation Outcome

## Delivered

- Re-read the required context documents and the latest split parts for `decisions-log`, `session-log`, and `decision-traceability`.
- Confirmed the active baseline: latest decision `D-061`, unchanged `P0` backlog centered on MVP launch scope, and next-step guidance pointing to continued ticketing work after the hardened inventory/hold slice.
- Created `session-log-part-16.md` and updated the session-log index because the previous latest part had already exceeded the size threshold.

## Verification

- Manual source-of-truth reread of the required `docs/context/*` files and latest split parts.

## Remaining Follow-Up

- Await the next explicit implementation or review task and synchronize any resulting scope/decision changes back into `docs/context/*`.

## Formalized Context Sync Request (2026-03-21 Baseline Refresh)

## Why This Step

- The user requested another full context synchronization before any new implementation work starts in this chat.
- Governance memory must reflect the fresh baseline because the repository workflow treats `docs/context/*` as the primary source of truth across chats.
- The assistant needs to reconfirm the current decision register, `P0` scope, latest session next-step guidance, and decision-traceability status directly from source documents before proceeding further.

## Scope

- Re-read the required context documents in the prescribed order:
  - product brief
  - tooling stack
  - engineering standards
  - quality rules
  - non-functional requirements
  - architecture overview
  - test strategy
  - decisions log
  - backlog
  - latest session-log entries
  - decision traceability
- Confirm from repository documents:
  - latest decision id
  - current `P0` backlog focus
  - current next step from session memory
  - current execution status of key traceability decisions
- Record the sync in governance memory without changing implementation or backlog scope.

## Explicitly Out Of Scope

- feature implementation, refactoring, or review work
- contract, schema, diagnostics, or runtime configuration changes
- production server actions
- reprioritizing roadmap items beyond the already documented state

## Constraints

- `docs/context/*` remains the primary source of truth.
- Split-index protocol must be respected for `decisions-log`, `session-log`, `decision-traceability`, and `task-request-template`.
- Governance updates must stay concise and sanitized, with no secrets or raw transcript content.

## Acceptance Signals

- The assistant can state the current latest decision id, current `P0` backlog focus, latest session `Next`, and current statuses of the key latest decision-traceability entries directly from repository docs.
- `task-request-template` and `session-log` both reflect that this context sync was completed in the current chat.
- No implementation/document scope changes are introduced unless an inconsistency is found.

## Implementation Outcome

## Delivered

- Re-read the required context documents and the active split parts referenced by the governance indexes.
- Confirmed that the latest decision remains `D-061`, the `P0` backlog still centers on the MVP launch slice headed by login/password + VK-backed auth foundation and downstream ticketing/event operations, and the latest session memory still points to product-scoped ticketing work unless priorities shift.
- Confirmed that the latest decision-traceability entries still show `D-060` as `in-progress` and `D-061` as `done`.

## Verification

- Manual source-of-truth reread of the required `docs/context/*` files plus the latest split parts for governance documents.

## Remaining Follow-Up

- Await the next explicit task and update both code and context documents in the same change if scope or implementation state moves.

## Formalized P0 Request (Public Ticketing Inventory Surface)

## Why This Step

- The synchronized backlog and architecture state still leave audience-facing public ticketing access unfinished even though the derived inventory and protected hold foundation already exist.
- The previous ticketing follow-ups intentionally stopped before unauthenticated/public inventory access, so the next bounded `P0` increment should expose that surface without jumping ahead to checkout, QR issuance, or check-in.
- This slice should keep observability, diagnostics, and Russian-language code comments aligned with the standing repository rules.

## Scope

- Add a public backend route for reading derived inventory of a published public event without session auth.
- Preserve the existing protected hold create/release routes for authenticated users.
- Ensure the public inventory response does not leak another user's active hold identifiers or ownership metadata.
- Add route-level diagnostics and structured logging for the new public inventory surface.
- Extend domain/data ticketing contracts so future audience clients can call the new public inventory API cleanly.
- Add regression coverage for:
  - anonymous read of public published inventory
  - denial for private or otherwise unavailable events
  - non-leakage of active hold ids in the public response

## Explicitly Out Of Scope

- checkout/order capture
- QR issuance or check-in
- sold-out automation
- public event discovery/listing by city/date/price
- mobile UI for audience ticket selection

## Constraints

- `docs/context/*` remains the source of truth and must be updated together with implementation state.
- New and materially changed code must keep Russian responsibility/flow comments.
- Backend observability for the new route must go through the existing sanitized diagnostics path with `requestId`, `stage`, and bounded metadata.

## Acceptance Signals

- Anonymous clients can fetch inventory for a `published + public` event through a dedicated public API route.
- Public responses never expose `active_hold_id` or current-user ownership metadata for holds created by authenticated users.
- Private/unpublished event access stays blocked behind the same safe `404` contract.
- Tests protect the new route and data-sanitization behavior.

## Implementation Outcome

## Delivered

- Added public backend route `GET /api/v1/public/events/{eventId}/inventory` for anonymous reads of published public event inventory.
- Kept hold create/release on the existing authenticated surface and preserved safe `404` behavior for unavailable/private events.
- Extended shared/data ticketing contracts with explicit `listPublicInventory` support for future audience clients.
- Added structured logger + sanitized diagnostics stages for the new public inventory success/rate-limit flow.
- Added route coverage for anonymous public inventory access, private-event denial, diagnostics capture, and active-hold metadata sanitization.

## Verification

- `./gradlew :domain:ticketing:allTests :server:test --tests com.bam.incomedy.server.ticketing.TicketingRoutesTest`
- `./gradlew :server:test --tests com.bam.incomedy.server.ticketing.TicketingRoutesTest :data:ticketing:compileKotlinMetadata :shared:compileKotlinMetadata :composeApp:compileDebugKotlin`

## Remaining Follow-Up

- Add public event discovery/listing by city/date/price so audience clients can obtain `eventId` without organizer-only surfaces.
- Continue with checkout/order capture after the public discovery surface is defined.

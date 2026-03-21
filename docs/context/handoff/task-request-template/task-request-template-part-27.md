# Task Request Template Part 27

## Formalized P0 Request (Public Event Discovery Surface)

## Why This Step

- The previous ticketing increment added anonymous public inventory reads, but audience clients still have no public route to discover eligible events by `city/date/price` and therefore no clean way to obtain `eventId`.
- The product backlog keeps event discovery inside `P0`, so the next bounded step should expose a public event listing surface before checkout/order capture begins.
- This slice should stay backend-first, preserve sanitized diagnostics, and avoid leaking organizer-only event fields into the audience API.

## Scope

- Add a public backend route for listing `published + public` events for audience discovery.
- Support bounded query filters for:
  - `city`
  - `date_from`
  - `date_to`
  - `price_min_minor`
  - `price_max_minor`
- Return an audience-safe event summary with venue/city/date/sales/price-range information.
- Extend domain/data event contracts so future audience clients can call the public discovery API directly.
- Add structured logging and sanitized diagnostics for the new public route.
- Add regression coverage for:
  - anonymous public event listing
  - city/date/price filtering
  - diagnostics capture with `requestId` correlation

## Explicitly Out Of Scope

- checkout/order capture
- QR issuance or check-in
- organizer event management changes
- audience mobile UI
- recommendation/ranking logic beyond deterministic filtering

## Constraints

- `docs/context/*` remains the source of truth and must be updated in the same change.
- Business-facing event discovery contracts must live in `domain/*`, not in `feature/*`.
- New backend diagnostics must stay sanitized and low-cardinality.
- Repository comments for new or materially changed code must stay in Russian.

## Acceptance Signals

- Anonymous clients can list public events through a dedicated public API.
- Filters by `city/date/price` work deterministically and are protected by tests.
- The public response exposes only audience-safe event summary fields.
- Documentation and governance memory reflect the new discovery surface and its remaining follow-up.

## Implementation Outcome

## Delivered

- Added public backend route `GET /api/v1/public/events` for anonymous discovery of `published + public` events.
- Added bounded query filters `city`, `date_from`, `date_to`, `price_min_minor`, and `price_max_minor` with route-level validation.
- Added audience-safe discovery DTOs and shared `domain/data` event contracts so future clients can call the public catalog without organizer-only models.
- Added structured logger and sanitized diagnostics for the public discovery route with `requestId` correlation and low-cardinality filter metadata.
- Added route coverage for anonymous listing, deterministic `city/date/price` filtering, and diagnostics capture.

## Verification

- `./gradlew :domain:event:allTests :data:event:compileKotlinMetadata :server:test --tests com.bam.incomedy.server.events.EventRoutesTest`

## Remaining Follow-Up

- Add public event detail and audience mobile UI surfaces on top of the new catalog route.
- Continue with checkout/order capture, QR issuance, and check-in after the public discovery entry point.

## Formalized P0 Request (Checkout Order Foundation)

## Why This Step

- Public catalog and inventory entry points now exist, but the audience flow still stops at temporary holds and cannot transition into a stable checkout entity.
- The product backlog keeps `ticket checkout` inside `P0`, while the tooling stack still lists PSP providers as candidates rather than confirmed production integrations.
- The next bounded step should therefore introduce a provider-agnostic internal `TicketOrder` foundation that safely converts active holds into a checkout-ready order without pretending that YooKassa/CloudPayments are already rollout-ready.

## Scope

- Add a backend ticket-order entity that groups one or more active seat holds of the current user for one event.
- Add a protected backend route for creating a checkout order from active hold ids.
- Persist order lines, total amount, and checkout expiration so pending orders do not lock inventory forever.
- Extend ticketing persistence so inventory units can remain blocked during checkout and automatically recover after order expiration.
- Extend shared `domain/data:ticketing` contracts so future clients can call the create-order API cleanly.
- Add structured logging and sanitized diagnostics for the checkout-order route.
- Add regression coverage for:
  - successful order creation from active holds
  - rejection of чужих/неактивных hold-ов
  - release of inventory after pending order expiration
  - migration coverage for new ticket-order tables

## Explicitly Out Of Scope

- real PSP integration or provider-specific checkout URLs
- payment confirmation webhooks
- QR issuance and check-in
- refunds and cancellations
- audience mobile UI

## Constraints

- `docs/context/*` remains the source of truth and must be updated in the same change.
- The slice must stay provider-agnostic because the payments provider is still only a candidate in `tooling-stack.md`.
- Inventory must not become permanently blocked if checkout is abandoned.
- New backend flow code and diagnostics hooks must keep Russian comments and sanitized low-cardinality metadata.

## Acceptance Signals

- Authenticated audience can create a stable checkout order from active holds of one event.
- Pending checkout orders block the same inventory from new holds until they expire.
- Expired pending orders release inventory back to its base availability automatically.
- Documentation, API contract, governance memory, and tests reflect the new checkout foundation and clearly note that PSP integration is still pending.

## Implementation Outcome

## Delivered

- Added provider-agnostic `TicketOrder` models and shared `createTicketOrder` contract in `domain/data:ticketing`.
- Added protected backend route `POST /api/v1/events/{eventId}/orders` with bounded payload validation, rate limiting, structured logging, and sanitized diagnostics.
- Added persistence for `ticket_orders` and `ticket_order_lines`, plus `held -> pending_payment` inventory transition and automatic release to base availability after expired pending checkout.
- Added regression coverage for successful order creation, rejection of чужих/неактивных hold-ов, pending-order expiry recovery, and migration verification for the new order tables.

## Verification

- `./gradlew :domain:ticketing:allTests :data:ticketing:compileKotlinMetadata :shared:compileKotlinMetadata :composeApp:compileDebugKotlin :server:test --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest' --tests 'com.bam.incomedy.server.ticketing.TicketingRoutesTest'`

## Remaining Follow-Up

- Decide the active PSP path and implement checkout handoff plus payment-confirmation/webhook processing on top of `TicketOrder`.
- Add QR issuance and check-in only after the paid order lifecycle is introduced.

## Formalized P0 Request (YooKassa Checkout Handoff Foundation)

## Why This Step

- `TicketOrder` now exists, but the audience flow still stops at an internal pending order and has no real PSP handoff.
- The tooling stack previously left the PSP undecided, which blocked meaningful checkout progress; this slice starts by fixing that governance gap and then adds the first real payment-provider bridge.
- The next bounded increment should expose server-created YooKassa redirect checkout on top of `TicketOrder`, while keeping order/inventory semantics provider-agnostic and leaving webhook confirmation plus ticket issuance for the next slice.

## Scope

- Confirm `YooKassa` as the active PSP integration target in context docs and decision traceability.
- Add server runtime config for YooKassa credentials and return URL.
- Add a backend payment gateway adapter that creates YooKassa payments through server-side API calls with idempotence and bounded metadata.
- Add persistence for a checkout-session/payment-attempt entity linked to `TicketOrder`.
- Add a protected backend route that starts checkout for an existing `awaiting_payment` order of the current user and returns the PSP confirmation URL.
- Extend shared `domain/data:ticketing` contracts so future clients can call the checkout-handoff API cleanly.
- Add structured logging and sanitized diagnostics for checkout-session creation.
- Add regression coverage for:
  - successful checkout-session creation
  - repeated start on the same order returning the active session idempotently
  - rejection of чужого/expired/non-awaiting order-а
  - migration coverage for the new payment-attempt table

## Explicitly Out Of Scope

- YooKassa webhook processing
- transition to `paid` / ticket issuance / QR generation
- refunds and cancellations after successful capture
- mobile UI for checkout return handling
- CloudPayments implementation

## Constraints

- `docs/context/*` remains the source of truth and must be updated in the same change.
- The PSP-specific layer must stay isolated behind server-side adapter abstractions so `TicketOrder` and inventory invariants remain provider-agnostic.
- Payment diagnostics must stay sanitized and low-cardinality; no secrets, auth headers, or raw provider payload dumps in logs.
- Runtime config must not make checkout routes look available when YooKassa credentials are absent.

## Acceptance Signals

- Authenticated audience can start real YooKassa checkout for their own pending order through a protected backend route.
- Repeated checkout start on the same active order is idempotent and does not create duplicate provider payments.
- New payment-attempt persistence and route behavior are covered by tests and migration verification.
- Context docs clearly state that YooKassa handoff exists, while webhook confirmation and ticket issuance remain the next follow-up.

## Formalized Documentation Structure Request (Bootstrap + Handoff Cleanup)

## Why This Step

- `docs/context` already preserves history well, but new-chat synchronization became too expensive because bootstrap context, task template, historical task log, and diagnostics runbook concerns drifted into the same handoff layer.
- The user explicitly requested a structural cleanup focused on continuity between chats, organization of work, and logging discipline rather than another product feature increment.
- The highest-leverage documentation change is to reduce onboarding cost without losing governance memory or breaking existing historical references.

## Scope

- Add a compact bootstrap snapshot file for new-chat context sync.
- Add a root `docs/README.md` that explains the roles of `context/`, `standup-platform-ru/`, and `project-reference/`.
- Keep `handoff/task-request-template.md` as the reusable template only.
- Introduce a dedicated `handoff/task-request-log.md` index for historical formalized requests while preserving existing part-file paths.
- Move server diagnostics operational instructions out of the handoff template into a dedicated engineering runbook.
- Update context protocol, chat handoff template, governance rules, and decision traceability to match the new document roles.

## Explicitly Out Of Scope

- rewriting or relocating the full historical task-request archive
- large-scale rewrite of existing product or engineering source-of-truth content
- changing the active `P0` product delivery sequence itself

## Constraints

- Existing historical references must remain understandable and preferably path-stable.
- The new bootstrap layer must reduce sync cost without weakening source-of-truth discipline.
- Governance memory still needs decisions, task history, and session logs to stay analyzable after the reorganization.

## Acceptance Signals

- A new chat can start from one compact current-state file before reading deeper context.
- The active task template is no longer an archive index.
- The handoff message points to a dedicated diagnostics runbook instead of embedding the full server-ops procedure inline.
- Top-level docs navigation makes the three documentation layers explicit.

## Implementation Outcome

## Delivered

- Added `docs/README.md` as the root documentation map and `docs/context/00-current-state.md` as the bootstrap snapshot for new chats.
- Added `docs/context/engineering/server-diagnostics-runbook.md` and removed the embedded diagnostics runbook from `handoff/chat-handoff-template.md`.
- Reworked `docs/context/handoff/task-request-template.md` into a reusable task-intake template and added `docs/context/handoff/task-request-log.md` as the historical formalized-request index.
- Updated `docs/context/README.md`, `docs/context/handoff/context-protocol.md`, governance rules/checklists, and decision traceability; accepted `D-063` to record the new document-role split.

## Verification

- Manual context-link review across `docs/README.md`, `docs/context/00-current-state.md`, `docs/context/handoff/context-protocol.md`, `docs/context/handoff/chat-handoff-template.md`, and `docs/context/handoff/task-request-log.md`

## Remaining Follow-Up

- Update the derived HTML handbook pages under `docs/project-reference/` so their newcomer-facing guidance mirrors the new bootstrap/template/log split.
- Keep `00-current-state.md` disciplined; if it starts growing into another long-form document, split or trim it before it becomes a second archive.

## Formalized P0 Request (YooKassa Payment Confirmation + Order Status Polling)

## Why This Step

- The local repository already had YooKassa checkout-session handoff in progress, but the audience flow still had no trustworthy way to confirm payment completion or re-read the resulting order state after returning from PSP.
- The current `00-current-state.md` and session-log next step explicitly called for payment-confirmation/webhook design and implementation on top of `TicketOrder` before QR issuance/check-in.
- This increment needed to stay backend-first, keep order/inventory semantics provider-agnostic, and make webhook handling safe enough for retries and late provider notifications.

## Scope

- Add an authenticated backend route for reading the current user-owned `TicketOrder` by id.
- Add a public backend payments webhook route for YooKassa notifications.
- Verify webhook source IP against published YooKassa ranges before processing.
- Re-check the current payment snapshot from YooKassa instead of trusting the raw webhook body.
- Add idempotent persistence transitions for:
  - `waiting_for_capture`
  - `paid` + `sold` inventory
  - `canceled` + released inventory
- Extend shared `domain/data:ticketing` contracts with order-status polling.
- Add structured logging, sanitized diagnostics, and regression coverage for the new surfaces.

## Explicitly Out Of Scope

- ticket issuance records
- QR generation
- checker scan/check-in flows
- sold-out automation
- refund/capture orchestration beyond current YooKassa status handling
- mobile checkout-return UI

## Constraints

- `docs/context/*` remains the source of truth and must be updated in the same change.
- Webhook processing must be idempotent and must not trust provider payload fields without provider-side recheck.
- New diagnostics metadata must stay low-cardinality and sanitized.
- New or materially changed repository code must keep Russian comments.
- Unsafe late-success cases must not silently risk double-sell.

## Acceptance Signals

- Authenticated clients can read a `TicketOrder` after checkout handoff through `GET /api/v1/orders/{orderId}`.
- YooKassa `payment.succeeded` and `payment.canceled` notifications can transition local order/session/inventory state safely.
- Duplicate webhook delivery is idempotent.
- Non-YooKassa source IPs are rejected before webhook processing.
- Context docs and governance memory reflect that payment confirmation exists and that QR/check-in is the next bounded step.

## Implementation Outcome

## Delivered

- Added authenticated `GET /api/v1/orders/{orderId}` plus shared `domain/data:ticketing` order polling contract.
- Added public `POST /api/v1/webhooks/payments` for YooKassa notifications with bounded payload parsing and source-IP allowlisting.
- Extended the PSP adapter so webhook handling re-reads the current YooKassa payment snapshot (`status`, `amount`, `metadata.order_id`, `metadata.event_id`) instead of trusting the raw webhook payload.
- Added idempotent repository transitions for `waiting_for_capture`, `paid`/`sold`, and `canceled`/released inventory states.
- Added explicit `recovery_required` webhook outcome for unsafe late-success situations where auto-applying payment would risk inconsistent inventory.
- Added route/provider regression coverage for order polling, successful/canceled webhook application, duplicate `payment.succeeded`, and forbidden source IPs.

## Verification

- `./gradlew :domain:ticketing:allTests :data:ticketing:compileKotlinMetadata :server:test --tests 'com.bam.incomedy.server.ticketing.TicketingRoutesTest' --tests 'com.bam.incomedy.server.payments.yookassa.YooKassaCheckoutGatewayTest'`

## Remaining Follow-Up

- Introduce paid ticket issuance records plus QR generation on top of confirmed `paid` orders.
- Add checker-facing check-in routes and scan validation after QR issuance exists.
- Revisit sold-out automation and durable recovery-incident handling if payment volume makes in-memory diagnostics insufficient.

## Formalized Governance/Runtime Request (Disable Default YooKassa Activation)

## Why This Step

- The repository now contains a concrete YooKassa implementation, but the user explicitly asked not to treat that fact as automatic provider adoption.
- The current runtime still inferred activation from env presence, which made stale/partial PSP config capable of affecting server startup and contradicted the desired “implemented, but not default” posture.
- The user also required documentation that similar external-provider choices must be explicitly confirmed before they are considered active.

## Scope

- Keep YooKassa implementation code in the repository.
- Change backend config so YooKassa stays disabled unless explicitly enabled.
- Add test coverage for disabled-by-default and explicit-enable config behavior.
- Update runtime/env docs and context governance docs to state that external-provider adoption must be explicitly confirmed by the user.
- Synchronize decisions, traceability, and current-state snapshot if the governance rule changes.

## Explicitly Out Of Scope

- deleting YooKassa implementation code
- choosing a different PSP
- removing completed checkout/webhook foundation work
- ticket issuance / QR / check-in implementation

## Constraints

- Server must start cleanly when YooKassa env is absent or partially filled but explicit enable is not set.
- Existing provider-specific code should remain available for future activation.
- Governance docs must clearly distinguish implemented integrations from user-confirmed default providers.

## Acceptance Signals

- YooKassa no longer activates implicitly from env presence.
- `YOOKASSA_ENABLED=false` keeps server startup unaffected by optional PSP settings.
- Documentation states that external-provider adoption/default activation requires explicit user confirmation.
- Governance memory records the new rule as the latest accepted decision.

## Implementation Outcome

## Delivered

- Added explicit `YOOKASSA_ENABLED` gating in backend config with `false` default.
- Added `AppConfigTest` coverage proving that YooKassa stays disabled by default and still validates mandatory fields when explicitly enabled.
- Updated `server/.env.example`, `deploy/server/.env.example`, and `server/README.md` to document disabled-by-default PSP activation.
- Added governance rule `D-064`, superseded the earlier “active YooKassa by default” wording, and updated `00-current-state`, tooling-stack, engineering standards, quality rules, decision traceability, and session memory.

## Verification

- `./gradlew :server:test --tests 'com.bam.incomedy.server.config.AppConfigTest'`

## Remaining Follow-Up

- If the user later explicitly confirms a PSP choice, reactivate that provider through the documented flag/config path and record a new accepted decision for the active default runtime.

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

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

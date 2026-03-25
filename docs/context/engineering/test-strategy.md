# Test Strategy

## Test Pyramid

- Unit tests: primary layer, fast and deterministic.
- Integration tests: repository/data/infrastructure behaviors with realistic boundaries.
- End-to-end/smoke tests: critical user flows on release branches.

## Mandatory Coverage by Feature

- Happy path.
- Error path.
- At least one edge case.
- Regression test for each fixed production bug.

## Integration and Contract Expectations

- Contract tests are required for the current auth API surface and for future payment/webhook integrations.
- Integration tests should cover concurrency and idempotency risks for ticketing, payments, and check-in; the current ticketing foundation already requires coverage for double-reserve conflicts and hold expiry/release recovery.
- Backend persistence changes must include migration-path verification for both clean schema creation and upgrade of a legacy initialized schema.
- Venue/hall-template backend changes must cover route behavior, layout contract validation, and migration-path verification for the organizer venue slice.
- Event/EventHallSnapshot backend changes must cover route behavior, lifecycle transition validation, detail/update override validation, snapshot freeze invariants, and migration-path verification for the organizer event slice.
- Comedian applications backend changes must cover comedian submit happy path, organizer review/list authorization, status transition validation, request-id-correlated diagnostics, and migration-path verification for the applications slice.
- Lineup backend changes must cover approved-application materialization into draft lineup entries, organizer/host reorder authorization, explicit contiguous `order_index` validation, live-stage mutation authorization/transition validation (`up_next` / `on_stage` uniqueness plus terminal status guards), request-id-correlated diagnostics, and migration-path verification for the lineup slice when schema changes are introduced.
- Public event discovery backend changes must cover audience-safe summary shaping, `city/date/price` filtering, and request-id-correlated diagnostics for the anonymous route.
- Ticketing inventory/hold/order/checkout backend changes must cover derivation from `EventHallSnapshot` plus event-local overrides, hold conflict invariants, checkout-order creation from active hold-ов, authenticated order-status reads, paid-order ticket issuance idempotency, authenticated `GET /api/v1/me/tickets`, checker-only `POST /api/v1/checkin/scan` with duplicate-scan handling, PSP handoff session creation with idempotent reuse, webhook-driven payment confirmation/cancellation with provider-status recheck plus source validation, duplicate-webhook idempotency, expiry/release semantics for both hold-ов and pending order-ов, stale-sync avoidance on unchanged reads, and migration-path verification for the ticketing foundation slice.
- Smoke tests on release branches must validate the currently shipped critical flows.
- Mobile UI changes in active product flows must have executable platform UI coverage where in-repo infrastructure exists.
- Auth/session boundary refactors must execute `:domain:auth:allTests`, `:domain:session:allTests`, `:core:backend:allTests`, `:data:auth:allTests`, `:data:session:allTests`, `:shared:allTests`, `:composeApp:testDebugUnitTest`, and `:composeApp:compileDebugKotlin` before completion.

## MVI-Specific Expectations

- ViewModel tests must assert:
  - state transitions,
  - effect emission,
  - deterministic handling of intents.

## CI Expectations

- Test suite runs on pull request.
- Merge is blocked on failing required tests.
- Flaky tests must be fixed or quarantined with explicit follow-up.
- Android repository changes that affect `composeApp`/shared mobile flows must execute `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin` in GitHub Actions CI.

## Verification Memory

- Current executable coverage map and recent verification outcomes now live in [verification-memory.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/verification-memory.md).
- Keep this document strategy-level; do not append run-by-run verification history here.

## High-Risk Scenario Set

- Double-booking race on seat/inventory transitions.
- Duplicate webhook delivery.
- Expired seat hold release.
- Abandoned checkout order expiration.
- Duplicate PSP session creation for one order.
- Refund after check-in.
- Role escalation / unauthorized finance access.
- Live lineup reorder conflict.

## Ownership

- Feature owner is responsible for keeping tests updated with behavior changes.

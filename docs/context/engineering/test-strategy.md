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
- Integration tests should cover concurrency and idempotency risks for ticketing, payments, and check-in once those domains are introduced.
- Smoke tests on release branches must validate the currently shipped critical flows.

## MVI-Specific Expectations

- ViewModel tests must assert:
  - state transitions,
  - effect emission,
  - deterministic handling of intents.

## CI Expectations

- Test suite runs on pull request.
- Merge is blocked on failing required tests.
- Flaky tests must be fixed or quarantined with explicit follow-up.

## High-Risk Scenario Set

- Double-booking race on seat/inventory transitions.
- Duplicate webhook delivery.
- Expired seat hold release.
- Refund after check-in.
- Role escalation / unauthorized finance access.
- Live lineup reorder conflict.

## Ownership

- Feature owner is responsible for keeping tests updated with behavior changes.

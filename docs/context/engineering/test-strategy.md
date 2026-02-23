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

## MVI-Specific Expectations

- ViewModel tests must assert:
  - state transitions,
  - effect emission,
  - deterministic handling of intents.

## CI Expectations

- Test suite runs on pull request.
- Merge is blocked on failing required tests.
- Flaky tests must be fixed or quarantined with explicit follow-up.

## Ownership

- Feature owner is responsible for keeping tests updated with behavior changes.

# Quality Rules

This document defines mandatory delivery and quality controls for InComedy.

## Definition of Done (DoD)

- A task is complete only when code, tests, and relevant `docs/context/*` updates are included.
- CI checks for the changed scope must pass before merge.
- Mandatory security review is part of DoD for every task, even if the change is not explicitly security-related.

## Error Handling

- Use a unified error model across data/domain/presentation layers.
- No silent failures: exceptions must be either handled with fallback or mapped to explicit feature errors.
- UI must receive understandable error states/messages via `MVI` state/effect contracts.

## API Contracts and Versioning

- Backend endpoints and payloads must be defined and updated via explicit contracts.
- Breaking API changes require versioning or migration strategy before rollout.
- Backend database schema changes require versioned forward migrations plus a rollout/backfill strategy for existing environments before merge.

## CI Quality Gates

- Pull request merge is blocked when:
  - tests fail,
  - static analysis reports critical issues,
  - required checks are not executed.

## Minimum Test Set Per Feature

- Required test coverage for each feature:
  - `happy path`,
  - `error path`,
  - at least one `edge case`,
  - regression test for each fixed production bug.

## Dependency and Layer Rules

- Enforce `presentation -> domain -> data` dependency direction.
- External SDKs/APIs must be wrapped behind adapters/interfaces.

## UI State Consistency

- Single source of truth per screen state.
- Reducer/state transition logic should stay deterministic and testable.

## Code Style and Structure

- Follow project naming conventions and module/package boundaries.
- Avoid oversized classes/files that mix unrelated responsibilities.
- Do not embed primary database schema DDL in mutable application startup/service logic once a migration system is in place.
- Code changes must include required repository code comments at class/object/interface, method/function, and field/property level according to `engineering-standards.md`.

## Observability

- Log key business and technical events with consistent structure.
- Include trace identifiers where applicable to simplify diagnostics.
- For auth callbacks/exchange, logs are mandatory on both backend and mobile entry points.
- Auth logs must include provider and stage, but must not include access/refresh tokens, bot tokens, or other secrets.
- Live-environment backend troubleshooting must have an operator-only sanitized retrieval path for recent diagnostic events; debugging must not require exposing raw server logs to clients.
- Diagnostics retention and payload shape must stay bounded and low-cardinality to avoid turning troubleshooting data into an uncontrolled log sink.

## Security and Privacy

- Never store secrets in repository.
- Mask or avoid logging personal/sensitive user data.
- Apply secure token/session handling in all auth flows.
- Client-side auth/session tokens must not persist in plain local storage (`SharedPreferences`, `UserDefaults`) outside controlled migration.
- Token refresh must use refresh-token rotation (one-time refresh token consumption and issuance of a new refresh token).
- Remote datastore transport must be secured by default (DB TLS / Redis TLS) with any insecure override treated as temporary exception.
- Auth callback payload fields must be validated for format, length, and protocol constraints before verification/storage.
- Any discovered vulnerability must be immediately reported to product owner and tracked in `docs/context/product/risk-log.md` with remediation plan, owner, and target date.

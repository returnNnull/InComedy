# Quality Rules

This document defines mandatory delivery and quality controls for InComedy.

## Definition of Done (DoD)

- A task is complete only when code, tests, and relevant `docs/context/*` updates are included.
- Every meaningful task must update `docs/context/governance/session-log.md` with a brief trace of the conversation/work path, not only the final code result.
- If the task changes the latest decision, current `P0` focus, next step, or active cross-cutting constraints, `docs/context/00-current-state.md` must be updated in the same change.
- CI checks for the changed scope must pass before merge.
- Mandatory security review is part of DoD for every task, even if the change is not explicitly security-related.
- Verification, test harness, simulator/runtime, and analogous execution issues discovered while completing the current task are part of that same task by default; they must not be reclassified as a separate blocker/task/epic unless a true external blocker or explicit user decision boundary is reached.
- New automation-driven implementation runs must not end with `partial`; after bounded local repair attempts, the run must finish either with a completed outcome or with a finished `docs_only` blocker record that states the exact external blocker, evidence, and next action.

## Error Handling

- Use a unified error model across data/domain/presentation layers.
- No silent failures: exceptions must be either handled with fallback or mapped to explicit feature errors.
- UI must receive understandable error states/messages via `MVI` state/effect contracts.

## API Contracts and Versioning

- Backend endpoints and payloads must be defined and updated via explicit contracts.
- Breaking API changes require versioning or migration strategy before rollout.
- Backend database schema changes require versioned forward migrations plus a rollout/backfill strategy for existing environments before merge.
- Replacing an active third-party auth/payment provider flow requires documented validation in the target launch market plus an explicit rollback plan before merge.

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

- Enforce layer intent together with compile-time direction:
  - `feature/presentation -> domain`
  - `data -> domain`
  - `core` is reusable technical support, not a business layer
  - `domain` must stay independent from `data` and `feature`
  - `data` must not depend on `feature/presentation`
- External SDKs/APIs must be wrapped behind adapters/interfaces.
- `data -> data` dependencies are an exception and require promoting the shared concern into `core/*` or another dedicated shared module unless a documented decision explicitly allows otherwise.
- Business contracts, enums, ports, and use cases must live in `domain/*`; if such code is discovered under `feature/*`, it should be treated as architectural debt and moved.
- Region- or provider-specific external flows must not become the default production path until staging/device smoke checks confirm they operate in the current target market.
- When a provider's live browser behavior requires an approved first-party web origin, mobile clients must open a backend-issued first-party launch surface instead of direct raw provider URLs.

## UI State Consistency

- Single source of truth per screen state.
- Reducer/state transition logic should stay deterministic and testable.

## Code Style and Structure

- Follow project naming conventions and module/package boundaries.
- Avoid oversized classes/files that mix unrelated responsibilities.
- Do not embed primary database schema DDL in mutable application startup/service logic once a migration system is in place.
- Code changes must include required repository code comments at class/object/interface, method/function, and field/property level according to `engineering-standards.md`; those comments must be written in Russian.
- Tasks that introduce or materially reshape backend flow code are not done until the resulting code comments explain the flow, boundaries, and observability hooks well enough for future chats to continue the work safely.

## Observability

- Log key business and technical events with consistent structure.
- Include trace identifiers where applicable to simplify diagnostics.
- For auth callbacks/exchange, logs are mandatory on both backend and mobile entry points.
- Auth logs must include provider and stage, but must not include access/refresh tokens, bot tokens, or other secrets.
- Live-environment backend troubleshooting must have an operator-only sanitized retrieval path for recent diagnostic events; debugging must not require exposing raw server logs to clients.
- Diagnostics retention and payload shape must stay bounded and low-cardinality to avoid turning troubleshooting data into an uncontrolled log sink.
- New backend logging for live troubleshooting must be wired into the sanitized diagnostics system; plain console/container logging alone does not satisfy observability DoD.

## Governance Traceability

- The repository must preserve a short analyzable history of collaboration decisions, not just final artifacts.
- `docs/context/00-current-state.md` is the mandatory bootstrap snapshot for new chats and must stay aligned with the latest decisions and active delivery focus.
- External provider choices must not be promoted to confirmed/default runtime status without explicit user confirmation; until then they should stay documented as candidate or disabled-by-default implementations.
- Existing code, sample config, or earlier assistant guidance must not be interpreted as evidence that the user approved a provider choice.
- `docs/context/governance/session-log.md` is the mandatory place for a concise per-task/per-session summary of:
  - what the user asked for,
  - what changed during the discussion,
  - which decisions were taken,
  - what remains next.
- `docs/context/handoff/task-request-template.md` should stay a reusable template only, while historical formalized requests and outcomes belong in `docs/context/handoff/task-request-log.md`.
- Scheduled `InComedy Executor` runs must follow `docs/context/handoff/automation-executor-prompt.md`, and automation TOML prompts should link to that document instead of carrying a divergent inline copy of the same rules.
- This trace must be concise enough to scan quickly, but specific enough that a later chat can understand the work trajectory without replaying the full conversation.

## Security and Privacy

- Never store secrets in repository.
- Mask or avoid logging personal/sensitive user data.
- Apply secure token/session handling in all auth flows.
- Client-side auth/session tokens must not persist in plain local storage (`SharedPreferences`, `UserDefaults`) outside controlled migration.
- Token refresh must use refresh-token rotation (one-time refresh token consumption and issuance of a new refresh token).
- Remote datastore transport must be secured by default (DB TLS / Redis TLS) with any insecure override treated as temporary exception.
- Auth callback payload fields must be validated for format, length, and protocol constraints before verification/storage.
- Any discovered vulnerability must be immediately reported to product owner and tracked in `docs/context/product/risk-log.md` with remediation plan, owner, and target date.

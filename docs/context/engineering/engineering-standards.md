# Engineering Standards

This document defines mandatory engineering rules for InComedy.

## Architecture

- Project architecture style: `Clean` (layered boundaries and dependency direction from outer layers to domain abstractions).
- Module taxonomy is mandatory:
  - `core/*` for shared technical primitives and cross-cutting infrastructure only;
  - `domain/*` for business entities, ports, use cases, and feature-neutral contracts;
  - `data/*` for implementations of domain ports and infrastructure-backed adapters;
  - `feature/*` for presentation, orchestration, feature-local reducers/ViewModels, and UI-adjacent pure helpers.
- Feature modules should keep clear separation of concerns and must not be used as the place where domain contracts permanently live.
- Dependency injection standard: `Koin`.
- Dependencies should be wired via feature modules and resolved through shared DI entry points (no ad-hoc object factories).
- Compile-time dependency rules are mandatory:
  - `domain` must not depend on `data` or `feature`;
  - `data` may depend on `domain` and `core`, but must not depend on `feature/presentation`;
  - `feature` may depend on `domain` and `core`, but must not be depended on by `data`;
  - if multiple `data` modules need the same code, that code must be promoted into `core/*` or another dedicated shared module instead of introducing `data -> data` coupling by default.
- `core` must stay technical: if a module starts speaking in product terms like `workspace`, `invitation`, `ticket`, or `role policy`, it belongs in `domain`, not in `core`.

## Presentation

- All `ViewModel` implementations must follow `MVI`.
- UI must be implemented separately per platform:
  - Android: Jetpack Compose
  - iOS: SwiftUI
- Shared feature modules should contain platform-agnostic logic (domain/use-cases/ViewModel/contracts), not platform UI widgets.
- App composition layers such as `shared` may assemble `feature + data + domain` modules, but they must not become a dumping ground for business contracts that belong in `domain/*`.
- Android navigation must be organized as a root `NavHost` with feature-owned nested subgraphs (`NavGraphBuilder` extensions), not a single flat route list.
- Each Android feature should keep its own navigation package (`.../feature/<name>/navigation/*`) with graph and destination declarations.
- iOS navigation must be organized as a root app graph container (app-level navigator) with feature-owned graph views, not direct screen switching in `ContentView`.
- Each iOS feature should keep navigation artifacts inside `iosApp/iosApp/Features/<Feature>/Navigation/*`.
- iOS integration with shared `ViewModel` must use a bridge + Swift `ObservableObject` adapter pattern:
  - shared bridge exposes `observeState`, `observeEffect`, `dispose`
  - Swift adapter owns bridge lifecycle and publishes UI-friendly state
  - bridge implementations should reuse shared base abstractions (`BaseFeatureBridge`, `BridgeHandle`)
- KMP ViewModel integration pattern is mandatory:
  - `commonMain`: shared ViewModel is a plain class exposing `StateFlow<State>` + `SharedFlow/Flow<Event>`
  - Android: wrap shared ViewModel in native AndroidX `ViewModel`; observe state via `collectAsStateWithLifecycle()`
  - iOS: wrap shared ViewModel in native `ObservableObject`; map state to `@Published` and keep explicit subscription disposal in lifecycle (`deinit`/view lifecycle)
- App-level session and authorized user profile must be stored in a shared session-focused ViewModel (not only in auth-screen wrapper state) so feature graphs can consume it as a single source of truth.
- Each feature should define explicit:
  - `Intent` (user/action inputs),
  - `State` (single source of truth for UI),
  - `Effect` (one-time side effects such as navigation/toast).

## Testability

- Code must be designed for high testability (dependency inversion, minimal hidden state, deterministic behavior).
- Business logic should be isolated from framework code when possible.
- Side effects (network, storage, time, random) must be abstracted behind interfaces.

## Code Documentation

- Code comments are mandatory for repository code at class/object/interface level, method/function level, and field/property level.
- Comments must explain purpose and responsibility, not restate syntax mechanically.
- Repository code comments must be written in Russian; short English product/SDK/API terms may appear only when needed as exact technical names inside otherwise Russian explanations.
- New code and materially changed code are incomplete until the affected classes/objects/interfaces, methods/functions, and meaningful fields/properties are brought into compliance with this comment rule.
- Backend handlers, services, diagnostics helpers, and integration adapters must be commented clearly enough that a new chat can understand the production flow and observability points without reverse-engineering the implementation first.
- When editing an existing file, bring touched classes and their methods/properties into compliance with this documentation rule in the same change.

## Observability

- Auth and payment-related flows must emit structured logs on every critical stage:
  - flow started,
  - external callback received,
  - verification/exchange success,
  - verification/exchange failure.
- Every backend auth request log must include request trace identifier (`X-Request-ID`/call-id).
- Operator-facing backend diagnostics must be retrievable through a sanitized, token-protected mechanism; raw server logs must not be the only debugging path for live environments.
- Backend changes that add or alter production-significant flows must use the bounded sanitized diagnostics system for structured stage logging; ad-hoc `println`, raw container logs, or unstructured logger output are secondary only and do not satisfy the primary observability requirement by themselves.
- Diagnostics events should carry stable low-cardinality metadata (`stage`, `provider`, `requestId`, safe outcome fields) so server behavior can be correlated with client logs and request ids without exposing secrets.
- Mobile/shared clients that call backend APIs must surface backend request correlation identifiers strongly enough to match device logs with server diagnostics.
- Logs must never include secrets or raw auth tokens.

## Governance Memory

- `docs/context/00-current-state.md` must stay as a compact bootstrap snapshot of the latest decision id, current `P0` focus, next step, latest relevant part files, and active cross-cutting constraints.
- New and materially updated project documentation in `docs/context/*`, `docs/README.md`, and adjacent governance/handoff indexes must be written in Russian; exact technical terms, API names, and historical untouched entries may remain in English only when needed.
- Repeated technical problems and confirmed repair paths must be recorded in `docs/context/engineering/issue-resolution-log.md` so future chats do not re-derive the same troubleshooting path from scratch.
- Each meaningful user task/session must leave a concise written trace in `docs/context/governance/session-log.md` (latest part file).
- Decisions that move an external auth/payment/push/PSP provider into the active, default, or confirmed path must be explicitly confirmed by the user before docs/runtime are updated to treat that provider as adopted.
- Assistant inference, previously implemented code, draft docs, or example env/config blocks do not count as user confirmation of an external provider choice.
- Session-log entries must summarize the conversation/work path in compact analytical form, not as a raw transcript.
- Minimum session-log shape remains:
  - `Context`
  - `Changes`
  - `Decisions`
  - `Next`
- Entries must capture request evolution when scope changes during the chat, so later reviews can reconstruct why the implementation moved in a certain direction.
- Session-log entries must stay sanitized:
  - no secrets,
  - no raw tokens,
  - no unnecessary verbatim user transcript dumps.
- Major tasks should be structured via `docs/context/handoff/task-request-template.md`, while delivered/formalized task history should accumulate in `docs/context/handoff/task-request-log.md`.

## Auth Session Security

- Mobile auth/session tokens must be stored only in secure platform storage:
  - Android: encrypted storage backed by Android Keystore (`EncryptedSharedPreferences`/equivalent).
  - iOS: Keychain.
- Plain `SharedPreferences`/`UserDefaults` are allowed only for one-time migration reads and must be cleared immediately after secure migration.
- Session restore flow must support access-token validation plus refresh-token fallback; refresh token must be rotated (one-time use) on every successful refresh.
- Remote datastore connections (PostgreSQL/Redis outside local host network) must use secure transport by default (`sslmode=require` for DB, `rediss://` for Redis) unless an explicit temporary insecure override is documented.
- Public web entrypoints must return baseline security headers (at reverse-proxy or app layer).
- Runtime containers must run as non-root unless a time-bounded exception is documented in governance decisions.
- All protected backend routes must pass through shared auth middleware/interceptor that validates JWT and revocation status before handler logic.

## Testing Policy

- Every delivered feature must include corresponding automated tests.
- Minimum expectation per feature:
  - unit tests for domain/business rules,
  - unit tests for `ViewModel` state transitions in `MVI`,
  - integration tests for data layer where logic is non-trivial.
- A feature is not considered complete without its tests.

## Rule of Change

- Any exception to these standards must be explicitly documented in `decisions-log.md` with rationale and expiry plan.
- Security-first rule: every backend/mobile change must include an explicit security check (threats, secret handling, auth/session impact, abuse controls) before merge.

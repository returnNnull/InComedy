# Engineering Standards

This document defines mandatory engineering rules for InComedy.

## Architecture

- Project architecture style: `Clean` (layered boundaries and dependency direction from outer layers to domain abstractions).
- Feature modules should keep clear separation of concerns (presentation/domain/data).
- Dependency injection standard: `Koin`.
- Dependencies should be wired via feature modules and resolved through shared DI entry points (no ad-hoc object factories).

## Presentation

- All `ViewModel` implementations must follow `MVI`.
- UI must be implemented separately per platform:
  - Android: Jetpack Compose
  - iOS: SwiftUI
- Shared feature modules should contain platform-agnostic logic (domain/use-cases/ViewModel/contracts), not platform UI widgets.
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
- When editing an existing file, bring touched classes and their methods/properties into compliance with this documentation rule in the same change.

## Observability

- Auth and payment-related flows must emit structured logs on every critical stage:
  - flow started,
  - external callback received,
  - verification/exchange success,
  - verification/exchange failure.
- Every backend auth request log must include request trace identifier (`X-Request-ID`/call-id).
- Logs must never include secrets or raw auth tokens.

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

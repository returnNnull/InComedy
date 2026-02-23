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
- iOS integration with shared `ViewModel` must use a bridge + Swift `ObservableObject` adapter pattern:
  - shared bridge exposes `observeState`, `observeEffect`, `dispose`
  - Swift adapter owns bridge lifecycle and publishes UI-friendly state
  - bridge implementations should reuse shared base abstractions (`BaseFeatureBridge`, `BridgeHandle`)
- Each feature should define explicit:
  - `Intent` (user/action inputs),
  - `State` (single source of truth for UI),
  - `Effect` (one-time side effects such as navigation/toast).

## Testability

- Code must be designed for high testability (dependency inversion, minimal hidden state, deterministic behavior).
- Business logic should be isolated from framework code when possible.
- Side effects (network, storage, time, random) must be abstracted behind interfaces.

## Testing Policy

- Every delivered feature must include corresponding automated tests.
- Minimum expectation per feature:
  - unit tests for domain/business rules,
  - unit tests for `ViewModel` state transitions in `MVI`,
  - integration tests for data layer where logic is non-trivial.
- A feature is not considered complete without its tests.

## Rule of Change

- Any exception to these standards must be explicitly documented in `decisions-log.md` with rationale and expiry plan.

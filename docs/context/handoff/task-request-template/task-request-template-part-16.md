# Task Request Template Part 16

## Formalized Analysis Request (Compare External Multi-Provider Auth Specification With Current InComedy Auth Architecture)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decisions-log.md`
  - `D-058`
  - `D-059`
  - `D-060`
- Current constraints:
  - The active MVP auth scope is `login + password` as the first-party standard plus `VK ID` as the supported external provider; phone OTP, Telegram, and Google are not in the active MVP auth scope.
  - The repository already uses a provider-agnostic internal identity/session foundation with `users`, `auth_identities`, credential accounts, hashed rotating refresh tokens, RBAC context, and sanitized diagnostics.
  - Android VK currently experiments with the official VK ID SDK as a transport layer, while backend-issued internal sessions remain the only application session contract.
  - Legacy Telegram and provider-enum artifacts still exist in parts of the codebase and must be treated as historical or cleanup debt unless reactivated by an explicit product decision.

## Goal

- What should be delivered:
  - analyze how closely the external auth specification matches the current project architecture and product scope
  - identify which parts can be reused directly, which parts conflict with active decisions, and which parts would require a migration rather than an additive implementation
  - produce a pragmatic implementation/migration plan toward the proposed architecture, explicitly preferring official SDKs where they are justified and compatible with repository security rules
  - highlight risks, advantages, and disadvantages of switching from the current implementation direction to the proposed one

## Scope

- In scope:
  - comparison of provider model, server contracts, data model, mobile architecture, and session model
  - assessment of Telegram, VK, and phone flows against the active product backlog and current codebase
  - migration options ranging from minimal alignment to full auth-surface pivot
  - security and operability implications of SDK-based versus browser/server-mediated provider flows
- Out of scope:
  - automatic adoption of the external specification as a new source of truth
  - implementation of phone OTP, Telegram Mini App auth, or account linking in this task
  - changing product scope or accepted ADRs without an explicit follow-up decision

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - any conflict between the external specification and current repository decisions must be called out explicitly
  - secrets must stay out of the repository and should remain server-side unless an SDK imposes a client-side compromise that is documented as an explicit risk
  - backend diagnostics and code comments must continue to follow the current repository rules

## Definition of Done

- Functional result:
  - the user receives a structured comparison between the external specification and the current project
  - the answer identifies direct matches, mismatches, and migration-only changes
  - the answer provides a phased plan for implementation or migration
  - the answer includes explicit risks plus pros/cons of moving to the proposed architecture

## Formalized Analysis Request (Evaluate Provider Token Lifecycle Capabilities Before Replacing Internal Sessions)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `D-059`
  - `D-060`
- Current constraints:
  - The user wants to understand whether the listed auth providers can independently support session lifecycle checks, specifically expiration detection, revocation handling, and token refresh.
  - The user is considering removing the current backend-issued session-token system for external providers and leaving internal token issuance only for phone-based auth.
  - The current repository already relies on internal sessions for protected backend routes, RBAC, linked identities, refresh rotation, logout, diagnostics correlation, and workspace access.

## Goal

- What should be delivered:
  - determine, for each listed provider, whether official capabilities exist for checking token validity/expiry, detecting revocation, and refreshing tokens
  - distinguish provider tokens, signed login assertions, and Mini App payloads so they are not treated as equivalent session artifacts
  - assess whether the current internal session layer can be removed for external providers without breaking product requirements or security guarantees

## Scope

- In scope:
  - Telegram login widget / Telegram Mini App / Telegram OIDC token lifecycle characteristics
  - VK token/session lifecycle characteristics via current official SDK and OAuth documentation
  - phone auth lifecycle implications as an internal first-party flow
  - consequences for the current InComedy backend/session architecture
- Out of scope:
  - implementing a new auth/session architecture in this task
  - accepting a new ADR that replaces the current internal-session model
  - broad provider analysis beyond the listed auth methods

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - use official provider documentation or official SDK documentation only
  - make clear when a capability is documented, inferred from SDK API surface, or not found in official materials

## Definition of Done

- Functional result:
  - the answer lists provider-by-provider support for expiry detection, revocation handling, and refresh
  - the answer states whether a provider-token-only model is viable for the listed provider mix
  - the answer gives a recommendation for whether InComedy should keep or remove its internal session layer

## Formalized Implementation Request (Android VK OneTap Integration)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/governance/session-log.md`
  - `D-060`
  - `D-061`
- Current constraints:
  - The active MVP auth scope still keeps `VK ID` as the only supported external provider on mobile.
  - The current Android VK SDK path already exists, but the latest live investigation showed `failed_oauth_state` before backend `/verify`.
  - The Android auth screen still renders VK as a plain button, while the user now explicitly wants the official VK OneTap experience on Android.
  - Backend-issued state, PKCE challenge, internal session issuance, and browser/public-callback fallback must remain compatible with the current repository decisions.

## Goal

- What should be delivered:
  - implement Android VK OneTap UI using the official VK SDK modules where possible
  - preserve the current backend `start/verify` contract and internal-session model
  - avoid reintroducing the known SDK state mismatch by ensuring the OneTap transport uses server-issued auth parameters safely
  - keep a bounded fallback to the existing browser launch path when OneTap is unavailable or not yet prepared

## Scope

- In scope:
  - Android auth screen integration of official VK OneTap Compose UI
  - Android-side preparation/caching of backend-issued VK launch parameters needed by OneTap
  - completion wiring from OneTap auth-code callback into the shared auth flow
  - regression tests for the new Android VK OneTap preparation/completion path
- Out of scope:
  - changing the backend session model
  - enabling Telegram, phone OTP, or other external providers
  - broader product-scope changes beyond the Android VK entry flow

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - new materially changed code must be commented in Russian
  - official VK SDK/OneTap modules should be preferred over custom lookalike UI
  - browser/public-callback VK auth must remain available as a safe fallback path

## Definition of Done

- Functional result:
  - Android auth screen exposes an official VK OneTap entry point when SDK config is available
  - OneTap uses backend-issued state/code-challenge metadata and completes through the shared VK verify path
  - the previous plain-button/browser path remains as fallback
  - relevant Android tests/build validation pass or failures are documented

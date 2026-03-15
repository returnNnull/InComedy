# Task Request Template Part 15

## Formalized Implementation Request (Stabilize Android VK Callback Completion)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/session-log.md`
  - `D-059`
- Current constraints:
  - Real-device behavior shows that the current Android VK flow still starts in the browser, even when the VK app is installed, and the user can get stuck in a browser -> VK app -> browser -> app bounce without completing authorization.
  - The current VK HTTPS callback bridge shows a manual "Open app" button on Android instead of attempting to return to the app automatically.
  - The shared auth ViewModel keeps pending external-auth state only in memory, so Android process recreation can reject a valid VK callback before backend verification runs.
  - VK callback payload parsing in the data layer must preserve the exact callback values sent by the HTTPS bridge, including URL-encoded parameters.

## Goal

- What should be delivered:
  - make Android VK callback completion return to the app automatically whenever the browser allows it
  - keep a manual open-app fallback so the user is not stranded if auto-return is blocked
  - let VK callback completion survive Android process recreation when backend-signed state is still valid
  - ensure VK callback payload parsing decodes URL-encoded values before backend verification

## Scope

- In scope:
  - VK HTTPS callback bridge behavior for Android return-to-app handoff
  - shared auth-state validation rules specific to backend-signed VK state
  - VK callback payload parsing and focused automated regression coverage
  - source-of-truth governance/task memory updates for the stabilization work
- Out of scope:
  - migrating Android VK auth to a VK SDK
  - changing the canonical public VK callback URI
  - redesigning the wider provider-agnostic auth/session architecture

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - backend-issued internal session remains the source of truth after VK verification
  - Android should still preserve a manual fallback path if browser auto-return to `incomedy://` is blocked
  - client-side VK state handling must not weaken backend verification; backend-signed `state` remains the authoritative anti-forgery check

## Definition of Done

- Functional result:
  - Android callback bridge attempts to reopen the app automatically and still renders a manual fallback button
  - VK callback completion no longer depends on in-memory-only client state surviving the browser/app round-trip
  - URL-encoded VK callback payload values are decoded correctly before `/api/v1/auth/vk/verify`
  - focused tests cover the new VK callback completion and parsing behavior

## Formalized Analysis Request (Provider Tokens Versus Internal Sessions And VK SDK Boundary)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/architecture-overview.md`
  - `D-059`
- Current constraints:
  - The repository currently treats backend-issued internal sessions as the source of truth for both first-party credentials and external providers.
  - The user observed that external providers still issue their own tokens anyway and asked whether the system should switch to provider tokens as the primary session model, while keeping internal token generation only for login/password.
  - The user also asked whether VK on Android should move to SDK-based authorization if that improves the mobile flow.

## Goal

- What should be delivered:
  - evaluate whether provider-issued tokens should replace internal sessions for the active auth architecture
  - define the recommended boundary between provider-native auth artifacts and backend-owned session tokens
  - assess whether VK SDK adoption makes sense specifically as an Android transport/integration layer without collapsing the shared session model

## Scope

- In scope:
  - architecture trade-off analysis for external-provider tokens vs backend-issued internal sessions
  - comparison of full external-token model vs hybrid exchange model
  - recommendation for VK SDK adoption boundary on Android
- Out of scope:
  - implementing the architecture pivot in code
  - changing active decisions/docs unless a new decision is explicitly accepted
  - committing to a VK SDK migration without a separate implementation task

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - protected backend routes currently rely on shared JWT + revocation middleware
  - role context, workspace membership, linked identities, and refresh-token rotation already depend on an internal user/session model

## Definition of Done

- Functional result:
  - the recommendation clearly states whether provider tokens should or should not become the primary auth session model
  - the answer identifies the least disruptive path if VK SDK adoption is still desired
  - the trade-off analysis stays consistent with the current source-of-truth architecture unless the user explicitly requests a pivot

## Formalized Implementation Request (Adopt Official VK ID Android SDK Without Replacing Internal Sessions)

## Context

- Related docs/decisions:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/governance/session-log.md`
  - `D-059`
- Current constraints:
  - The current Android VK flow still depends on a browser/public-callback handoff and has shown real-device UX instability.
  - The user explicitly chose the bounded path `VK SDK on Android + backend exchange -> internal session`, not a global provider-token pivot.
  - New or substantially changed code must be commented at class/object/interface, method/function, and meaningful field/property level.
  - Backend production-significant diagnostics must flow through the sanitized diagnostics system with low-cardinality metadata rather than ad-hoc raw logging.

## Goal

- What should be delivered:
  - integrate the official VK ID Android SDK as the preferred Android VK auth transport
  - keep backend-issued internal sessions as the only application session model after VK verification
  - preserve the public HTTPS callback/browser flow as the fallback path for non-SDK Android setups and other platforms
  - document the required VK cabinet setup and runtime/build parameters needed to enable the SDK path

## Scope

- In scope:
  - Android app integration of the official VK ID SDK in auth-code mode
  - backend VK start/verify contract changes needed to support Android SDK code exchange while preserving current browser flow
  - client logging around Android VK SDK start/success/failure/fallback
  - sanitized backend diagnostics that distinguish Android SDK verification from browser callback verification
  - source-of-truth docs, env examples, and setup instructions for the new Android VK SDK path
- Out of scope:
  - replacing backend-issued internal sessions with provider tokens
  - removing the existing public HTTPS callback bridge for iOS/browser fallback
  - expanding VK auth into unrelated One Tap / widget UI work

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - Android VK SDK should be an acquisition/transport detail only; the backend remains the verifier and session issuer
  - browser/public-callback fallback must remain available if Android SDK config is absent or intentionally disabled
  - any new backend telemetry must use sanitized diagnostics with bounded metadata such as `requestId`, `stage`, and safe source markers

## Definition of Done

- Functional result:
  - Android VK sign-in prefers the official VK ID SDK when both client and server SDK config are present
  - backend `/api/v1/auth/vk/start` returns enough metadata for Android SDK auth-code launch without breaking browser fallback
  - backend `/api/v1/auth/vk/verify` can distinguish Android SDK completions from browser callback completions and issue the same internal session shape
  - Android/client and backend/server logging are present for the new SDK path without leaking secrets or raw provider tokens
  - docs explain VK cabinet setup plus the required server env and Android build/env parameters

## Formalized Implementation Request (Prefer Ignored local.properties For Android VK SDK Build Secrets)

## Context

- Related docs/decisions:
  - `README.md`
  - `docs/context/governance/session-log.md`
  - `D-060`
- Current constraints:
  - The repository is public, so Android VK SDK credentials must not be committed anywhere under version control.
  - The existing setup supports `~/.gradle/gradle.properties` and environment variables, but the user wants a repository-local path that stays untracked and easier to discover per project.
  - The standard Android root `local.properties` file is already ignored by git in this repository and is present on local developer machines.

## Goal

- What should be delivered:
  - make the Android build read VK SDK properties from ignored root `local.properties`
  - provide placeholder keys in that file without accidentally enabling the SDK on empty values
  - document `local.properties` as the preferred local setup for this public repository

## Scope

- In scope:
  - Gradle property resolution precedence for Android VK SDK build configuration
  - ignored root `local.properties` placeholder entries
  - concise developer-facing setup docs for the new project-local secret path
- Out of scope:
  - changing server-side env handling
  - committing real credentials
  - replacing environment-variable or `~/.gradle/gradle.properties` support

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - the repository is public, so real Android VK SDK credentials must stay outside tracked files
  - empty placeholders in `local.properties` must keep Android on browser/public-callback fallback rather than partially enabling the SDK

## Definition of Done

- Functional result:
  - Android build configuration resolves VK SDK properties from ignored root `local.properties`
  - `local.properties` contains safe placeholders that do not enable the SDK until real values are added
  - repository documentation points contributors to the project-local ignored setup

## Formalized Documentation Request (Require Russian-Language Code Comments In Rules And New-Chat Handoff)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/handoff/chat-handoff-template.md`
  - `docs/context/governance/decisions-log.md`
- Current constraints:
  - The repository already requires code comments for new and materially changed code, but the comment language itself is not yet fixed as a standing rule.
  - The project conversation and handoff protocol are Russian-first, so future chats should inherit the same language requirement before implementation starts.

## Goal

- What should be delivered:
  - make Russian the explicit default language for mandatory repository code comments
  - surface that requirement in the new-chat handoff template so the next chat sees it immediately
  - synchronize governance records so the rule change is traceable

## Scope

- In scope:
  - engineering and quality rule updates for comment language
  - new-chat handoff template update
  - decision log, traceability, and session-log synchronization for the new standing rule
- Out of scope:
  - repository-wide backfill of all historical comments in untouched code
  - translation of third-party documentation or external specification quotes

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - the rule applies to mandatory repository code comments for new or materially changed code
  - exact technical terms may remain in English inside otherwise Russian explanations when translation would reduce precision

## Definition of Done

- Functional result:
  - engineering rules explicitly require Russian-language code comments
  - the new-chat handoff template passes that rule into future chats
  - governance records capture the accepted rule change and its traceability

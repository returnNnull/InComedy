# Task Request Template Part 11

## Formalized Implementation Request (Google Auth Via Native Mobile SDKs And Backend ID Token Verification)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `D-045`, `D-052`, `D-053`
- Current constraints:
  - Telegram remains operationally incomplete and is explicitly deferred as an unfinished task for now.
  - Google auth is currently only a client-side stub in `data/auth`, while the authoritative context requires provider-agnostic internal identities and real backend session issuance before launch.
  - The implementation path must follow current official Google mobile guidance rather than extending the existing generic browser/custom-scheme stub.

## Goal

- What should be delivered:
  - Implement real Google sign-in that issues the existing internal backend session and refresh token pair.
  - Keep Google plugged into the same internal `User + AuthIdentity` model, role context, workspace context, and secure mobile session restore flow already used by Telegram.

## Scope

- In scope:
  - add backend Google auth start/verify contract and ID token verification against Google public keys
  - persist/update Google auth identities in the provider-agnostic user model
  - expose shared/data-layer Google provider integration against the real backend contract
  - trigger Google auth natively on mobile instead of the current browser stub
  - update Android auth flow to use official Google mobile sign-in with backend server client id and nonce/state
  - update iOS auth flow/config plumbing for native Google sign-in and backend ID token handoff
  - update automated tests, OpenAPI, runtime examples, and context docs
- Out of scope:
  - VK implementation
  - Sign in with Apple
  - Telegram remediation
  - production credential provisioning in Google Cloud Console

## Constraints

- Tech/business constraints:
  - Do not keep the current custom-scheme browser OAuth stub as the production Google path if it conflicts with current official mobile guidance.
  - Backend verification must validate Google token signature/issuer/audience/expiry and nonce/state before internal session issuance.
  - Logs/diagnostics must remain token-safe and include request correlation ids on failure paths.
  - Runtime secrets and client ids must not be committed to the repository; only examples/placeholders are allowed.

## Definition of Done

- Functional result:
  - Google button starts a native mobile sign-in flow instead of the stubbed browser URL flow
  - successful Google sign-in returns an internal backend session with refresh token rotation compatible with existing session restore
  - Google identities appear as linked providers in the current session/role context
- Required tests:
  - backend unit/route tests for Google start and verify
  - shared/data auth tests for Google provider contract
  - Android unit/UI coverage for Google auth effect handling where current repo infrastructure supports it
  - server test suite for the changed auth/session scope
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

## Formalized Implementation Request (Auth Strategy Pivot To Phone Number And Code)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `D-045`, `D-057`
- Current constraints:
  - The product decision on Friday, March 14, 2026 is to stop treating Telegram and Google as active login methods for InComedy.
  - The shared/mobile/server auth foundation must remain provider-agnostic because the next planned implementations are phone number + code and VK.
  - Removing legacy auth methods must happen without regressing session foundation, role context, workspace context, or the ability to add new providers cleanly.

## Goal

- What should be delivered:
  - Remove Telegram and Google from the active app/runtime auth surface.
  - Reframe the auth entry experience around the new standard: phone number + code first, VK next.
  - Preserve the current extensibility seams (`AuthProviderType`, provider interfaces/services, internal `User + AuthIdentity + session` model) so the next auth slice does not need a structural redesign.

## Scope

- In scope:
  - update context docs and ADRs to reflect the auth strategy pivot
  - remove Telegram/Google from visible mobile auth entry points
  - remove active Telegram/Google runtime requirements and supported API/docs surface where those providers are no longer part of the supported product contract
  - keep provider abstractions and prepare the codebase for `PHONE` and `VK`
- Out of scope:
  - implementing the actual phone OTP flow
  - implementing real VK backend auth
  - deleting every historical Telegram/Google source file if it is not required to complete the active-surface pivot safely

## Constraints

- Tech/business constraints:
  - Do not collapse the auth architecture back to a single hardcoded flow; provider extensibility remains mandatory.
  - The new docs must clearly state that the next implementation step is `phone number + code`, then `VK`.
  - Avoid leaving docs/runtime/UI contradictions where Telegram/Google look active in one place and removed in another.

## Definition of Done

- Functional result:
  - Telegram and Google are no longer presented as supported login methods in the app
  - the app and runtime docs no longer require Telegram/Google as the active auth baseline
  - the provider-agnostic auth foundation remains intact and ready for `PHONE` and `VK`
- Required docs updates:
  - `docs/context/product/product-brief.md`
  - `docs/context/product/backlog.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`

## Formalized Implementation Request (Conversation Trace Standard In Context Docs)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/handoff/context-protocol.md`
- Current constraints:
  - The repository already stores implementation history in governance docs, but the requirement to preserve a brief analyzable trace of user-assistant conversation flow is not explicit enough yet.
  - Future chats should inherit this rule from the bootstrap guidance instead of relying on ad-hoc reminders.

## Goal

- What should be delivered:
  - Make concise conversation/work trace logging mandatory in context governance docs.
  - Ensure new chats are explicitly instructed to keep that trace updated.

## Scope

- In scope:
  - add the rule to engineering standards
  - add the rule to quality/DoD expectations
  - update the bootstrap guidance with explicit session-log instructions
  - record the governance decision and current session outcome in governance docs
- Out of scope:
  - storing raw full chat transcripts
  - introducing a separate transcript storage system outside current context docs

## Constraints

- Tech/business constraints:
  - The stored trace must be short and analyzable, not verbose transcript dumping.
  - Session-log summaries must remain sanitized and must not include secrets, tokens, or raw sensitive data.
  - The standard should reinforce use of the existing `Context / Changes / Decisions / Next` structure instead of inventing a new logging format.

## Definition of Done

- Functional result:
  - repository standards explicitly require concise conversation-trace logging in governance docs
  - the bootstrap guidance explicitly instructs the next assistant to maintain that trace
  - governance docs record the decision so the rule is durable across chats
- Required docs updates:
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/handoff/context-protocol.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`

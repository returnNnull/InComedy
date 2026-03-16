# Task Request Template Part 18

## Formalized Context Sync Request (New Chat Handoff Resynchronization)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/engineering/engineering-standards.md`
  - `docs/context/engineering/quality-rules.md`
  - `docs/context/product/non-functional-requirements.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/test-strategy.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/product/backlog.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `D-059`
  - `D-060`
  - `D-061`
- Current constraints:
  - `docs/context/*` remains the primary source of truth for the repository.
  - A new chat must not proceed to implementation until the ordered context sync is completed.
  - Split governance documents must be followed through their latest indexed parts to confirm the current active state.

## Goal

- What should be delivered:
  - ordered context sync across the required product, engineering, and governance documents
  - confirmation of the latest accepted decision id
  - confirmation of the current `P0` priority baseline from the backlog
  - confirmation of the latest `Next` item from session governance memory
  - confirmation of the current execution status of the active key decisions from decision traceability

## Scope

- In scope:
  - reading the listed context files in the requested order
  - reading the latest split parts for `decisions-log`, `session-log`, and `decision-traceability`
  - summarizing the current baseline before any new implementation work
- Out of scope:
  - product or engineering changes without a follow-up task
  - changing accepted decisions unless a real conflict is discovered
  - implementation work before the sync is explicitly completed

## Constraints

- Tech/business constraints:
  - if new information conflicts with existing docs, the docs must be updated before code
  - no secrets or tokens may be copied into governance artifacts
  - summaries must stay concise and analytical rather than transcript-like

## Definition of Done

- Functional result:
  - the ordered context sync is completed
  - the response confirms the current `D-*` head, `P0` baseline, latest `Next`, and key decision execution status
  - the repository is ready for the next implementation instruction without re-reading the same baseline

## Formalized Implementation Request (Organizer Workspace Invitations And Permission Roles)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md`
  - `docs/context/product/product-brief.md`
  - `docs/context/product/non-functional-requirements.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/test-strategy.md`
  - `D-045`
  - `D-059`
  - `D-061`
- Current constraints:
  - The repository already has organizer workspace creation/list plus workspace-scoped permission role enums in storage.
  - Current mobile shells surface only workspace summary data; invitations, member management, and permission editing are still missing.
  - The active MVP auth baseline is `login + password` first with `VK ID` as the supported external provider, so invitation delivery cannot rely on phone OTP, Telegram, Google, or store-console capabilities.

## Goal

- What should be delivered:
  - organizer workspace member invitation flow for already registered users
  - pending invitation inbox for the invited user with accept/decline actions
  - workspace member roster with permission roles and controlled role editing
  - backend, shared, Android, and iOS support for the slice
  - automated coverage and synchronized docs/context updates

## Scope

- In scope:
  - invite existing users by exact login/username lookup already known to the organizer
  - use `workspace_members.joined_at IS NULL` as the pending-invitation state instead of introducing a parallel invitation entity
  - expose pending invitations as a dedicated session-context list for the current user
  - show active members and pending invites inside workspace data returned to the current member
  - allow permission-role assignment and editing with a bounded matrix:
    - `owner` can invite and retarget `manager`, `checker`, `host`
    - `manager` can invite and retarget only `checker`, `host`
    - `checker` and `host` have read-only workspace team visibility in this slice
  - keep owner transfer, member removal, and external invitation delivery out of this slice
- Out of scope:
  - ownership transfer
  - arbitrary staff removal/cancel flows beyond invitee decline
  - external delivery channels for invitations
  - organizer operational domains beyond workspace team management

## Constraints

- Tech/business constraints:
  - new and materially changed code must keep Russian comments at class/method/property level
  - backend changes must emit sanitized structured diagnostics with `requestId`, `stage`, and bounded metadata
  - API changes must be reflected in `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - if the target user lookup fails, the server must respond explicitly without leaking secrets or raw provider data
  - active role switching remains a separate global session concern; accepting an invitation should add organizer capability without forcing an active-role switch

## Definition of Done

- Functional result:
  - an organizer can invite an already registered user into a workspace with an allowed permission role
  - the invited user can see pending workspace invitations and accept or decline them
  - accepted invitations appear as active workspace memberships and grant organizer workspace access
  - owner/manager role updates follow the bounded permission matrix above
  - Android and iOS main shells can view invitations and manage the workspace team within the allowed scope
- Required tests:
  - `:server:test` for organizer workspace invitation, acceptance/decline, and role-update routes
  - `:shared:allTests`
  - `:composeApp:testDebugUnitTest`
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16e,OS=26.2' -parallel-testing-enabled NO -maximum-parallel-testing-workers 1 test`
- Required docs updates:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

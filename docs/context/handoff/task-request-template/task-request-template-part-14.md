# Task Request Template Part 14

## Formalized Documentation Request (Persist Platform Developer Account Constraint)

## Context

- Related docs/decisions:
  - `docs/context/product/product-brief.md`
  - `docs/context/product/risk-log.md`
  - `docs/context/governance/session-log.md`
  - `D-059`
- Current constraints:
  - The user explicitly confirmed that the project currently has neither an Apple Developer Program account nor a Google Play developer account.
  - This constraint affects planning for mobile auth, store rollout, universal-link/device validation, and any integration that depends on platform developer consoles or paid Apple capabilities.
  - The limitation must survive chat handoffs and therefore cannot remain only in transient conversation.

## Goal

- What should be delivered:
  - record the absence of platform developer accounts as persistent repository context
  - add an explicit active risk so platform-console assumptions are surfaced during planning
  - ensure new chats inherit this limitation automatically from the normal context read order

## Scope

- In scope:
  - update source-of-truth context docs with the Apple/Google developer-account constraint
  - add or update risk tracking for platform-account-dependent work
  - record the clarification in governance session memory
- Out of scope:
  - provisioning Apple or Google developer accounts
  - changing the current auth/release architecture in the same task
  - promising rollout dates that still depend on missing platform accounts

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - future implementation planning must not assume App Store Connect or Play Console access by default
  - the constraint applies until the user explicitly confirms that the relevant accounts now exist

## Definition of Done

- Functional result:
  - `product-brief.md` states the standing platform-account limitation in the main context read order
  - `risk-log.md` contains an active risk describing the delivery impact of missing Apple/Google developer accounts
  - `session-log.md` and the latest session-log part preserve the clarification for future chats

## Formalized Implementation Request (Android VK App Preferred Launch)

## Context

- Related docs/decisions:
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/governance/session-log.md`
  - `D-059`
- Current constraints:
  - Android VK auth already works through an external URL launch plus callback handling, but the current app simply opens the VK auth URL as a generic external browser intent.
  - The user requires Android auth UX to prefer the installed VK application when available and only fall back to the browser if VK is not installed.
  - The project still has no Google Play developer account, so the solution should remain testable through direct device builds without assuming Play Console access.

## Goal

- What should be delivered:
  - make Android VK auth launch prefer the VK app if it is installed
  - preserve browser fallback behavior when VK is unavailable or cannot handle the auth URL
  - record the Android launch-policy change in source-of-truth docs and session memory

## Scope

- In scope:
  - Android-only launch intent selection for external VK auth
  - focused automated tests for the launch selection behavior
  - source-of-truth doc and governance memory updates
- Out of scope:
  - migrating to VK SDK
  - changing the backend VK callback contract
  - iOS-specific launch-policy changes

## Constraints

- Tech/business constraints:
  - `docs/context/*` remains the primary source of truth
  - keep the shared backend-issued VK auth URL contract intact
  - preserve a browser fallback so Android auth still works when the VK app is absent

## Definition of Done

- Functional result:
  - Android attempts to start VK auth via the installed VK app first
  - Android falls back to browser launch when the VK app is unavailable or the app launch fails
  - unit tests cover the launch-selection policy
  - architecture/session/task docs describe the new Android auth behavior

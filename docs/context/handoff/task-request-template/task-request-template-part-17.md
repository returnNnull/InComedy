# Task Request Template Part 17

## Formalized Investigation Request (Android VK OneTap Live Reproduction With Device Logs And Server Diagnostics)

## Context

- Related docs/decisions:
  - `docs/context/governance/session-log.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/tooling-stack.md`
  - `D-060`
  - `D-061`
- Current constraints:
  - The Android app now exposes official VK OneTap on top of the existing backend `start/verify` contract.
  - Earlier live checks already showed `failed_oauth_state` on Android before backend `/verify`.
  - The user wants another live reproduction using the connected physical Android device plus sanitized server diagnostics from production.
  - The investigation must first confirm that the needed VK auth API routes are actually recorded by the diagnostics system before drawing conclusions from missing events.

## Goal

- What should be delivered:
  - capture a fresh Android reproduction from the connected device with logcat evidence
  - confirm diagnostics coverage for the relevant VK auth API surface
  - fetch the matching sanitized server diagnostics window from production
  - identify whether the failure happens before backend `/verify`, inside callback bridge handling, or during backend verification/exchange

## Scope

- In scope:
  - `AUTH_FLOW` and relevant Android activity/VK SDK logcat capture during one reproduction
  - diagnostics retrieval for `/api/v1/auth/vk/*` and `/auth/vk/callback*`
  - root-cause analysis for the reproduced failure
- Out of scope:
  - shipping a remediation code change in the same task unless a follow-up explicitly requests it
  - raw host/container log access unless sanitized diagnostics are insufficient

## Constraints

- Tech/business constraints:
  - prefer sanitized diagnostics over raw container logs
  - never print the diagnostics token from `deploy/server/.env`
  - keep any new context updates concise and analytical

## Definition of Done

- Functional result:
  - the reproduced Android flow is correlated with production diagnostics
  - the answer states which VK auth stage failed and whether server `/verify` was reached
  - the answer provides the most probable root cause and bounded next remediation direction

## Formalized Implementation Request (Android VK OneTap Rework To Match Official Documentation)

## Context

- Related docs/decisions:
  - `docs/context/governance/session-log.md`
  - `docs/context/engineering/architecture-overview.md`
  - `docs/context/engineering/tooling-stack.md`
  - `D-060`
  - `D-061`
- Current constraints:
  - The previous Android VK OneTap attempt prewarmed backend-issued `state` and PKCE metadata.
  - Live reproduction on `2026-03-15` confirmed `failed_oauth_state` inside the VK Android SDK before backend `/api/v1/auth/vk/verify`.
  - Official VK Android auth-flow documentation states that for SDK/backend code exchange the frontend must generate `state`, `codeVerifier`, and `codeChallenge`, and send them to the backend only at code-exchange time.
  - Browser/public-callback VK flow remains required as fallback and for non-Android surfaces.

## Goal

- What should be delivered:
  - Android VK OneTap implementation that follows the documented VK SDK lifecycle
  - backend verify contract that accepts Android SDK completion data (`code`, `deviceId`, `state`, `codeVerifier`) without relying on backend-issued Android PKCE
  - preserved browser/public-callback VK flow for fallback
  - updated docs and focused regression tests

## Scope

- In scope:
  - Android Compose VK entry UI and OneTap wiring
  - shared auth contract changes needed for the Android VK callback payload
  - backend VK start/verify service and route adjustments
  - docs/context updates for the new documented flow
- Out of scope:
  - changing the internal InComedy access/refresh session model
  - adding new auth providers or account-linking flows
  - changing iOS/browser VK auth beyond keeping compatibility with the fallback contract

## Constraints

- Tech/business constraints:
  - Android SDK/OneTap path must not depend on backend-issued `state` or PKCE metadata
  - browser/public-callback flow must remain operational
  - new and substantially changed code must be documented in Russian comments
  - diagnostics must stay sanitized and low-cardinality

## Definition of Done

- Functional result:
  - Android OneTap uses locally generated `state + codeVerifier + codeChallenge`
  - backend `/api/v1/auth/vk/verify` can complete Android SDK code exchange using client-supplied PKCE/state
  - browser/public-callback VK flow still starts and verifies successfully with server-issued signed state
  - unit tests cover the new Android verify payload and the backend client-source split

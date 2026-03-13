# Task Request Template Part 09

## Latest Formalized Request (Push Restored Telegram OIDC Flow And Validate Live Deployment)

## Context

- Related docs/decisions:
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `server/README.md`
  - `D-053`, `D-054`
- Current constraints:
  - The repository-local Telegram auth path was just restored to the official OIDC authorization-code flow with HTTPS callback bridge.
  - The user requested execution, not another local-only preparation step: push to GitHub, wait for CI/CD, verify that the live server comes back, then validate the Telegram button from the app on a device.
  - Live validation must use the real `main` branch deployment outcome and real device/emulator behavior, not only local tests.

## Goal

- What should be delivered:
  - Push the restored Telegram OIDC changes, wait for the resulting workflows, verify whether the deployed server is healthy, and then check whether Telegram login works from the app by actually pressing the button on device.

## Scope

- In scope:
  - commit and push the current Telegram OIDC restoration work to `origin/main`
  - wait for the GitHub Actions workflows triggered by that push
  - verify live server health and Telegram auth start endpoint availability after deploy
  - reinstall the current Android build if needed and reproduce the Telegram button flow on emulator/device
  - capture the resulting client/server symptoms in source-of-truth docs
- Out of scope:
  - redesigning the Telegram auth flow again in this step
  - changing iOS behavior
  - speculative fixes without first validating the real deployment result

## Constraints

- Tech/business constraints:
  - Sensitive Telegram auth values, runtime secrets, and raw callback payloads must not be exposed in logs or docs.
  - Validation must clearly distinguish between CI success, CD success/failure, live server health, and mobile UX behavior.
  - If deployment fails, the failure and its user-visible impact must be documented before any follow-up implementation.

## Definition of Done

- Functional result:
  - the restored Telegram OIDC work is pushed to `main`
  - the resulting CI/CD status is recorded
  - the live server health state is verified after the deploy attempt
  - the Telegram button behavior is reproduced on device/emulator and correlated with the live backend state
- Required tests:
  - GitHub Actions results for the pushed `main` commit
  - live `GET /health` and Telegram auth-start smoke check
  - Android emulator/device button smoke test
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/governance/session-log.md`

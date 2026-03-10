# Task Request Template Part 02

## Latest Formalized Request (Server Security Audit)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md` (security-first rule, auth observability, protected-route middleware)
  - `docs/context/engineering/quality-rules.md` (mandatory security review, no silent failures, auth logging)
  - `docs/context/product/non-functional-requirements.md` (security/privacy, reliability, operability)
  - `D-032`, `D-039`, `D-040`, `D-041`, `D-042`, `D-043`, `D-044`
- Current constraints:
  - Audit scope is the in-repo `server` folder first.
  - Findings must be documented with severity, evidence, and remediation guidance.
  - Any discovered vulnerabilities must be added to `docs/context/product/risk-log.md` in the same change.

## Goal

- What should be delivered:
  - Perform a security audit of `server/*`.
  - Produce a written report covering verified controls, findings, and test/coverage gaps.

## Scope

- In scope:
  - `server/src/main/**/*`
  - `server/src/test/**/*`
  - `server/Dockerfile`
  - `server/build.gradle.kts`
  - `server/.env.example`
  - `server/README.md`
  - Security-oriented verification via `./gradlew :server:test :server:installDist`
- Out of scope:
  - Runtime production infrastructure outside `server/*` except where server code explicitly depends on proxy/runtime behavior.
  - Fixing findings in code during the audit block unless separately requested.

## Constraints

- Tech/business constraints:
  - Keep `docs/context/*` as source of truth.
  - Report must distinguish implemented controls from inferred risk areas.
  - Review should prioritize exploitable security issues over style or general cleanup.
- Deadlines or milestones:
  - Complete in the current session with a shareable report artifact.

## Definition of Done

- Functional result:
  - A markdown security audit report exists in project docs and summarizes the server security posture.
- Required tests:
  - `./gradlew :server:test :server:installDist`
- Required docs updates:
  - `docs/context/handoff/task-request-template.md`
  - `docs/context/product/risk-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/server-security-audit-2026-03-06.md`

---

## Latest Formalized Request (Server Security Remediation)

## Context

- Related docs/decisions:
  - `docs/context/governance/server-security-audit-2026-03-06.md`
  - `docs/context/product/risk-log.md` (`V-002`, `V-003`, `V-004`)
  - `D-040`, `D-041`, `D-044`
- Current constraints:
  - Fix the audited security findings in the `server` module without introducing contract drift.
  - Keep new security behavior covered by automated tests.

## Goal

- What should be delivered:
  - Remediate the discovered server security issues and verify them with tests.

## Scope

- In scope:
  - Telegram auth replay protection
  - Auth rate-limit identity hardening
  - Public auth request body-size limits
  - `X-Request-ID` validation hardening
  - Required route, storage, contract, and test updates inside server/docs scope
- Out of scope:
  - Production deployment itself
  - Non-server feature work

## Constraints

- Tech/business constraints:
  - Preserve current public endpoint paths.
  - Keep auth error responses sanitized.
  - Update context docs in the same change.
- Deadlines or milestones:
  - Complete in the current session after the audit.

## Definition of Done

- Functional result:
  - Audited security findings are remediated in code and reflected in docs/contracts.
- Required tests:
  - `./gradlew :server:test :server:installDist`
- Required docs updates:
  - `docs/context/engineering/api-contracts/v1/openapi.yaml`
  - `docs/context/governance/server-security-audit-2026-03-06.md`
  - `docs/context/product/risk-log.md`
  - `docs/context/governance/session-log.md`
  - `docs/context/governance/decision-traceability.md`

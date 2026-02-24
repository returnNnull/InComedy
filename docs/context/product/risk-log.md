# Risk Log

Use this file to track active product/technical risks, including current security vulnerabilities.

## Template

- ID:
- Date:
- Risk:
- Impact:
- Probability:
- Mitigation:
- Owner:
- Status:

## Security Vulnerability Template

- ID:
- Date discovered:
- Vulnerability:
- Affected components:
- Severity:
- Exploitability:
- Current exposure:
- Immediate containment:
- Remediation plan:
- Target fix date:
- Owner:
- Status:

---

## R-001

- Date: 2026-02-23
- Risk: Payment provider integration delays MVP ticketing and donations.
- Impact: High
- Probability: Medium
- Mitigation: Select provider early, integrate sandbox flow first, keep fallback manual refund process.
- Owner: TBD
- Status: open

## R-002

- Date: 2026-02-23
- Risk: Chat moderation gaps create abuse/spam risk at launch.
- Impact: High
- Probability: Medium
- Mitigation: Add baseline moderation tools (report, mute, ban) before public rollout.
- Owner: TBD
- Status: open

## V-001

- Date discovered: 2026-02-24
- Vulnerability: Mobile auth/session token persisted in plain local storage (`SharedPreferences`/`UserDefaults`).
- Affected components: `composeApp` auth wrapper, `iosApp` auth wrapper.
- Severity: High
- Exploitability: Medium
- Current exposure: Mitigated in code by migration to secure storage; requires rollout on client updates.
- Immediate containment: Do not log tokens; force secure storage for new writes.
- Remediation plan: Complete release rollout with secure storage migration and verify migration telemetry on Android/iOS.
- Target fix date: 2026-02-26
- Owner: Engineering
- Status: in-progress

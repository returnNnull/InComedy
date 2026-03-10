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
- Risk: Event feed or future user-generated communication features may create abuse/spam risk without baseline moderation controls.
- Impact: High
- Probability: Medium
- Mitigation: Keep broad public chat out of MVP, add baseline moderation/reporting controls for announcements and future community features before rollout.
- Owner: TBD
- Status: open

## R-003

- Date: 2026-03-06
- Risk: iOS release may be blocked if third-party login is shipped without Sign in with Apple or if donation flow conflicts with App Review expectations.
- Impact: High
- Probability: High
- Mitigation: Include Sign in with Apple in MVP scope, review donation UX against current App Store guidelines early, and keep iOS donation flow compatible with pass-through/web-checkout fallback.
- Owner: Product + Engineering
- Status: open

## R-004

- Date: 2026-03-06
- Risk: Seat oversell or inconsistent inventory state during concurrent checkout damages trust and creates refund/support cost.
- Impact: High
- Probability: Medium
- Mitigation: Model sellable inventory explicitly, use row-level locking/transactional holds, enforce idempotent order/payment handling, and add concurrency integration tests.
- Owner: Engineering
- Status: open

## R-005

- Date: 2026-03-06
- Risk: Donation payout model may fail legal/compliance review for comedians without verified payout identity.
- Impact: High
- Probability: High
- Mitigation: Require payout profile verification before enabling donations, keep manual settlement fallback, and lock donation launch behind approved legal/financial scheme.
- Owner: Product + Finance
- Status: open

## R-006

- Date: 2026-03-06
- Risk: Hall builder scope grows too large and delays MVP if treated like a generic CAD editor.
- Impact: High
- Probability: Medium
- Mitigation: Keep hall builder v1 limited to 2D templates, rows/seats/tables/zones/stage/blocking only, and defer advanced freeform editing.
- Owner: Product + Engineering
- Status: open

## R-007

- Date: 2026-03-06
- Risk: Over-scoped MVP (public chat, complex payouts, advanced recommendations) delays delivery of the operational core that creates value.
- Impact: High
- Probability: High
- Mitigation: Prioritize organizer operations, ticketing, lineup, check-in, and live stage status; defer broad social/community scope to P1/P2.
- Owner: Product
- Status: open

## R-008

- Date: 2026-03-10
- Risk: Live stage-status updates may behave unreliably under poor venue connectivity, damaging organizer and comedian trust during active shows.
- Impact: High
- Probability: Medium
- Mitigation: Design live state with WebSocket plus push/polling fallback, keep state transitions idempotent, and test degraded-network behavior before rollout.
- Owner: Engineering
- Status: open

## R-009

- Date: 2026-03-10
- Risk: Incorrect role or workspace permission modeling may expose organizer financial data or allow unauthorized operational actions.
- Impact: Critical
- Probability: Medium
- Mitigation: Implement explicit permission matrix, shared authorization checks, audit logging, and permission-focused automated tests before organizer/ticketing rollout.
- Owner: Engineering
- Status: open

## R-010

- Date: 2026-03-10
- Risk: Refund and cancellation flows may create heavy support load if policies and operator tooling are not defined before ticketing launch.
- Impact: High
- Probability: Medium
- Mitigation: Lock refund/cancel policy before ticketing implementation, keep manual recovery tooling available, and track post-payment recovery incidents explicitly.
- Owner: Product + Engineering
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

## V-002

- Date discovered: 2026-03-06
- Vulnerability: Telegram auth payloads can be replayed for up to the accepted auth-age window because backend verification has no one-time nonce/state binding or replay cache.
- Affected components: `server/auth/telegram/TelegramAuthVerifier`, `server/auth/telegram/TelegramAuthRoutes`, Telegram mobile callback completion flow.
- Severity: High
- Exploitability: Medium
- Current exposure: Mitigated in code via single-use assertion registration and reduced default auth-age; pending deployment of updated server build.
- Immediate containment: Deploy updated server build and continue treating Telegram callback payloads as sensitive credentials in all logs and client handoff flows.
- Remediation plan: Keep current single-use assertion protection, add database migration/deploy rollout, and consider restoring explicit challenge/state binding later for defense in depth.
- Target fix date: 2026-03-10
- Owner: Engineering
- Status: in-progress

## V-003

- Date discovered: 2026-03-06
- Vulnerability: Auth rate limiting trusts caller-controlled `X-Forwarded-For`, allowing client fingerprint spoofing and limiter bypass.
- Affected components: `server/security/AuthRateLimiter`, `server/auth/telegram/TelegramAuthRoutes`, `server/auth/session/SessionRoutes`.
- Severity: High
- Exploitability: High
- Current exposure: Mitigated in code by removing raw `X-Forwarded-For` trust from rate limiting; pending deployment of updated server build.
- Immediate containment: Deploy updated server build and keep direct access to the app container blocked.
- Remediation plan: Keep direct-peer/auth-identity based limiting as default and introduce trusted-proxy-aware client IP resolution only if explicitly needed later.
- Target fix date: 2026-03-10
- Owner: Engineering
- Status: in-progress

## V-004

- Date discovered: 2026-03-06
- Vulnerability: Public auth endpoints do not enforce request body size limits, leaving Telegram verify and refresh endpoints exposed to oversized-request DoS attempts.
- Affected components: `server/Application`, `server/auth/telegram/TelegramAuthRoutes`, `server/auth/session/SessionRoutes`.
- Severity: Medium
- Exploitability: Medium
- Current exposure: Mitigated in code with route-level body caps; pending deployment of updated server build. Proxy-side caps are still recommended as defense in depth.
- Immediate containment: Deploy updated server build and keep conservative reverse-proxy body-size limits in front of the server.
- Remediation plan: Keep application-level caps, add proxy-level caps in deploy infrastructure, and preserve oversized-payload regression tests.
- Target fix date: 2026-03-12
- Owner: Engineering
- Status: in-progress

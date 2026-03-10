# Server Security Audit

- Date: 2026-03-06
- Scope: `server/*` only
- Verification: manual code review + `./gradlew :server:test :server:installDist`
- Out of scope: edge/runtime files outside `server/*` except where server code assumes proxy behavior
- Remediation status: fixes for all listed findings were implemented in code on 2026-03-06 and verified by `./gradlew :server:test :server:installDist`; deployment is still required.

## Executive Summary

- The server already has a solid baseline: prepared SQL statements, hashed rotating refresh tokens, centralized JWT + revocation checks, default-secure DB/Redis transport policy, non-root container runtime, Telegram payload shape validation, and sanitized API error bodies.
- I found 4 security issues: 3 of them are now remediated in code, and 1 low-severity observability issue is also remediated in code.
- Test coverage was expanded during remediation, but the server security surface still deserves broader integration coverage over time.

## Findings

### SRV-SEC-001 High: Telegram auth assertions are replayable within the accepted auth window

- Evidence:
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/telegram/TelegramAuthVerifier.kt:13-35`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/telegram/TelegramAuthVerifier.kt:73-84`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/telegram/TelegramAuthRoutes.kt:21-48`
- Why it matters:
  - The backend accepts any valid Telegram-signed payload as long as `auth_date` is recent enough. There is no one-time nonce, server-issued challenge, or replay cache. If a valid payload is captured once, it can be submitted repeatedly to mint fresh sessions until it ages out. With the default `TELEGRAM_AUTH_MAX_AGE_SECONDS=86400`, that replay window is one day.
- Risk:
  - Account takeover is possible for any user whose signed Telegram payload is intercepted from mobile deep-link handling, logs, browser history, or a malicious app intercepting the callback.
- Recommended remediation:
  - Restore challenge/state binding end-to-end, or store a short-lived replay cache keyed by `(telegram_id, auth_date, hash)` and reject re-use.
  - Reduce the maximum accepted auth age to the minimum practical window even after replay protection is added.
- Remediation status:
  - Fixed in code by single-use server-side assertion registration plus reduced default `TELEGRAM_AUTH_MAX_AGE_SECONDS=300`.

### SRV-SEC-002 High: Auth rate limiting trusts unvalidated `X-Forwarded-For`

- Evidence:
  - `server/src/main/kotlin/com/bam/incomedy/server/security/AuthRateLimiter.kt:60-66`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/telegram/TelegramAuthRoutes.kt:22-25`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/session/SessionRoutes.kt:37-38`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/session/SessionRoutes.kt:68-69`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/session/SessionRoutes.kt:86-87`
- Why it matters:
  - The limiter key uses the first `X-Forwarded-For` hop whenever that header exists. In common reverse-proxy setups, clients can influence or pre-populate that header, which makes the effective client identity spoofable.
- Risk:
  - An attacker can rotate fake forwarded IPs to bypass per-client limits on Telegram verify, session validation, refresh, and logout endpoints, weakening the project’s mandatory auth abuse protection.
- Recommended remediation:
  - Only trust forwarded headers from explicitly configured proxies.
  - Derive the client IP from Ktor trusted-proxy support or proxy-authenticated metadata, not from raw request headers.
- Remediation status:
  - Fixed in code by removing raw `X-Forwarded-For` from limiter identity and moving route limits to direct peer, authenticated user, token hash, and Telegram account identifiers.

### SRV-SEC-003 Medium: Public auth endpoints have no request body size guard

- Evidence:
  - `server/src/main/kotlin/com/bam/incomedy/server/Application.kt:36-121`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/telegram/TelegramAuthRoutes.kt:32`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/session/SessionRoutes.kt:94`
- Why it matters:
  - The public auth endpoints accept JSON request bodies directly, but the server folder does not define request-size limits or route-level `Content-Length` guards. If edge limits are absent or misconfigured, oversized bodies can consume memory and worker time.
- Risk:
  - This creates a straightforward denial-of-service path against the highest-traffic unauthenticated endpoints.
- Recommended remediation:
  - Enforce strict body limits at both proxy and application layers for auth routes.
  - Reject oversized `Content-Length` early and keep request DTOs on small, explicit payload budgets.
- Remediation status:
  - Fixed in code with explicit route-level body caps for Telegram verify and refresh endpoints. Proxy-level caps are still recommended in deployment.

### SRV-SEC-004 Low: `X-Request-ID` is accepted and reflected with minimal validation

- Evidence:
  - `server/src/main/kotlin/com/bam/incomedy/server/Application.kt:65-70`
- Why it matters:
  - The server accepts any non-blank request ID, reflects it back to the client, and places it into structured logs. That lets callers inject arbitrary correlation IDs and pollute observability data.
- Risk:
  - This does not directly compromise auth, but it reduces log integrity and can complicate incident analysis.
- Recommended remediation:
  - Accept only a safe character set and length, preferably UUID-format values, and generate a server ID for everything else.
- Remediation status:
  - Fixed in code by accepting only UUID `X-Request-ID` values and generating a fresh server id otherwise.

## Verified Controls

- `server/src/main/kotlin/com/bam/incomedy/server/auth/session/JwtSessionTokenService.kt:24-80`
  - Access tokens are signed with JWT HMAC, refresh tokens are generated with `SecureRandom`, and refresh tokens are stored as hashes.
- `server/src/main/kotlin/com/bam/incomedy/server/auth/session/SessionAuthPlugin.kt:24-85`
  - Protected routes use centralized JWT verification plus `session_revoked_at` enforcement.
- `server/src/main/kotlin/com/bam/incomedy/server/auth/session/SessionRoutes.kt:84-155`
  - Refresh flow consumes old refresh tokens and rotates new ones.
- `server/src/main/kotlin/com/bam/incomedy/server/config/AppConfig.kt:10-52`
  - Remote PostgreSQL and Redis connections default to secure transport and fail fast on insecure remote URLs unless explicitly overridden.
- `server/src/main/kotlin/com/bam/incomedy/server/auth/telegram/TelegramAuthVerifier.kt:73-109`
  - Telegram payload fields are validated for positive IDs, age/skew, hash shape, username format, and `https` photo URL.
- `server/src/main/kotlin/com/bam/incomedy/server/Application.kt:80-92`
  - API error responses are sanitized; stack traces stay server-side.
- `server/Dockerfile:1-10`
  - Runtime container uses a non-root `appuser`.

## Test Coverage Gaps

- Current server security tests now cover:
  - Telegram payload verifier validation,
  - replay prevention,
  - body-size limits on public auth routes,
  - request-id validation,
  - rate-limit behavior against spoofed forwarded headers.
- Still missing broader automated coverage for:
  - refresh token rotation success path and invalidation persistence,
  - revocation-aware protected-route middleware on authenticated requests,
  - config hardening rules (`DB_SSL_MODE`, `REDIS_ALLOW_INSECURE`, weak secrets),
  - database-backed replay assertion persistence/migration paths.

## Recommended Next Actions

1. Fix replay protection for Telegram auth first.
2. Replace raw `X-Forwarded-For` trust with trusted-proxy-aware client identity resolution.
3. Add body-size guards for public auth endpoints.
4. Expand server security tests before adding more protected APIs.

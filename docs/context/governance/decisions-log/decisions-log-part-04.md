# Decisions Log Part 04

## D-055

- Date: 2026-03-13
- Status: superseded by `D-057`
- Decision: Start Telegram mobile auth from a first-party HTTPS launch page on `https://incomedy.ru/auth/telegram/launch`, then let that page navigate the browser to `https://oauth.telegram.org/auth`, instead of opening the raw Telegram OIDC URL directly from the mobile app.
- Rationale: Real-device validation on Friday, March 13, 2026 showed that both the documented manual OIDC URL (`client_id`, `redirect_uri`, `response_type=code`, `state`, PKCE) and the live Android Chrome path still end on `origin required` when the app opens `oauth.telegram.org/auth` directly. Telegram's own `telegram-login.js` library is explicitly built around a first-party web origin context, and the live endpoint behavior indicates that browser/provider launch now depends on that context even for OIDC initiation.
- Consequences: `/api/v1/auth/telegram/start` must return an InComedy launch URL rather than a raw Telegram URL; the server must expose `/auth/telegram/launch` plus safe launch telemetry; Android/iOS keep opening the backend-provided URL unchanged; callback bridge and backend code exchange remain intact; `D-054` stays in progress until the new first-party launch surface is validated on a live device/browser path.

## D-056

- Date: 2026-03-13
- Status: superseded by `D-057`
- Decision: Implement Google mobile auth through native platform SDKs (`Credential Manager` + Google Identity on Android, `GoogleSignIn-iOS` on iOS) and send the resulting Google `id_token` to the backend for signature/claim verification and internal session issuance, instead of extending the current generic browser/custom-scheme Google OAuth stub.
- Rationale: The repository context requires compliance-by-design for auth flows, while current official Google mobile guidance is centered on native SDK sign-in surfaces and backend authentication with Google ID tokens. The existing client-side browser stub neither creates a real backend-linked identity nor matches the current recommended mobile path strongly enough to use as production auth.
- Consequences: Google auth needed a dedicated backend `/start` + `/verify` contract, nonce/state issuance and validation, server-side Google JWKS verification, mobile-native effect handling instead of generic external URL launch, and runtime config placeholders for Google client ids without committing secrets. After `D-057`, this slice is no longer part of the active supported auth surface.

## D-057

- Date: 2026-03-14
- Status: superseded by `D-059`
- Decision: Drop Telegram and Google from the active InComedy auth surface and adopt phone number + one-time code as the primary product login standard, while keeping the internal auth architecture provider-agnostic so VK can be added next without another structural rewrite.
- Rationale: Telegram remains operationally blocked upstream, Google auth increases product/compliance scope without matching the current RU-market onboarding priorities, and the product direction now explicitly favors direct phone-based onboarding with a simple fallback/expansion path toward VK. The existing provider-agnostic identity/session model is still the right foundation, but the active provider mix has changed.
- Consequences: Telegram/Google buttons, active runtime requirements, and supported API/docs surface must be removed or marked legacy in the same change; provider abstractions stay in place; the next auth implementation slice is `phone number + code`, followed by `VK ID`.

## D-058

- Date: 2026-03-14
- Status: accepted
- Decision: Every meaningful user-assistant work session must leave a concise, sanitized trace in `docs/context/governance/session-log.md`, summarizing the course of the conversation and implementation rather than only the final result.
- Rationale: The repository already depends on compact governance memory for continuity across chats, but later analysis of why work moved in a certain direction is weak if only final outcomes are stored. A short per-session trace makes scope changes, intermediate decisions, and next steps auditable without needing raw transcript storage.
- Consequences: Engineering standards, quality rules, and the new-chat handoff template must explicitly require session-log updates in `Context / Changes / Decisions / Next` form; raw transcript dumping is not allowed; secrets and sensitive values must remain excluded from those summaries.

## D-059

- Date: 2026-03-14
- Status: accepted
- Decision: Replace the active auth standard with first-party login + password registration/sign-in and treat VK ID as the supported external auth provider, while keeping the internal user/session architecture provider-agnostic.
- Rationale: The product direction changed again: phone/email OTP add delivery dependencies and per-auth operational cost, while standard credentials give a simpler first-party baseline for the current MVP. VK ID remains strategically relevant for RU-market onboarding, but should sit alongside internal credentials rather than behind another pivot to telecom-based auth.
- Consequences: Product/engineering docs and backlog must replace phone-first language with login/password + VK ID; the next implementation slices are credential auth first, then VK ID integration; password hashing, credential-abuse protection, and future recovery/reset flows become mandatory design concerns; earlier phone-first analysis remains historical only.

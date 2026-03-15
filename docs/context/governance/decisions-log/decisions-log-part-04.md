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

## D-060

- Date: 2026-03-15
- Status: accepted
- Decision: Use the official VK ID Android SDK in auth-code mode as the preferred Android VK auth transport, while keeping backend-issued internal sessions as the only application session model and preserving the public HTTPS callback/browser flow as fallback for non-SDK Android setups and other platforms.
- Rationale: Real-device Android behavior showed that the generic browser/public-callback path still creates an unreliable browser -> VK app -> browser -> app bounce. Official VK documentation for Android is centered on the VK ID SDK plus Android-app registration metadata, so the SDK is the correct Android-specific integration surface. At the same time, the repository already depends on a provider-agnostic internal session model for RBAC, linked identities, refresh rotation, logout, and diagnostics, so SDK adoption should improve transport reliability without turning provider tokens into the primary session contract.
- Consequences: Android build/runtime config must add VK SDK dependency, manifest placeholders, and Application initialization; backend VK start/verify contracts must expose optional SDK launch metadata and distinguish SDK verification from browser verification; server diagnostics must record safe source markers for VK verification; VK cabinet setup now requires a dedicated Android VK app/client configuration alongside the existing public-callback flow when browser/iOS fallback remains supported.

## D-061

- Date: 2026-03-15
- Status: accepted
- Decision: Repository code comments required by the standing documentation rule must be written in Russian, with English allowed only for exact technical terms that do not have a stable local equivalent.
- Rationale: The repository context, user communication, and handoff process are now explicitly Russian-first, so mixed-language comments create avoidable friction for future chats and code review. Making Russian the default comment language keeps new code documentation consistent across Android, iOS, shared, and server modules.
- Consequences: `engineering-standards.md`, `quality-rules.md`, and the new-chat handoff template must explicitly require Russian-language code comments; future implementation work should treat non-Russian repository comments in touched scope as documentation debt to normalize when those areas are materially edited.

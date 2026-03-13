# Decisions Log Part 04

## D-055

- Date: 2026-03-13
- Status: accepted
- Decision: Start Telegram mobile auth from a first-party HTTPS launch page on `https://incomedy.ru/auth/telegram/launch`, then let that page navigate the browser to `https://oauth.telegram.org/auth`, instead of opening the raw Telegram OIDC URL directly from the mobile app.
- Rationale: Real-device validation on Friday, March 13, 2026 showed that both the documented manual OIDC URL (`client_id`, `redirect_uri`, `response_type=code`, `state`, PKCE) and the live Android Chrome path still end on `origin required` when the app opens `oauth.telegram.org/auth` directly. Telegram's own `telegram-login.js` library is explicitly built around a first-party web origin context, and the live endpoint behavior indicates that browser/provider launch now depends on that context even for OIDC initiation.
- Consequences: `/api/v1/auth/telegram/start` must return an InComedy launch URL rather than a raw Telegram URL; the server must expose `/auth/telegram/launch` plus safe launch telemetry; Android/iOS keep opening the backend-provided URL unchanged; callback bridge and backend code exchange remain intact; `D-054` stays in progress until the new first-party launch surface is validated on a live device/browser path.

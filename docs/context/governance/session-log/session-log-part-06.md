# Session Log Part 06

## 2026-03-13 14:55

- Context: Live Android validation proved that the backend is healthy again, but the real Chrome path still ends on `origin required` after the app opens the documented Telegram OIDC URL directly.
- Changes: Confirmed outside the app that the raw OIDC authorization URL returned by `/api/v1/auth/telegram/start` still responds with plain `origin required`, even when common `Origin` and `Referer` headers are forced manually. Inspected Telegram's official `telegram-login.js` and found that the library is explicitly built around a first-party web origin context, while the current app path bypasses that context and opens `oauth.telegram.org/auth` directly. Implemented a first-party launch bridge at `/auth/telegram/launch` plus `/auth/telegram/launch/telemetry`, changed `TelegramAuthService.createLaunchRequest()` to return the InComedy launch URL, preserved the existing callback bridge and backend code exchange, added route/resource tests for the new launch surface, and updated the contract/docs so the active Telegram entry path is now `app -> https://incomedy.ru/auth/telegram/launch -> oauth.telegram.org/auth -> https://incomedy.ru/auth/telegram/callback -> incomedy://auth/telegram`.
- Decisions: Accepted `D-055`: Telegram mobile auth must start from a first-party HTTPS launch page before navigating to `oauth.telegram.org/auth`. `D-054` remains in progress until this new launch surface is validated on a live device/browser path.
- Next: Commit and push the first-party launch bridge implementation, wait for CI/CD, then re-run `https://incomedy.ru/health`, `https://incomedy.ru/api/v1/auth/telegram/start`, and the real Android Telegram button flow to confirm whether the `origin required` blocker is removed.

## 2026-03-13 15:21

- Context: Static conformance review against the user-provided official Telegram manual OIDC flow found that the current backend auth URL builder still requested `openid profile` instead of the documented `openid profile phone`.
- Changes: Updated the real `TelegramOidcClient.buildAuthorizationUrl()` implementation to request `scope=openid profile phone`, aligned existing Telegram auth/launch bridge tests with the documented scope, added a dedicated `TelegramOidcClientTest` so the authorization URL contract is covered directly, synchronized architecture/API/traceability docs with the corrected scope usage, and verified the server module with `./gradlew :server:test`.
- Decisions: No new ADR; this change is a conformance fix within existing decisions `D-054` and `D-055`.
- Next: Continue live Telegram auth validation with the first-party launch bridge path and recheck whether the remaining blocker is only the browser/provider-side `origin required` behavior.

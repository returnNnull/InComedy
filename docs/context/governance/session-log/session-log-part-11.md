# Session Log Part 11

## 2026-03-15 05:30

- Context: After the token/session architecture discussion, the user chose a concrete implementation task and requested Android VK OneTap instead of further auth-strategy analysis.
- Changes: Formalized the new Android implementation request, checked the current Android VK SDK integration against the official VK OneTap Compose modules, and confirmed that OneTap can accept externally generated `state`, `codeChallenge`, and `scopes`, which makes it compatible with the repository's backend-issued VK launch metadata if that metadata is prepared before the user taps the widget.
- Decisions: Implement Android VK OneTap as an Android-only presentation/transport layer on top of the existing backend `start/verify` contract; prewarm backend-issued VK launch metadata for the widget instead of letting OneTap invent its own auth state; keep browser/public-callback launch as the fallback path for unavailable or failed OneTap attempts.
- Next: Integrate the official `onetap-compose` SDK module into the Android auth screen, add local preparation state for server-issued VK launch metadata, wire OneTap auth-code callbacks into the shared completion flow, and verify the result with focused Android tests/builds.

## 2026-03-15 05:58

- Context: The Android VK OneTap task moved from design to implementation and needed to stay compatible with the already accepted backend-issued state/PKCE model and the existing browser/public-callback fallback.
- Changes: Added the official Android `onetap-compose` VK module, introduced an explicit shared `prepare provider` path so Android can prewarm VK launch metadata without auto-opening auth UI, extended Android auth UI to render official VK OneTap when prepared launch metadata and SDK config are available, kept a browser fallback action using the same prepared launch request, and added regression coverage for prepared-launch reuse plus updated Android UI wiring. Validation passed with `:composeApp:testDebugUnitTest` and `:feature:auth:allTests`.
- Decisions: Keep OneTap strictly as an Android presentation/transport layer over the existing backend `start/verify` contract and internal session issuance; do not let the widget generate its own independent auth state; keep browser/public-callback fallback available even when OneTap is rendered.
- Next: Run a live Android smoke test against the real VK cabinet/runtime to verify that the prewarmed OneTap path avoids the earlier `failed_oauth_state` incident and that browser fallback still completes correctly when forced.

## 2026-03-15 06:07

- Context: The user requested a fresh live reproduction from the connected Android device and asked to verify first that the needed VK auth API routes are actually recorded by sanitized production diagnostics.
- Changes: Confirmed diagnostics coverage in the server routes for `GET /api/v1/auth/vk/start`, `POST /api/v1/auth/vk/verify`, and `GET /auth/vk/callback`; verified diagnostics retrieval works on production; captured a new device reproduction from the connected Android phone; and correlated it with production diagnostics. The fresh run produced `ActivityTaskManager` launches into `com.vk.id.internal.auth.AuthActivity` and `com.vkontakte.android/com.vk.auth.external.VkExternalAuthActivity`, then returned via `vk54484048://vk.ru/...` into `RedirectUriReceiverActivity`, after which the app logged `android.vk_onetap.failed provider=VK reason=failed_oauth_state`. Production diagnostics for the same window contained only one `auth.vk.start.success` event (`requestId=0a839913-3f1e-4eed-bed6-efce82a3115a`) and no `/api/v1/auth/vk/verify` or `/auth/vk/callback` events.
- Decisions: Treat the reproduced failure as definitively client-side and pre-backend: the new OneTap path is still failing inside the VK Android SDK state check before InComedy backend verification or callback bridge handling begins. The most probable cause remains the Android app-first VK/OneTap transport not preserving the repository's externally supplied long backend-issued `state` reliably enough through the VK app round-trip.
- Next: Redesign Android VK auth to follow VK's frontend-generated state/PKCE model for SDK/backend code exchange, or disable app-first VK/OneTap transport and fall back to browser/public-callback mode until that redesign is implemented.

## 2026-03-15 06:22

- Context: After the live reproduction, the user asked to rebuild Android VK auth exactly according to the official VK Android/OneTap documentation rather than trying shorter backend-issued state values or other undocumented workarounds.
- Changes: Re-checked the official VK Android auth-flow and OneTap docs plus the SDK sources; confirmed that for Android SDK/backend code exchange the client must own `state`, `codeVerifier`, and `codeChallenge`, while the backend receives them only at the verify/exchange step. Declared the previous prewarmed backend-issued Android `state/PKCE` attempt invalid for documented SDK usage and prepared the repository for a contract change: browser/public-callback keeps backend-issued signed state, while Android OneTap/SDK switches to locally generated PKCE/state and sends `code + deviceId + state + codeVerifier` to backend verify.
- Decisions: Align the Android VK implementation with the documented SDK lifecycle even if that means removing the recently added prewarm path. Keep internal InComedy session issuance unchanged after successful provider verification, and keep browser/public-callback as the explicit fallback transport for Android and other platforms.
- Next: Update the Android shared/UI/backend VK contracts, remove backend-issued PKCE dependence from Android OneTap, and cover the new flow with focused unit tests before the next live device check.

## 2026-03-15 06:49

- Context: The documented Android VK redesign moved from decision to code and needed to replace the previous prewarm-based implementation without regressing browser/public-callback completion.
- Changes: Removed Android dependence on backend-issued VK SDK metadata from the shared/UI contract; Android now generates local `state`, `codeVerifier`, and `codeChallenge` for OneTap, passes `code + deviceId + state + codeVerifier` to backend `/api/v1/auth/vk/verify`, and uses browser/public-callback only as fallback. Simplified the shared auth state by dropping the temporary `prepare provider` cache path, updated backend VK verify/start contracts to distinguish browser signed-state validation from Android PKCE verification, and refreshed focused tests. Validation passed with `:feature:auth:allTests`, `:composeApp:testDebugUnitTest`, and `:server:test --tests 'com.bam.incomedy.server.auth.vk.*'`.
- Decisions: Keep the new split lifecycle as the supported shape: browser/public-callback remains server-issued signed state, Android OneTap remains client-generated PKCE/state per VK docs. Do not reintroduce backend-issued Android `state/PKCE` metadata.
- Next: Run a live Android smoke check against the connected device and production VK cabinet to confirm that OneTap no longer fails with `failed_oauth_state` and that browser fallback still reaches backend `/verify`.

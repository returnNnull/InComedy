# Session Log Part 05

## 2026-03-13 12:15

- Context: Product temporarily requested rollback to the previous Telegram auth flow after the official OIDC rollout was considered unacceptable for the current RU launch slice.
- Changes: Analyzed commit chain (`421979b -> faa08c0 -> e2eab1e -> 39e874c`), temporarily restored code/contracts/docs to the legacy Telegram verify flow in a local corrective branch, and documented the regression mechanism plus missing rollout guardrails while preserving diagnostics, session, identity, role, and workspace foundations.
- Decisions: No new long-lived ADR was kept from this rollback step; it was a temporary corrective action that would be superseded if fresh device/browser evidence still showed the direct legacy mobile launch path failing before backend verify.
- Next: Reproduce the legacy flow on device and determine whether the remaining failure is in backend verify or earlier in browser/provider launch.

## 2026-03-13 13:40

- Context: After the temporary rollback, Telegram auth was reproduced on Android emulator through the real UI button to determine whether the remaining failure is in backend verify or in pre-backend browser launch.
- Changes: Installed the current debug build on the emulator, reproduced the Telegram button flow, captured `adb logcat`, Chrome page state, Chrome DevTools tab metadata, and server diagnostics. The result showed that Android reaches external browser launch, but server diagnostics remain empty for `/api/v1/auth/telegram/*`, so the failure happens before backend verify. To isolate the Android launch path, the auth screen was switched from Compose `LocalUriHandler` to an explicit `ACTION_VIEW` browser intent with safe URI summaries for the outgoing URL and final `intent.data`.
- Decisions: Continue treating the temporary legacy Telegram regression as a pre-backend Android/browser launch issue until a verify request or callback reaches the backend.
- Next: Reinstall the updated Android build, reproduce the Telegram button flow again, inspect the new `android.external_auth.intent_*` logs, and use them to determine whether query parameters are lost inside the app or later inside browser/provider handling.

## 2026-03-13 14:05

- Context: The Android diagnostic build with explicit browser intent logging was reinstalled to prove whether the app itself drops Telegram query parameters before handing off to Chrome.
- Changes: Reproduced the Telegram login flow again on the emulator, captured the new `AUTH_FLOW` entries, and confirmed that `android.external_auth.intent_prepared` plus `android.external_auth.intent_started` both contain the full sanitized key set `bot_id,origin,request_access,return_to,state`. Server diagnostics still remained empty for `/api/v1/auth/telegram/*`, and Chrome still ended on the user-visible `origin required` error.
- Decisions: The direct legacy `oauth.telegram.org/auth?...` mobile launch issue is not caused by KMP URL construction or Compose-to-Android intent bridging; treat it as a browser/provider-side failure of the legacy direct-launch path.
- Next: Choose one supported Telegram entry strategy for the active slice: either a first-party web page on `incomedy.ru` that hosts the Telegram login widget/controlled handoff, or a validated return to the official Telegram OIDC flow.

## 2026-03-13 14:20

- Context: After the legacy direct-launch reproduction still failed before backend verify, the user explicitly selected the official Telegram OIDC option.
- Changes: Reactivated the official Telegram OIDC flow by reapplying the revert of `23b3400`, then manually restored the incomplete files that did not come back cleanly (`Application.kt`, `AppConfig.kt`, `AuthDataModule.kt`, callback bridge route/static HTML, and related tests). Kept the explicit Android `ACTION_VIEW` diagnostics in `AuthScreen` so browser handoff remains observable on device, fixed the bridge HTML `pagehide` listener syntax, and resynchronized `docs/context/*` with active decisions `D-053` and `D-054`.
- Decisions: Continue with accepted `D-053` and `D-054`; the active Telegram path is again the official OIDC authorization-code flow with HTTPS callback bridge back into the app.
- Next: Run auth/backend/android verification, then deploy with `TELEGRAM_LOGIN_CLIENT_ID`, `TELEGRAM_LOGIN_CLIENT_SECRET`, and `TELEGRAM_LOGIN_STATE_SECRET` configured and validate the end-to-end device flow.

## 2026-03-13 14:24

- Context: The user requested execution of the restored Telegram OIDC branch all the way through `push -> CI/CD -> live server check -> Telegram button smoke test`.
- Changes: Committed the restoration as `b5765f2` (`fix: restore Telegram OIDC bridge flow`) and pushed it to `origin/main`. Observed GitHub Actions for this exact SHA: `CI Server` and `CI Android` completed `success`, while `CD Server` failed in job `deploy-staging`, step `Deploy with docker compose`. External smoke checks on Friday, 2026-03-13, returned `502` from both `https://incomedy.ru/health` and `https://incomedy.ru/api/v1/auth/telegram/start`. Reinstalled the current debug build on the Android emulator, pressed the Telegram button through the real UI, and captured `AUTH_FLOW stage=start_auth.requested provider=TELEGRAM` followed by `AUTH_FLOW stage=start_auth.failed provider=TELEGRAM reason=Request failed with status 502`; the auth screen also rendered the same `Request failed with status 502` message before any browser handoff. Local config inspection confirms that the active OIDC server startup now requires `TELEGRAM_LOGIN_CLIENT_ID` and `TELEGRAM_LOGIN_CLIENT_SECRET`, so staging runtime env drift in `/opt/incomedy/server/.env` is the leading deployment-failure hypothesis. Direct host confirmation was not possible from the current local operator access.
- Decisions: Treat live Telegram auth as currently broken because the backend deployment did not complete successfully and the public server is unhealthy. Do not spend more time on browser/callback debugging until the server answers `200` on `/health`.
- Next: Update staging `/opt/incomedy/server/.env` with the OIDC-required Telegram runtime variables, rerun the staging compose deployment, confirm `GET /health` returns `200`, and only then rerun the Telegram mobile smoke test.

## V-001

- Дата обнаружения: 2026-02-24
- Категория: `security`
- Уязвимость: Mobile auth/session token сохранялся в plain local storage (`SharedPreferences`/`UserDefaults`).
- Затронутые компоненты: `composeApp` auth wrapper, `iosApp` auth wrapper.
- Severity: High
- Exploitability: Medium
- Current exposure: Mitigated in code by migration to secure storage; требует rollout на client updates.
- Immediate containment: Не логировать токены; использовать secure storage для всех новых writes.
- Remediation plan: Завершить release rollout с secure storage migration и проверить migration telemetry на Android/iOS.
- Target fix date: 2026-02-26
- Owner: Engineering
- Связанные артефакты: `D-037`
- Status: in-progress

## V-002

- Дата обнаружения: 2026-03-06
- Категория: `security`
- Уязвимость: Legacy Telegram auth payloads можно replay-ить в пределах accepted auth-age window, потому что backend verification не имеет one-time nonce/state binding или replay cache.
- Затронутые компоненты: `server/auth/telegram/TelegramAuthVerifier`, `server/auth/telegram/TelegramAuthRoutes`, Telegram mobile callback completion flow.
- Severity: High
- Exploitability: Medium
- Current exposure: Contained only if Telegram auth removed from the active app/runtime surface; legacy slice не должен считаться supported product entry point.
- Immediate containment: Держать Telegram auth выключенным в активном app surface и не re-enable legacy route family без отдельного decision.
- Remediation plan: Удалить или архивировать legacy Telegram auth slice из supported runtime/docs, пока реализован новый login/password + VK path.
- Target fix date: 2026-03-10
- Owner: Engineering
- Связанные артефакты: `docs/context/handoff/task-request-template/task-request-template-part-02.md`
- Status: superseded

## V-003

- Дата обнаружения: 2026-03-06
- Категория: `security`
- Уязвимость: Auth rate limiting доверял caller-controlled `X-Forwarded-For`, что позволяло spoof fingerprint и bypass limiter-а.
- Затронутые компоненты: `server/security/AuthRateLimiter`, `server/auth/telegram/TelegramAuthRoutes`, `server/auth/session/SessionRoutes`.
- Severity: High
- Exploitability: High
- Current exposure: Mitigated in code by removing raw `X-Forwarded-For` trust from rate limiting; pending deployment of updated server build.
- Immediate containment: Развернуть обновлённый server build и держать direct access к app container закрытым.
- Remediation plan: Сохранить direct-peer/auth-identity based limiting как default и добавлять trusted-proxy-aware client IP resolution только при явной необходимости.
- Target fix date: 2026-03-10
- Owner: Engineering
- Связанные артефакты: `docs/context/handoff/task-request-template/task-request-template-part-02.md`
- Status: in-progress

## V-004

- Дата обнаружения: 2026-03-06
- Категория: `security`
- Уязвимость: Public auth endpoints не ограничивали размер request body, оставляя Telegram verify и refresh endpoints уязвимыми к oversized-request DoS.
- Затронутые компоненты: `server/Application`, `server/auth/telegram/TelegramAuthRoutes`, `server/auth/session/SessionRoutes`.
- Severity: Medium
- Exploitability: Medium
- Current exposure: Mitigated in code with route-level body caps; pending deployment of updated server build. Proxy-side caps всё ещё рекомендуются как defense in depth.
- Immediate containment: Развернуть обновлённый server build и держать консервативные reverse-proxy body-size limits перед server.
- Remediation plan: Сохранить application-level caps, добавить proxy-level caps в deploy infrastructure и удерживать oversized-payload regression tests.
- Target fix date: 2026-03-12
- Owner: Engineering
- Связанные артефакты: `docs/context/handoff/task-request-template/task-request-template-part-02.md`
- Status: in-progress

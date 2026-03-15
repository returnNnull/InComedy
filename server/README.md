# InComedy Server

Ktor backend module with internal session, identity, diagnostics, and organizer-workspace foundation on top of PostgreSQL.

## Run

1. Export values from `server/.env.example`.
2. Start PostgreSQL.
3. Run:

```bash
./gradlew :server:run
```

## Docker

Build image locally:

```bash
./gradlew :server:installDist
docker build -f server/Dockerfile -t incomedy-server:local .
```

Run with env file:

```bash
docker run --rm --env-file server/.env.example -p 8080:8080 incomedy-server:local
```

### Docker Compose

```bash
cp deploy/server/.env.example deploy/server/.env
cd deploy/server
docker compose up -d
docker compose ps
```

Use a pinned image tag in `deploy/server/.env`:

```env
IMAGE=ghcr.io/<owner>/incomedy-server:<release-tag-or-sha>
```

Do not deploy with `latest`.

## Current Auth Status

- Telegram and Google are no longer part of the active supported auth surface.
- The current product standard is first-party `login + password`.
- `VK ID` is the supported external provider on top of the same internal user/session model.
- The backend still keeps provider-agnostic user/session foundations so new providers can be added without changing business ids.
- The canonical public VK callback URI is `https://incomedy.ru/auth/vk/callback`.
- Android can additionally use the official VK ID Android SDK in auth-code mode when a dedicated Android VK client id/redirect pair is configured.
- Browser/public-callback VK verification and Android SDK verification still converge into the same backend-issued internal session contract.

## Environment

Required:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`

Optional:

- `VK_ID_CLIENT_ID`
- `VK_ID_REDIRECT_URI`
- `VK_ID_ANDROID_CLIENT_ID`
- `VK_ID_ANDROID_REDIRECT_URI`
- `VK_ID_SCOPE`
- `VK_ID_STATE_SECRET`
- `VK_ID_STATE_TTL_SECONDS`
- `IOS_ASSOCIATED_DOMAIN_APP_IDS`
- `JWT_ISSUER`
- `JWT_ACCESS_TTL_SECONDS`
- `JWT_REFRESH_TTL_SECONDS`
- `REDIS_URL`
- `REDIS_ALLOW_INSECURE`
- `DB_SSL_MODE`
- `DB_ALLOW_INSECURE`
- `DIAGNOSTICS_ACCESS_TOKEN`
- `DIAGNOSTICS_RETENTION_LIMIT`

VK auth routes return `503` until both `VK_ID_CLIENT_ID` and `VK_ID_REDIRECT_URI` are configured. The expected production callback value is `https://incomedy.ru/auth/vk/callback`.

When `VK_ID_ANDROID_CLIENT_ID` and `VK_ID_ANDROID_REDIRECT_URI` are also configured, `GET /api/v1/auth/vk/start` returns extra Android SDK launch metadata and `POST /api/v1/auth/vk/verify` accepts `client_source=android_sdk` so the backend can exchange the code against the dedicated Android VK client configuration.

When `IOS_ASSOCIATED_DOMAIN_APP_IDS` is configured with comma-separated Apple app ids (`TEAMID.bundleId`), the backend also serves Apple App Site Association metadata for the VK callback URL.

## Security Defaults

- Server startup runs Flyway migrations before serving traffic.
- Credential passwords are hashed with `Argon2id`.
- Credential login/register and VK auth routes are protected by route-specific rate limits.
- Caddy adds baseline security headers and TLS termination.
- The container runs as non-root `appuser`.
- `X-Request-ID` is accepted only in UUID format; invalid values are replaced server-side.
- Diagnostics retrieval is operator-only and returns sanitized events, not raw server logs.
- Protected routes use JWT session auth plus rate limiting.

## Diagnostics

When `DIAGNOSTICS_ACCESS_TOKEN` is set, the backend exposes:

- `GET /api/v1/diagnostics/events`

Supported filters:

- `request_id`
- `route_prefix`
- `stage`
- `status`
- `from`
- `to`
- `limit`

Example:

```bash
INCOMEDY_DIAGNOSTICS_BASE_URL=https://incomedy.ru \
INCOMEDY_DIAGNOSTICS_TOKEN=replace-me \
scripts/fetch_server_diagnostics.sh --request-id 123e4567-e89b-12d3-a456-426614174000
```

VK auth diagnostics now record a safe `client_source` marker such as `browser_bridge` or `android_sdk` so operator traces can distinguish public-callback and Android SDK completions without exposing provider secrets.

## Public API

- `GET /health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/vk/start`
- `POST /api/v1/auth/vk/verify`
- `GET /api/v1/auth/session/me`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/me/roles`
- `POST /api/v1/me/active-role`
- `GET /api/v1/workspaces`
- `POST /api/v1/workspaces`
- `GET /api/v1/diagnostics/events` when diagnostics are enabled

## Public Auth Bridges

- `GET /auth/vk/callback`
- `GET /auth/vk/callback/telemetry`
- `GET /.well-known/apple-app-site-association` when `IOS_ASSOCIATED_DOMAIN_APP_IDS` is configured
- `GET /apple-app-site-association` when `IOS_ASSOCIATED_DOMAIN_APP_IDS` is configured

## CI/CD

- `/.github/workflows/ci-server.yml` runs `:server:test` and `:server:installDist`.
- `/.github/workflows/cd-server.yml` builds/pushes a GHCR image and deploys through SSH + Docker Compose.

Runtime `.env` on the target host lives at:

- `/opt/incomedy/server/.env`

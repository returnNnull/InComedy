# InComedy Server

Ktor backend module with Telegram auth verification and PostgreSQL persistence.

## Run

1. Copy `server/.env.example` values into your environment.
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

### Docker Compose (Server + PostgreSQL)

```bash
cp deploy/server/.env.example deploy/server/.env
```

Update secrets in `deploy/server/.env`, then run:

```bash
cd deploy/server
docker compose up -d
docker compose ps
```

### Database Migrations

- Server startup runs versioned Flyway migrations before serving traffic.
- Clean databases are created from `server/src/main/resources/db/migration/*`.
- Existing initialized databases are upgraded in place; schema history is tracked in `flyway_schema_history`.
- Ordinary deploys do not recreate the database because deploy compose keeps PostgreSQL data in the named volume `postgres_data`.
- Data is lost only if the volume is explicitly removed, the host is replaced without restoring the volume, or `DB_URL` points to a different database.

### Domain + HTTPS (Caddy)

`deploy/server/Caddyfile` is used for TLS termination and reverse proxy:

- `incomedy.ru` -> `server:8080`
- `www.incomedy.ru` -> redirect to `https://incomedy.ru`
- `api.incomedy.ru` -> `server:8080`

For production domain use:

```bash
cd /opt/incomedy/server
docker compose --env-file .env up -d
```

Security defaults:

- Caddy config adds baseline security headers and CSP.
- Server container runs as non-root `appuser`.
- Telegram auth uses the official `oauth.telegram.org` authorization-code flow, but mobile clients first open a first-party `https://incomedy.ru/auth/telegram/launch` page so the browser starts Telegram auth from an approved InComedy origin before backend `/token` exchange, signed server-side `state`, PKCE, and `id_token` verification against Telegram JWKS.
- Telegram auth assertions remain single-use server-side after successful OIDC verification.
- Public auth JSON bodies are capped in application code (`4 KiB` for Telegram verify, `2 KiB` for refresh).
- `X-Request-ID` is accepted only in UUID format; invalid values are replaced by a server-generated id.
- If `DIAGNOSTICS_ACCESS_TOKEN` is configured, the server keeps a bounded in-memory store of sanitized diagnostics events and exposes an operator-only retrieval endpoint.

## Required Environment Variables

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `TELEGRAM_LOGIN_CLIENT_ID`
- `TELEGRAM_LOGIN_CLIENT_SECRET`

Optional:

- `REDIS_URL` - enables distributed auth rate limiting (recommended for multi-instance deployment).
- `DB_SSL_MODE` - PostgreSQL SSL mode (`disable`, `require`, `verify-full`, ...). Default: `disable` for local DB hosts, `require` for remote hosts.
- `DB_ALLOW_INSECURE` - set `true` only to allow remote DB without TLS.
- `REDIS_ALLOW_INSECURE` - set `true` only to allow remote `redis://` without TLS.
- `TELEGRAM_LOGIN_REDIRECT_URI` - registered Telegram login redirect URI. Default: `https://incomedy.ru/auth/telegram/callback`.
- `TELEGRAM_LOGIN_STATE_SECRET` - HMAC secret for server-issued Telegram login `state`. Defaults to `JWT_SECRET` when omitted.
- `TELEGRAM_LOGIN_STATE_TTL_SECONDS` - maximum lifetime of a Telegram login `state`. Default: `600`.
- `TELEGRAM_BOT_TOKEN` - optional Telegram bot token if other backend slices still need bot access.
- `DIAGNOSTICS_ACCESS_TOKEN` - enables operator-only diagnostics retrieval endpoint when set.
- `DIAGNOSTICS_RETENTION_LIMIT` - bounded number of sanitized diagnostics events to retain in memory. Default: `1000`.

Security notes:

- In deploy compose, Postgres is no longer published to host (`5432` is internal only).
- For remote Redis use `rediss://...` unless you intentionally opt out with `REDIS_ALLOW_INSECURE=true`.
- Auth rate limiting no longer trusts raw `X-Forwarded-For`; protected routes key limits by authenticated user and public routes use direct peer/token/account identifiers.
- Diagnostics retrieval is operator-only via `X-Diagnostics-Token` and returns sanitized events, not raw server logs.

## Diagnostics Retrieval

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

Required header:

- `X-Diagnostics-Token: <operator token>`

Example:

```bash
INCOMEDY_DIAGNOSTICS_BASE_URL=https://incomedy.ru \
INCOMEDY_DIAGNOSTICS_TOKEN=replace-me \
scripts/fetch_server_diagnostics.sh --request-id 123e4567-e89b-12d3-a456-426614174000
```

Notes:

- Returned events are newest-first.
- Diagnostics store is bounded in memory and is intended for recent troubleshooting, not long-term audit retention.
- Use the `requestId` surfaced in backend error messages/device logs to correlate a failing client action with server-side diagnostics.

## API

### Health

- `GET /health`

### Telegram auth start

- `GET /api/v1/auth/telegram/start`

Response body:

```json
{
  "auth_url": "https://incomedy.ru/auth/telegram/launch?state=signed_state",
  "state": "signed_state"
}
```

### Telegram auth verify

- `POST /api/v1/auth/telegram/verify`
- Request body:

```json
{
  "code": "tg_login_authorization_code",
  "state": "signed_state"
}
```

### Auth Refresh

- `POST /api/v1/auth/refresh`
- Request body:

```json
{
  "refresh_token": "opaque_refresh_token"
}
```

## CI/CD

Workflows:

- `/.github/workflows/ci-server.yml`:
  - runs `:server:test` and `:server:installDist` on PR/push.
- `/.github/workflows/cd-server.yml`:
  - builds and pushes Docker image to GHCR on `main`.
  - deploys to staging host via SSH + Docker Compose.

Required GitHub secrets for `staging` environment:

- `STAGING_SSH_HOST`
- `STAGING_SSH_PORT`
- `STAGING_SSH_USER`
- `STAGING_SSH_KEY`
- `STAGING_GHCR_USER`
- `STAGING_GHCR_TOKEN`

Runtime `.env` is managed directly on the server at:

- `/opt/incomedy/server/.env`

- Response body:

```json
{
  "access_token": "jwt",
  "refresh_token": "random_token",
  "expires_in": 3600,
  "user": {
    "id": "uuid",
    "display_name": "John Doe",
    "username": "johndoe",
    "photo_url": "https://..."
  }
}
```

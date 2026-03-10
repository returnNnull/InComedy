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
- Telegram auth payload is validated for format/length/https constraints before hash verification.
- Telegram auth assertions are single-use server-side and default auth-age is `300` seconds.
- Public auth JSON bodies are capped in application code (`4 KiB` for Telegram verify, `2 KiB` for refresh).
- `X-Request-ID` is accepted only in UUID format; invalid values are replaced by a server-generated id.

## Required Environment Variables

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `TELEGRAM_BOT_TOKEN`

Optional:

- `REDIS_URL` - enables distributed auth rate limiting (recommended for multi-instance deployment).
- `DB_SSL_MODE` - PostgreSQL SSL mode (`disable`, `require`, `verify-full`, ...). Default: `disable` for local DB hosts, `require` for remote hosts.
- `DB_ALLOW_INSECURE` - set `true` only to allow remote DB without TLS.
- `REDIS_ALLOW_INSECURE` - set `true` only to allow remote `redis://` without TLS.
- `TELEGRAM_AUTH_MAX_AGE_SECONDS` - maximum accepted Telegram auth assertion age. Default: `300`.

Security notes:

- In deploy compose, Postgres is no longer published to host (`5432` is internal only).
- For remote Redis use `rediss://...` unless you intentionally opt out with `REDIS_ALLOW_INSECURE=true`.
- Auth rate limiting no longer trusts raw `X-Forwarded-For`; protected routes key limits by authenticated user and public routes use direct peer/token/account identifiers.

## API

### Health

- `GET /health`

### Telegram auth verify

- `POST /api/v1/auth/telegram/verify`
- Request body:

```json
{
  "id": 123456789,
  "first_name": "John",
  "last_name": "Doe",
  "username": "johndoe",
  "photo_url": "https://t.me/i/userpic/320/johndoe.jpg",
  "auth_date": 1700000000,
  "hash": "telegram_hash"
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
    "photo_url": "https://...",
    "roles": ["audience"],
    "active_role": "audience",
    "linked_providers": ["telegram"]
  }
}
```

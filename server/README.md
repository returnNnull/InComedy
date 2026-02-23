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

## Required Environment Variables

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `TELEGRAM_BOT_TOKEN`

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
- `STAGING_SERVER_DOTENV` (full `.env` file content from `deploy/server/.env`)

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

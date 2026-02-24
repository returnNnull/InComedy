# API Contracts

This directory stores API contracts used between client and backend.

## Recommended Structure

- `v1/openapi.yaml` for HTTP API contracts.
- Optional per-domain schemas if needed (e.g., `events/`, `tickets/`, `chat/`).

## Current Contract

- `v1/openapi.yaml`
  - Health endpoints: `/health`
  - Auth endpoints:
    - `/auth/telegram/callback`
    - `/api/v1/auth/telegram/verify`

## Rules

- Contract updates must happen in the same change as backend/client behavior changes.
- Breaking changes require versioning or documented migration plan.
- Do not merge API behavior changes without corresponding contract updates.

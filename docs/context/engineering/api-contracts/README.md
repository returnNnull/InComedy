# API Contracts

This directory stores API contracts used between client and backend.

## Recommended Structure

- `v1/openapi.yaml` for HTTP API contracts.
- Optional per-domain schemas if needed (e.g., `events/`, `tickets/`, `chat/`).

## Current Contract

- `v1/openapi.yaml`
  - Health endpoints: `/health`
  - Session/auth endpoints:
    - `/api/v1/auth/register`
    - `/api/v1/auth/login`
    - `/api/v1/auth/vk/start`
    - `/api/v1/auth/vk/verify`
    - `/api/v1/auth/session/me`
    - `/api/v1/auth/refresh`
    - `/api/v1/auth/logout`
  - Identity endpoints:
    - `/api/v1/me/roles`
    - `/api/v1/me/active-role`
  - Workspace endpoints:
    - `/api/v1/workspaces`
  - Diagnostics endpoints:
    - `/api/v1/diagnostics/events`
  - Public auth bridge routes outside the versioned API contract:
    - `/auth/vk/callback`
    - `/.well-known/apple-app-site-association`

## Rules

- Contract updates must happen in the same change as backend/client behavior changes.
- Breaking changes require versioning or documented migration plan.
- Do not merge API behavior changes without corresponding contract updates.

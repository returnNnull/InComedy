# API Contracts

This directory stores API contracts used between client and backend.

## Recommended Structure

- `v1/openapi.yaml` for HTTP API contracts.
- Optional per-domain schemas if needed (e.g., `events/`, `tickets/`, `chat/`).

## Rules

- Contract updates must happen in the same change as backend/client behavior changes.
- Breaking changes require versioning or documented migration plan.
- Do not merge API behavior changes without corresponding contract updates.

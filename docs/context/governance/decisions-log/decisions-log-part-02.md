# Decisions Log Part 02

## D-016

- Date: 2026-02-23
- Status: accepted
- Decision: Add context-operability artifacts (task template, integrity checklist, rollout plan, and decision traceability matrix).
- Rationale: Reduces context drift and clarifies responsibility split between product input and engineering execution.
- Consequences: New governance docs are now part of default operating protocol for major tasks.

## D-017

- Date: 2026-02-23
- Status: accepted
- Decision: Require proactive assistant reminders to refresh task input/backlog before major implementation blocks.
- Rationale: Prevents stale priorities and under-specified tasks from propagating incorrect context into code changes.
- Consequences: Reminder rule added to context protocol and project context responsibilities.

## D-018

- Date: 2026-02-23
- Status: accepted
- Decision: Use free-form task intake by default; assistant is responsible for formalizing requests into project templates and context updates.
- Rationale: Reduces user overhead while preserving documentation discipline and context integrity.
- Consequences: Task-template usage remains mandatory for major work, but it is filled and maintained by assistant unless user asks otherwise.

## D-019

- Date: 2026-02-23
- Status: accepted
- Decision: Keep `chat-handoff-template.md` auto-synced with `context-protocol.md`.
- Rationale: Prevents transfer-template drift when protocol rules evolve.
- Consequences: Any protocol change affecting handoff behavior must include template update in the same change.

## D-020

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize Android app navigation on `androidx.navigation:navigation-compose`.
- Rationale: Provides a stable and well-supported route graph foundation for upcoming multi-screen Android flows.
- Consequences: `composeApp` must define screen routing through `NavHost`; new Android screens should register routes in the navigation graph.

## D-021

- Date: 2026-02-23
- Status: accepted
- Decision: When splitting oversized context documents, store parts in a dedicated subfolder (`name/name-part-XX.md`) instead of the parent folder root.
- Rationale: Keeps context directories clean and improves scan/readability in large governance docs.
- Consequences: Existing and future split context files must follow folder-based part storage; index files must reference subfolder paths.

## D-022

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize Android navigation architecture as root graph + feature-owned nested subgraphs.
- Rationale: Flat navigation becomes hard to maintain as feature count grows; subgraphs preserve modular boundaries and predictable ownership.
- Consequences: New Android features must expose navigation registration via dedicated `feature/<name>/navigation/*` graph files and integrate through root app graph.

## D-023

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize iOS navigation architecture as root app graph container + feature-owned graph views.
- Rationale: Direct root-screen switching in `ContentView` does not scale; graph decomposition keeps iOS feature boundaries explicit and maintainable.
- Consequences: iOS navigation should be coordinated in app/graph layer (`Navigation/*`), while features expose dedicated navigation views under `Features/<Feature>/Navigation/*`.

## D-024

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize KMP ViewModel consumption via native platform wrappers (AndroidX ViewModel on Android, ObservableObject on iOS).
- Rationale: Native wrappers provide lifecycle-safe flow collection and keep shared ViewModels platform-agnostic.
- Consequences: Android UI must not bind directly to shared ViewModel instances; iOS UI must observe shared state/events through wrapper/adapter objects with explicit subscription disposal.

## D-025

- Date: 2026-02-23
- Status: accepted
- Decision: Add in-repo `server` module on Ktor with PostgreSQL persistence and implement Telegram server-side auth verification as first production auth backend step.
- Rationale: P0 requires real backend auth completion (`code/session` flow) and secure provider data verification on server side.
- Consequences: Telegram auth payload verification (`hash` + `auth_date`) and app session token issuance now live in backend; next increments should add Google and VK exchange endpoints in the same server module.

## D-026

- Date: 2026-02-23
- Status: accepted
- Decision: Keep auth provider integrations in dedicated mobile data layer (`data/auth`) and route Telegram completion through backend verify endpoint from that layer.
- Rationale: Preserves Clean dependency direction (`presentation -> domain -> data`) and avoids networking/provider coupling inside `feature/auth` domain/mvi module.
- Consequences: Provider implementations moved from `feature/auth` to `data/auth`; deep-link callbacks on Android/iOS now pass Telegram payload to shared auth flow which completes via backend API call.

## D-027

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize server delivery on GitHub Actions CI/CD with Docker image publishing to GHCR and staging deploy via SSH + Docker Compose.
- Rationale: Provides reproducible build, automated validation, and low-friction deployments for initial server rollout.
- Consequences: Repository now includes server CI/CD workflows, Dockerfile, and deploy compose manifest; staging secrets must be configured in GitHub environment before first deployment.

## D-028

- Date: 2026-02-23
- Status: accepted
- Decision: Provide PostgreSQL as a first-class containerized dependency in deploy stack (`deploy/server/docker-compose.yml`) for staging/bootstrap environments.
- Rationale: Simplifies first deployment and reduces manual infrastructure setup for backend auth rollout.
- Consequences: Deploy env now includes Postgres credentials and persisted volume; server DB connection is wired to compose `postgres` service by default.

## D-029

- Date: 2026-02-23
- Status: accepted
- Decision: Staging deployment must use server-local runtime env file (`/opt/incomedy/server/.env`) and no longer rely on `STAGING_SERVER_DOTENV` secret injection.
- Rationale: Prevents repeated deploy failures caused by malformed multiline secret payloads and keeps env management explicit on host.
- Consequences: CD workflow now validates only file existence and uses existing server env; GitHub staging secrets list excludes `STAGING_SERVER_DOTENV`.

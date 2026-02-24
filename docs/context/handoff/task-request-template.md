# Task Request Template

Use this template for new implementation tasks.

## Context

- Related docs/decisions:
- Current constraints:

## Goal

- What should be delivered:

## Scope

- In scope:
- Out of scope:

## Constraints

- Tech/business constraints:
- Deadlines or milestones:

## Definition of Done

- Functional result:
- Required tests:
- Required docs updates:

---

## Latest Formalized Request

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (`P0`: social auth, deep-link callback)
  - `docs/context/engineering/tooling-stack.md`
  - `D-012` (platform-specific UI split), `D-015` (Koin DI standard)
- Current constraints:
  - Keep platform-specific UI split (Android Compose, iOS SwiftUI).
  - Do not change auth business logic during navigation library onboarding.

## Goal

- What should be delivered:
  - Add an Android navigation library to project dependencies.
  - Wire minimal navigation host so app entry goes through a navigation graph.

## Scope

- In scope:
  - `gradle/libs.versions.toml` dependency alias for navigation.
  - `composeApp/build.gradle.kts` dependency wiring.
  - `composeApp/src/main/kotlin/com/bam/incomedy/App.kt` minimal `NavHost`.
- Out of scope:
  - Multi-screen redesign.
  - iOS navigation refactor.
  - Auth backend/deep-link completion.

## Constraints

- Tech/business constraints:
  - Follow `MVI` and existing feature boundaries.
  - Keep current auth UX and DI initialization.
- Deadlines or milestones:
  - Prepare base navigation layer for the next implementation block.

## Definition of Done

- Functional result:
  - Android app uses configured navigation library and renders auth route via `NavHost`.
- Required tests:
  - Build verification for `composeApp` and existing auth unit tests remain green.
- Required docs updates:
  - `docs/context/engineering/tooling-stack.md`
  - `docs/context/governance/decisions-log.md`
  - `docs/context/governance/decision-traceability.md`
  - `docs/context/governance/session-log.md`

---

## Latest Formalized Request (Navigation Subgraphs)

## Context

- Related docs/decisions:
  - `docs/context/engineering/engineering-standards.md` (MVI/effect-driven navigation)
  - `D-020` (Android navigation standard)
- Current constraints:
  - Keep current auth UI behavior unchanged.
  - Prepare scalable navigation structure for upcoming feature growth.

## Goal

- What should be delivered:
  - Reorganize Android navigation into root host + feature subgraph.
  - Move auth route registration into dedicated auth navigation package.

## Scope

- In scope:
  - `AppNavHost` root navigation container.
  - `AuthGraph` nested graph and route constants.
  - `App.kt` wiring through new navigation layer.
- Out of scope:
  - New screens and cross-feature transitions.
  - iOS navigation updates.

## Constraints

- Tech/business constraints:
  - Keep platform-specific UI split.
  - Keep ViewModel free from `NavController` dependencies.
- Deadlines or milestones:
  - Deliver as base structure for next feature navigation increments.

## Definition of Done

- Functional result:
  - App boots through root `NavHost` and renders auth screen via auth subgraph.
- Required tests:
  - `composeApp` debug assembly succeeds.
- Required docs updates:
  - `docs/context/governance/session-log/*`
  - `docs/context/governance/decision-traceability.md`

---

## Latest Formalized Request (Rule + Full Commit)

## Context

- Related docs/decisions:
  - `D-020` (navigation-compose standard),
  - current navigation subgraph implementation in `composeApp`.
- Current constraints:
  - Keep documentation as source of truth.
  - Include script changes in the same final commit.

## Goal

- What should be delivered:
  - Add explicit rule that navigation must be decomposed into subgraphs.
  - Commit all pending workspace changes, including script updates.

## Scope

- In scope:
  - Context updates in engineering/governance docs.
  - Full git commit of current working tree changes.
- Out of scope:
  - New feature screens implementation.

## Constraints

- Tech/business constraints:
  - Rules must be documented before implementation/commit completion.
- Deadlines or milestones:
  - Complete in current session.

## Definition of Done

- Functional result:
  - Rule is documented and decision recorded.
  - All pending changes are committed, including `scripts/*`.
- Required tests:
  - `:composeApp:assembleDebug`.
- Required docs updates:
  - `engineering-standards`, `architecture-overview`, `decisions-log`, `decision-traceability`, `session-log`.

---

## Latest Formalized Request (iOS Navigation Graphs)

## Context

- Related docs/decisions:
  - `D-022` (Android subgraph rule),
  - current iOS app starts directly from auth screen via `ContentView`.
- Current constraints:
  - Keep iOS UI platform-specific and SwiftUI-based.
  - Preserve existing auth UI behavior.

## Goal

- What should be delivered:
  - Add scalable iOS navigation architecture with root graph container and feature graphs.
  - Route current auth flow through feature-owned graph view.

## Scope

- In scope:
  - iOS navigation container in app layer.
  - Auth graph and placeholder post-auth graph.
  - Documentation updates with explicit iOS navigation rule.
- Out of scope:
  - Full implementation of post-auth screens.

## Constraints

- Tech/business constraints:
  - Shared ViewModel stays navigation-framework-agnostic.
  - Cross-feature transitions handled in app/graph layer.
- Deadlines or milestones:
  - Complete in current iteration as structural foundation.

## Definition of Done

- Functional result:
  - `ContentView` renders root graph, auth is hosted in feature graph, and auth success can transition to main graph placeholder.
- Required tests:
  - `xcodebuild` iOS compile check (if environment allows).
- Required docs updates:
  - `engineering-standards`, `architecture-overview`, `decisions-log`, `decision-traceability`, `session-log`.

---

## Latest Formalized Request (iOS BridgeHandle Compile Fix)

## Context

- Related docs/decisions:
  - existing iOS bridge adapter base (`BridgeBackedObservableObject`),
  - `D-023` iOS navigation structure rollout.
- Current constraints:
  - Fix must not change shared Kotlin bridge contracts.
  - Keep iOS bridge lifecycle disposal behavior.

## Goal

- What should be delivered:
  - Remove compile-time dependency on unresolved `BridgeHandle` Swift type in iOS app target.

## Scope

- In scope:
  - `iosApp/iosApp/Common/Bridge/BridgeBackedObservableObject.swift` type handling update.
- Out of scope:
  - Kotlin bridge API redesign.
  - Navigation flow changes.

## Constraints

- Tech/business constraints:
  - Maintain safe disposal of active binding handle.
- Deadlines or milestones:
  - Immediate unblock for iOS run/build.

## Definition of Done

- Functional result:
  - iOS code no longer references unknown `BridgeHandle` type and compiles past this point.
- Required tests:
  - iOS compile check in Xcode environment.
- Required docs updates:
  - `session-log`.

---

## Latest Formalized Request (Ktor Auth Backend: Plan)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (`P0`: social auth + real backend completion)
  - `docs/context/engineering/tooling-stack.md` (`Ktor` backend confirmed)
  - `D-011` (auth feature module and provider abstraction)
- Current constraints:
  - Existing mobile flow currently builds provider launch URLs in client and uses placeholder `exchangeCode`.
  - Real provider token/session exchange must be moved to backend.

## Goal

- What should be delivered:
  - Produce implementation plan for Ktor auth backend for Telegram, VK, Google based on official provider APIs.

## Scope

- In scope:
  - Provider flow selection and backend endpoint design.
  - Validation/security/session issuance plan.
  - Required configuration/secrets list and rollout order.
- Out of scope:
  - Full coding implementation in this step.
  - Final provider credential setup in production consoles.

## Constraints

- Tech/business constraints:
  - Keep current product P0 priorities and existing auth UX entry points.
  - Follow `MVI`, quality rules, and secure token/session handling.
- Deadlines or milestones:
  - First deliver actionable technical plan; implementation starts after data/credentials confirmation.

## Definition of Done

- Functional result:
  - Concrete phased plan for Ktor auth backend accepted.
- Required tests:
  - Test plan includes happy/error/edge cases per provider and session issuance.
- Required docs updates:
  - `docs/context/handoff/task-request-template.md` (this entry),
  - and then governance docs in implementation phase.

---

## Latest Formalized Request (Telegram Auth Backend Implementation)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (`P0`: real auth completion via Ktor backend)
  - `D-011` (auth feature architecture), `D-025` (server module + Telegram-first rollout)
  - `docs/context/engineering/tooling-stack.md` (`PostgreSQL` confirmed)
- Current constraints:
  - Start with Telegram provider first.
  - Keep bot token out of repository and use environment variables.

## Goal

- What should be delivered:
  - Create `server` module in current repository.
  - Implement Ktor Telegram auth verify endpoint with server-side signature validation and DB-backed session issuance.

## Scope

- In scope:
  - `server` Gradle module setup.
  - Endpoint `POST /api/v1/auth/telegram/verify`.
  - Telegram payload verification (`hash`, `auth_date`).
  - PostgreSQL persistence for users/refresh tokens.
  - JWT access token + refresh token issuance.
- Out of scope:
  - Google/VK backend exchange implementation.
  - Full mobile callback wiring in this change.

## Constraints

- Tech/business constraints:
  - Use Ktor on backend and PostgreSQL for storage.
  - No hardcoded secrets in source control.
- Deadlines or milestones:
  - Complete Telegram backend slice first, then move to Google/VK.

## Definition of Done

- Functional result:
  - Telegram auth payload can be verified by backend and returns app session tokens.
- Required tests:
  - Backend verifier tests for happy/error/edge cases.
- Required docs updates:
  - `tooling-stack`, `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`.

---

## Latest Formalized Request (Mobile Telegram Callback + Data Layer)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (`P0`: real auth completion)
  - `D-025` (Telegram backend verify endpoint)
  - `D-026` (auth provider integrations in mobile data layer)
- Current constraints:
  - Keep `feature/auth` focused on domain/mvi contracts.
  - Do not place provider networking logic in feature/presentation layer.

## Goal

- What should be delivered:
  - Implement Telegram callback handling on Android/iOS and complete mobile auth through backend verify endpoint.
  - Add/organize mobile `data` layer for auth providers and backend API calls.

## Scope

- In scope:
  - `data/auth` module with provider implementations and Telegram backend API client.
  - Android deep-link callback wiring (`incomedy://auth/*`).
  - iOS callback wiring (`onOpenURL` + URL scheme setup).
  - Shared DI wiring to include data auth module.
- Out of scope:
  - Google/VK real backend exchange.
  - Production environment config and deployment.

## Constraints

- Tech/business constraints:
  - Maintain Clean dependency direction and existing shared MVI flow.
  - Telegram bot token stays server-side only.
- Deadlines or milestones:
  - Complete in current iteration as Telegram E2E mobile slice.

## Definition of Done

- Functional result:
  - Mobile app accepts Telegram callback URL and completes auth via backend verification.
- Required tests:
  - `:feature:auth:allTests`
  - `:composeApp:assembleDebug`
- Required docs updates:
  - `tooling-stack`, `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`.

---

## Latest Formalized Request (Server CI/CD Bootstrap)

## Context

- Related docs/decisions:
  - `D-025` (server module exists),
  - `D-027` (CI/CD approach),
  - `docs/context/engineering/quality-rules.md` (CI gates required).
- Current constraints:
  - Keep deployment simple and reproducible for early stage.
  - Use environment secrets for credentials and runtime config.

## Goal

- What should be delivered:
  - Add CI for server test/build.
  - Add CD for Docker image publish and staging deploy.

## Scope

- In scope:
  - GitHub Actions workflows for `server`.
  - Dockerfile and deploy compose manifest.
  - Documentation of required secrets/setup.
- Out of scope:
  - Production rollout approval flow.
  - Cloud-specific IaC beyond compose-based staging.

## Constraints

- Tech/business constraints:
  - No secrets in repository.
  - Keep rollback path simple (`docker compose up -d` with previous image tag).
- Deadlines or milestones:
  - Deliver in current iteration.

## Definition of Done

- Functional result:
  - PRs run server CI.
  - `main` pushes can publish image and deploy staging after secrets setup.
- Required tests:
  - `:server:build`.
- Required docs updates:
  - `tooling-stack`, `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`.

---

## Latest Formalized Request (PostgreSQL Docker Container for Deploy)

## Context

- Related docs/decisions:
  - `D-025` (server + PostgreSQL persistence)
  - `D-027` (server CI/CD baseline)
  - `D-028` (compose-based PostgreSQL bootstrap)
- Current constraints:
  - Need ready deployment stack with minimal manual DB setup.

## Goal

- What should be delivered:
  - Add ready-to-run PostgreSQL container in deploy compose stack for server.

## Scope

- In scope:
  - `deploy/server/docker-compose.yml` postgres service with persistence and healthcheck.
  - `deploy/server/.env.example` with DB/server variables.
  - Docs update for startup flow.
- Out of scope:
  - Managed database provisioning.
  - Production HA/backup automation.

## Constraints

- Tech/business constraints:
  - Keep secrets out of repository.
  - Keep deployment compatible with existing CD workflow.
- Deadlines or milestones:
  - Complete in current iteration.

## Definition of Done

- Functional result:
  - `docker compose up -d` can run both PostgreSQL and server with shared env.
- Required tests:
  - compose syntax validation in target environment (`docker compose config`).
- Required docs updates:
  - `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`, `server/README.md`.

---

## Latest Formalized Request (Domain Routing with Caddy)

## Context

- Related docs/decisions:
  - `D-027` (CI/CD baseline),
  - `D-028` (compose Postgres),
  - `D-030` (Caddy TLS/domain routing).
- Current constraints:
  - Domain `incomedy.ru` must be reachable externally over HTTPS.

## Goal

- What should be delivered:
  - Add Caddy reverse-proxy/TLS layer for domain-based access to server API.

## Scope

- In scope:
  - `deploy/server/Caddyfile` for `incomedy.ru`, `www.incomedy.ru`, `api.incomedy.ru`.
  - `deploy/server/docker-compose.yml` update with `caddy` service.
  - CD workflow update to copy Caddyfile to target host.
- Out of scope:
  - DNS registrar automation.
  - Advanced WAF/rate-limit edge policies.

## Constraints

- Tech/business constraints:
  - Keep server runtime in Docker Compose.
  - Avoid exposing app container port publicly.
- Deadlines or milestones:
  - Complete in current deployment iteration.

## Definition of Done

- Functional result:
  - Domain requests are served over HTTPS and proxied to backend app container.
- Required tests:
  - Live checks: `https://incomedy.ru/health`, `https://api.incomedy.ru/health`.
- Required docs updates:
  - `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`, `server/README.md`.

---

## Latest Formalized Request (Mobile Telegram Auth Completion)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (`P0`: real social auth completion)
  - `D-025` (server Telegram verify), `D-026` (auth in data layer), `D-031` (domain wiring)
- Current constraints:
  - Domain stack is live (`https://incomedy.ru`, `https://api.incomedy.ru`).
  - Mobile auth must stop using localhost callbacks/backend URLs.

## Goal

- What should be delivered:
  - Complete Telegram auth in mobile apps (Android/iOS) against deployed backend/domain.

## Scope

- In scope:
  - Telegram launch URL config (`origin`, `return_to`, bot id).
  - Mobile backend base URL for Telegram verify call.
  - Callback parsing compatibility for query/fragment formats.
- Out of scope:
  - VK/Google backend completion.
  - Additional auth UI redesign.

## Constraints

- Tech/business constraints:
  - Keep auth network/provider implementation in `data/auth`.
  - Keep feature/auth module domain+mvi only.
- Deadlines or milestones:
  - Complete in current iteration.

## Definition of Done

- Functional result:
  - Mobile Telegram callback is processed and exchanged to backend session using deployed API domain.
- Required tests:
  - `:data:auth:compileKotlinIosSimulatorArm64`
  - `:feature:auth:allTests`
  - `:composeApp:assembleDebug`
- Required docs updates:
  - `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`.

---

## Latest Formalized Request (Auth Logging Rule + Implementation)

## Context

- Related docs/decisions:
  - `D-031` (mobile telegram auth completion),
  - `D-032` (mandatory auth logging),
  - `docs/context/engineering/quality-rules.md` Observability section.
- Current constraints:
  - Need logs in both server and mobile for debugging callback/exchange problems.
  - Logs must not contain secrets.

## Goal

- What should be delivered:
  - Add structured auth logging in server and mobile app.
  - Document this as mandatory engineering rule for future work.

## Scope

- In scope:
  - backend request-id logging for auth endpoint,
  - server auth stage logs (received/success/failure),
  - mobile auth stage logs for provider click, callback received, parse/success/failure,
  - context docs update with observability rule.
- Out of scope:
  - external log aggregation stack (ELK, Loki, etc.)
  - full metrics/tracing platform setup.

## Constraints

- Tech/business constraints:
  - No token/secret leakage in logs.
  - Keep implementation minimal and compatible with current stack.
- Deadlines or milestones:
  - Complete in current auth stabilization iteration.

## Definition of Done

- Functional result:
  - Auth flow can be debugged end-to-end from logs on backend and mobile.
- Required tests:
  - `:server:build`
  - `:feature:auth:allTests`
  - `:composeApp:assembleDebug`
- Required docs updates:
  - `engineering-standards`, `quality-rules`, `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`.

---

## Latest Formalized Request (OpenAPI Contract for Current Server API)

## Context

- Related docs/decisions:
  - `docs/context/engineering/api-contracts/README.md`
  - `D-033` (OpenAPI maintenance rule)
- Current constraints:
  - Server already exposes health and Telegram auth endpoints.
  - Contract must match current DTOs/routes.

## Goal

- What should be delivered:
  - Add current backend API contract in OpenAPI format under `api-contracts/v1`.

## Scope

- In scope:
  - `GET /health`
  - `GET /auth/telegram/callback`
  - `POST /api/v1/auth/telegram/verify`
  - request/response schemas and status codes
- Out of scope:
  - Future VK/Google contracts.
  - Auto-generated swagger UI wiring in server runtime.

## Constraints

- Tech/business constraints:
  - Contract file must be human-readable and versioned in repo.
  - Keep it aligned with current implementation.
- Deadlines or milestones:
  - Complete in current iteration.

## Definition of Done

- Functional result:
  - OpenAPI file exists and documents current auth/health endpoints.
- Required tests:
  - Manual contract review against route and DTO definitions.
- Required docs updates:
  - `api-contracts/README.md`, `decisions-log`, `decision-traceability`, `session-log`, `task-request-template`.

---

## Latest Formalized Request (KMP Native VM Wrappers)

## Context

- Related docs/decisions:
  - Existing shared auth ViewModel in `commonMain`.
  - User-approved integration recipe for KMP VM usage on Android/iOS.
- Current constraints:
  - Preserve shared MVI contracts (`StateFlow` + `Event Flow`).
  - Add native platform wrappers for lifecycle-safe consumption.

## Goal

- What should be delivered:
  - Implement Android native ViewModel wrapper around shared auth VM.
  - Keep/align iOS ObservableObject wrapper pattern for shared VM consumption.
  - Register this pattern as explicit engineering rule.

## Scope

- In scope:
  - Android auth wrapper + UI wiring changes.
  - Context governance updates for new rule/decision.
- Out of scope:
  - Full iOS flow refactor beyond compile stabilization.

## Constraints

- Tech/business constraints:
  - Shared VM remains platform-agnostic and framework-free.
  - Lifecycle cleanup must be explicit in native wrappers.
- Deadlines or milestones:
  - Apply in current auth feature iteration.

## Definition of Done

- Functional result:
  - Android auth UI consumes native ViewModel wrapper and lifecycle-aware state collection.
  - Rule is documented and traceable in governance docs.
- Required tests:
  - `:composeApp:assembleDebug`.
- Required docs updates:
  - `engineering-standards`, `architecture-overview`, `decisions-log`, `decision-traceability`, `session-log`.

---

## Latest Formalized Request (iOS Wrapper Simplification)

## Context

- Related docs/decisions:
  - Ongoing `D-023`/`D-024` rollout for iOS graph and native wrapper patterns.
- Current constraints:
  - Keep lifecycle-safe cleanup.
  - Reduce accidental complexity in iOS layer.

## Goal

- What should be delivered:
  - Make iOS auth wrapper/navigation code easier to read and maintain while preserving behavior.

## Scope

- In scope:
  - Remove extra base wrapper class in iOS auth stack.
  - Simplify auth graph view structure for current single-screen flow.
- Out of scope:
  - New post-auth feature implementation.

## Constraints

- Tech/business constraints:
  - Keep explicit binding disposal.
  - Keep platform-specific iOS wrapper pattern.
- Deadlines or milestones:
  - Immediate cleanup in current session.

## Definition of Done

- Functional result:
  - iOS auth wrapper is self-contained and easier to reason about.
- Required tests:
  - iOS compile check in Xcode environment.
- Required docs updates:
  - `session-log`.

---

## Пример (на русском)

## Context

- Related docs/decisions:
  - `docs/context/product/backlog.md` (P0: social auth)
  - `D-015` (Koin), `D-012` (platform-specific UI)
- Current constraints:
  - не менять платежный модуль,
  - не трогать release-процесс,
  - сохранить текущий UX auth-кнопок.

## Goal

- What should be delivered:
  - подключить deep-link callback для auth на Android/iOS,
  - прокинуть callback в shared ViewModel,
  - показать понятный error/success status в UI.

## Scope

- In scope:
  - `composeApp` auth callback wiring,
  - `iosApp` auth callback wiring,
  - `shared/feature/auth` обработка callback.
- Out of scope:
  - новый дизайн экрана,
  - рефактор платежей,
  - внедрение feature flags.

## Constraints

- Tech/business constraints:
  - соблюдать `MVI`,
  - использовать `Koin` DI,
  - сохранить platform-specific UI split.
- Deadlines or milestones:
  - MVP-ready к концу спринта.

## Definition of Done

- Functional result:
  - успешный OAuth callback завершает авторизацию на обеих платформах.
- Required tests:
  - happy path, error path, edge case для callback.
- Required docs updates:
  - `session-log`, `decisions-log` (если появилось новое решение), `decision-traceability`.

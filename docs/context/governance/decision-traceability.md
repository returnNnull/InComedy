# Decision Traceability

Track how decisions map to implementation and tests.

## Template

| Decision ID | Requirement/Rule | Implementation Paths | Test Coverage | Status |
|---|---|---|---|---|
| D-XXX | ... | ... | ... | planned/in-progress/done |

---

## Entries

| Decision ID | Requirement/Rule | Implementation Paths | Test Coverage | Status |
|---|---|---|---|---|
| D-011 | Auth as dedicated feature module | `feature/auth/*`, `shared/src/commonMain/kotlin/com/bam/incomedy/shared/auth/*` | `feature/auth/src/commonTest/kotlin/com/bam/incomedy/feature/auth/mvi/AuthViewModelTest.kt` | done |
| D-012 | Platform-specific UI split | `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/ui/*`, `iosApp/iosApp/Features/Auth/*` | UI behavior validated via feature flow tests and manual platform checks | in-progress |
| D-014 | Base bridge primitives | `shared/src/commonMain/kotlin/com/bam/incomedy/shared/bridge/*` | Covered indirectly through auth bridge flow tests | in-progress |
| D-015 | Koin DI standard | `feature/auth/src/commonMain/kotlin/com/bam/incomedy/feature/auth/di/*`, `shared/src/commonMain/kotlin/com/bam/incomedy/shared/di/InComedyKoin.kt` | Build/test checks on auth/shared integration | in-progress |
| D-020 | Android navigation standard (`navigation-compose`) | `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `composeApp/src/main/kotlin/com/bam/incomedy/App.kt`, `composeApp/src/main/kotlin/com/bam/incomedy/navigation/*`, `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/navigation/*` | Android app build checks and smoke app launch | done |
| D-021 | Folder-based storage for split context parts | `docs/context/handoff/context-protocol.md`, `docs/context/handoff/chat-handoff-template.md`, `docs/context/governance/decisions-log.md`, `docs/context/governance/decisions-log/*`, `docs/context/governance/session-log.md`, `docs/context/governance/session-log/*`, `docs/context/README.md` | Manual context integrity check of links and file locations | done |
| D-022 | Android navigation subgraph architecture rule | `docs/context/engineering/engineering-standards.md`, `docs/context/engineering/architecture-overview.md`, `composeApp/src/main/kotlin/com/bam/incomedy/navigation/*`, `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/navigation/*` | `:composeApp:assembleDebug` | done |
| D-023 | iOS navigation graph architecture rule | `docs/context/engineering/engineering-standards.md`, `docs/context/engineering/architecture-overview.md`, `iosApp/iosApp/Navigation/*`, `iosApp/iosApp/Features/Auth/Navigation/*`, `iosApp/iosApp/Features/Main/Navigation/*`, `iosApp/iosApp/ContentView.swift`, `iosApp/iosApp/Features/Auth/UI/AuthRootView.swift` | iOS build verification (`xcodebuild`) | in-progress |
| D-024 | Native wrappers for KMP ViewModels | `docs/context/engineering/engineering-standards.md`, `docs/context/engineering/architecture-overview.md`, `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/viewmodel/AuthAndroidViewModel.kt`, `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/ui/AuthScreen.kt`, `composeApp/src/main/kotlin/com/bam/incomedy/App.kt`, `iosApp/iosApp/Features/Auth/ViewModel/AuthScreenModel.swift` | `:composeApp:assembleDebug`, iOS build verification (`xcodebuild`) | in-progress |
| D-025 | Ktor `server` module + Telegram backend auth verification + PostgreSQL persistence | `settings.gradle.kts`, `server/build.gradle.kts`, `server/src/main/kotlin/com/bam/incomedy/server/*`, `server/src/main/resources/application.conf`, `data/auth/src/commonMain/kotlin/com/bam/incomedy/data/auth/backend/TelegramBackendApi.kt` | `:server:test` (`TelegramAuthVerifierTest`), `:feature:auth:allTests` | done |
| D-026 | Auth provider integrations live in mobile data layer; feature module stays domain/mvi only | `data/auth/*`, `feature/auth/src/commonMain/kotlin/com/bam/incomedy/feature/auth/domain/*`, `feature/auth/src/commonMain/kotlin/com/bam/incomedy/feature/auth/mvi/*`, `shared/src/commonMain/kotlin/com/bam/incomedy/shared/di/InComedyKoin.kt` | `:feature:auth:allTests`, `:composeApp:assembleDebug` | done |
| D-027 | Server CI/CD via GitHub Actions + Docker + staging compose deploy | `.github/workflows/ci-server.yml`, `.github/workflows/cd-server.yml`, `server/Dockerfile`, `deploy/server/docker-compose.yml`, `server/README.md` | `:server:build` | done |
| D-028 | Compose-based PostgreSQL dependency for staging/bootstrap | `deploy/server/docker-compose.yml`, `deploy/server/.env.example`, `server/README.md` | `docker compose config` (manual validation on target env) | done |
| D-029 | Staging deploy uses server-local env file instead of secret-injected dotenv | `.github/workflows/cd-server.yml`, `server/README.md` | Workflow validation on next `CD Server` run | done |
| D-030 | Public domain routing and TLS via Caddy in deploy stack | `deploy/server/docker-compose.yml`, `deploy/server/Caddyfile`, `.github/workflows/cd-server.yml`, `server/README.md` | Live domain health checks after deploy (`https://incomedy.ru/health`, `https://api.incomedy.ru/health`) | done |
| D-031 | Mobile Telegram auth is wired to deployed domain endpoints and robust callback parsing | `data/auth/src/commonMain/kotlin/com/bam/incomedy/data/auth/di/AuthDataModule.kt`, `data/auth/src/commonMain/kotlin/com/bam/incomedy/data/auth/providers/TelegramAuthProvider.kt`, `data/auth/src/androidMain/kotlin/com/bam/incomedy/data/auth/backend/AuthBackendConfig.android.kt`, `data/auth/src/iosMain/kotlin/com/bam/incomedy/data/auth/backend/AuthBackendConfig.ios.kt` | `:data:auth:compileKotlinIosSimulatorArm64`, `:feature:auth:allTests`, `:composeApp:assembleDebug` | done |
| D-032 | Structured auth-flow logging is mandatory across backend and mobile | `server/src/main/kotlin/com/bam/incomedy/server/Application.kt`, `server/src/main/kotlin/com/bam/incomedy/server/auth/telegram/*`, `feature/auth/src/commonMain/kotlin/com/bam/incomedy/feature/auth/mvi/*`, `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/viewmodel/AuthAndroidViewModel.kt`, `iosApp/iosApp/Features/Auth/ViewModel/AuthScreenModel.swift`, `docs/context/engineering/*` | `:server:build`, `:feature:auth:allTests`, `:composeApp:assembleDebug` | done |
| D-033 | OpenAPI contract is mandatory for current auth API surface | `docs/context/engineering/api-contracts/README.md`, `docs/context/engineering/api-contracts/v1/openapi.yaml` | Contract reviewed against server routes and DTO models | done |

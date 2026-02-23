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

# Session Log Part 02

## 2026-02-23 12:30

- Context: Need iOS UI structure by feature files instead of large `ContentView`.
- Changes: Split iOS auth UI into separate files (`AuthRootView`, `AuthProviderLinkButton`, `AuthProvider`) under `iosApp/iosApp/Features/Auth/*`; `ContentView` now acts as root container only.
- Decisions: Keep SwiftUI screens modular with feature subfolders, similar to Android package organization.
- Next: Continue iOS auth flow wiring with callback handling in dedicated feature files.

## 2026-02-23 12:41

- Context: Need one scalable way to use many shared ViewModels in SwiftUI.
- Changes: Added shared `AuthFeatureBridge` (state/effect observation + lifecycle) and iOS `AuthScreenModel` ObservableObject adapter; wired SwiftUI auth screen to shared ViewModel through this bridge.
- Decisions: Accepted bridge + adapter strategy as project standard in `D-013`.
- Next: Reuse this pattern for next features and optionally evaluate KMP-NativeCoroutines/KMP-ObservableViewModel for reducing adapter boilerplate.

## 2026-02-23 12:44

- Context: Need reusable base pattern instead of per-feature bridge boilerplate.
- Changes: Added shared bridge primitives (`BaseFeatureBridge`, `BridgeHandle`, `CompositeBridgeHandle`) and iOS base class (`BridgeBackedObservableObject`); refactored `AuthFeatureBridge` and `AuthScreenModel` to use them.
- Decisions: Accepted base bridge policy in `D-014`.
- Next: Apply same base pattern when adding next feature bridge.

## 2026-02-23 12:54

- Context: Need a single DI standard for scaling feature modules.
- Changes: Added Koin dependencies, introduced auth feature Koin module, added shared `InComedyKoin` entry point, and migrated auth wiring from manual factory to DI resolution.
- Decisions: Accepted Koin as DI standard in `D-015`.
- Next: Add Koin modules for next features and wire backend/service modules using the same container conventions.

## 2026-02-23 13:07

- Context: Need stronger context integrity for long-running work.
- Changes: Added task request template, context integrity checklist, standards rollout plan, and decision traceability matrix; linked them in context index/protocol.
- Decisions: Accepted context-operability docs in `D-016`.
- Next: Use task template for major requests and update traceability on significant implementation changes.

## 2026-02-23 13:11

- Context: Need clearer usage examples and reminder workflow for context updates.
- Changes: Added Russian example to task request template and backlog update examples; added explicit assistant reminder rule for refreshing task input/backlog before major tasks.
- Decisions: Accepted reminder policy in `D-017`.
- Next: Apply reminder rule on each major request and keep examples aligned with real task patterns.

## 2026-02-23 13:13

- Context: Need low-friction workflow where user provides tasks in free form.
- Changes: Updated context responsibilities/protocol to make assistant responsible for formalizing free-form tasks into template/docs.
- Decisions: Accepted default free-form intake policy in `D-018`.
- Next: Continue with free-form task intake and keep context artifacts synchronized automatically.

## 2026-02-23 13:15

- Context: Need guaranteed consistency of cross-chat transfer template.
- Changes: Added explicit auto-sync rule between `context-protocol.md` and `chat-handoff-template.md`.
- Decisions: Accepted template auto-sync policy in `D-019`.
- Next: On every protocol change, update handoff template in the same commit.

## 2026-02-23 13:18

- Context: Need base Android navigation layer before adding next app screens.
- Changes: Formalized free-form task in `handoff/task-request-template.md`; added `navigation-compose` to tooling stack and Android app dependencies; introduced minimal `NavHost` with auth route in `composeApp`.
- Decisions: Accepted Android navigation standard in `D-020`.
- Next: Register the next screen route in the same navigation graph when implementing post-auth flow.

## 2026-02-23 13:20

- Context: Session log crossed context-size threshold.
- Changes: Split `session-log.md` into index + parts (`session-log/session-log-part-01.md`, `session-log/session-log-part-02.md`) per context protocol.
- Decisions: Continue following `D-009` split policy for all oversized context docs.
- Next: Keep appending new entries to `session-log/session-log-part-02.md` until next split is required.

## 2026-02-23 13:23

- Context: Need cleaner folder structure for split context files.
- Changes: Moved session-log parts into dedicated folder `governance/session-log/`; updated links/rules in context protocol, handoff template, session-log index, and context README.
- Decisions: Accepted folder-based split storage policy in `D-021`.
- Next: Apply the same folder-based split format to any future oversized context document.

## 2026-02-23 13:25

- Context: Decisions register crossed context-size threshold.
- Changes: Split `decisions-log.md` into index + parts under `governance/decisions-log/`; updated context references to index/part format.
- Decisions: Continued applying `D-009` and `D-021` for oversized governance docs.
- Next: Append future decisions to `governance/decisions-log/decisions-log-part-02.md` until next split is required.

## 2026-02-23 13:27

- Context: Need consistent append instructions after index+part migration.
- Changes: Updated `context-protocol.md` and `README.md` to require appending new decision/session entries into latest part files referenced by index docs.
- Decisions: Operationalized split workflow from `D-021` across governance update rules.
- Next: Follow index->latest-part append flow for every new governance update.

## 2026-02-23 13:30

- Context: Navigation will scale and needs modular subgraph structure.
- Changes: Added root navigation container (`AppNavHost`), introduced auth subgraph (`AuthGraph`, `AuthDestinations`), and rewired `App.kt` to render through root host.
- Decisions: Implement current Android navigation using nested feature graph pattern under existing `D-020`.
- Next: Add next feature graph (post-auth/main) and move cross-feature transitions to graph callbacks.

## 2026-02-23 13:34

- Context: Need to lock subgraph-based navigation as explicit project rule and finalize current workspace changes.
- Changes: Added subgraph architecture rule in engineering context docs, registered decision `D-022`, updated traceability, and prepared full workspace commit including script migration changes.
- Decisions: Android navigation graph decomposition is now mandatory for scalable feature growth (`D-022`).
- Next: Build next `main` feature graph and route auth success transition through graph-level callback.

## 2026-02-23 13:39

- Context: Need iOS-side scalable navigation structure similar to Android subgraphs.
- Changes: Added iOS root graph container/navigation state (`Navigation/*`), introduced feature-owned auth graph and placeholder main graph, rewired `ContentView` through app graph, and connected auth success callback to graph transition.
- Decisions: Accepted iOS navigation graph standard in `D-023`.
- Next: Replace placeholder main graph with real post-auth feature graphs and deep-link entry routing.

## 2026-02-23 13:43

- Context: iOS run/build blocked by Swift compile error `Cannot find type 'BridgeHandle' in scope`.
- Changes: Refactored `BridgeBackedObservableObject` to avoid hard compile-time dependency on exported Kotlin handle type and dispose active handle via runtime selector (`dispose`) on bridged NSObject.
- Decisions: Keep current Kotlin bridge API unchanged; compatibility fix applied in iOS adapter layer.
- Next: Re-run iOS build in full Xcode environment and confirm no further bridge-symbol export issues.

## 2026-02-23 13:45

- Context: iOS compile still failed on unresolved `AuthFeatureBridge` symbol.
- Changes: Updated `AuthScreenModel` to use stable exported ObjC names from Shared framework (`SharedAuthFeatureBridge`, `SharedInComedyKoin`) and explicit bridge initialization via `SharedInComedyKoin.shared.getAuthViewModel()`.
- Decisions: iOS bridge adapter layer should reference Shared exported symbols in their stable generated form when Swift name mapping is inconsistent.
- Next: Rebuild iOS target and verify no further unresolved Shared symbol errors.

## 2026-02-23 13:47

- Context: ObjC-prefixed symbol names were unresolved in Swift target.
- Changes: Switched `AuthScreenModel` back to Swift-visible KMP names (`AuthFeatureBridge`, `InComedyKoin`) while keeping explicit `init(viewModel:)` construction.
- Decisions: Prefer `swift_name` aliases for Shared types in Swift files; avoid ObjC-prefixed names unless Swift aliases are unavailable.
- Next: Re-run iOS build and continue with next unresolved symbol if any.

## 2026-02-23 13:49

- Context: Swift compile error from actor isolation in default initializer argument.
- Changes: Removed actor-isolated call from `init` default parameter in `AuthScreenModel`; bridge default is now resolved inside `@MainActor` initializer body.
- Decisions: Avoid main-actor static calls in default argument expressions.
- Next: Continue incremental compile-error cleanup until iOS target compiles.

## 2026-02-23 14:06

- Context: Need to align auth feature with recommended KMP ViewModel integration recipe.
- Changes: Added Android native wrapper (`AuthAndroidViewModel`), switched Android app/auth navigation to consume wrapper instead of shared VM directly, and migrated state collection to `collectAsStateWithLifecycle()`. Documented platform-wrapper rule and registered decision `D-024`.
- Decisions: KMP ViewModels must be consumed through native platform wrappers (`D-024`).
- Next: Stabilize iOS build/export issues and finalize iOS wrapper flow verification under the same rule.

## 2026-02-23 14:22

- Context: iOS implementation became harder to follow due to extra adapter layers.
- Changes: Simplified iOS auth wrapper by removing `BridgeBackedObservableObject`, inlining binding/dispose logic in `AuthScreenModel`, and simplifying auth graph navigation (`AuthGraphView`) to a direct stack without placeholder route type.
- Decisions: For current scope, prefer the simplest explicit iOS wrapper implementation that preserves lifecycle cleanup and readability.
- Next: Validate iOS compile in Xcode and keep this simpler style for next iOS feature wrappers.

## 2026-02-23 14:33

- Context: Swift compiler still reported actor-isolation issue in `deinit` for `AuthScreenModel`.
- Changes: Removed `@MainActor` isolation from `AuthScreenModel` class; main-thread UI updates remain explicitly wrapped in `Task { @MainActor ... }`.
- Decisions: For this wrapper, avoid class-level actor isolation to keep synchronous cleanup in `deinit` compile-safe.
- Next: Rebuild iOS target and continue remaining compile fixes, if any.

## 2026-02-23 18:31

- Context: Need to start real backend auth completion from P0 scope with provider-side verification and persistent sessions.
- Changes: Added new `server` module in repo with Ktor runtime, PostgreSQL datasource/schema bootstrap, Telegram auth verify endpoint (`POST /api/v1/auth/telegram/verify`), Telegram hash/auth_date verification service, user upsert + refresh token storage, JWT access token issuance, `.env` template, and verifier tests.
- Decisions: Accepted backend bootstrap and Telegram-first auth rollout in `D-025`; confirmed `PostgreSQL` as database stack choice.
- Next: Wire mobile deep-link callback flow to backend Telegram verify endpoint and then implement Google/VK backend exchange endpoints in the same server module.

## 2026-02-23 18:49

- Context: Need to complete mobile auth side for Telegram and keep provider/networking logic in data layer.
- Changes: Added new `data/auth` KMP module with provider implementations and Telegram backend API client; moved provider implementations out of `feature/auth`; wired shared DI to include `authDataModule`; added Android deep-link intent filters and callback handling in `MainActivity`; added iOS URL scheme + callback handling via `onOpenURL`; updated shared bridge with callback-url completion helper.
- Decisions: Accepted data-layer placement rule for auth integrations in `D-026`.
- Next: Validate Telegram auth end-to-end against running local server and then implement Google/VK backend exchange in `server` + `data/auth`.

## 2026-02-23 19:24

- Context: Need delivery automation for new server module.
- Changes: Added server CI workflow (`ci-server.yml`) with Gradle test/build checks, CD workflow (`cd-server.yml`) with GHCR image publishing and staging deploy over SSH + Docker Compose, added `server/Dockerfile`, deploy compose manifest (`deploy/server/docker-compose.yml`), and CI/CD setup docs in `server/README.md`.
- Decisions: Accepted server CI/CD rollout approach in `D-027`.
- Next: Configure staging GitHub environment secrets and perform first deployment from `main`.

## 2026-02-23 19:35

- Context: Need ready-to-run PostgreSQL container for server deployment.
- Changes: Extended `deploy/server/docker-compose.yml` with `postgres` service (healthcheck + persistent volume) and server `depends_on` DB wiring; added `deploy/server/.env.example`; updated `server/README.md` with compose startup instructions.
- Decisions: Accepted compose-based PostgreSQL bootstrap strategy in `D-028`.
- Next: Fill `deploy/server/.env` (or `STAGING_SERVER_DOTENV`) and run first compose deployment.

## 2026-02-23 21:38

- Context: CD deploy repeatedly failed due to malformed multiline `STAGING_SERVER_DOTENV` secret overwriting runtime env.
- Changes: Updated CD workflow to stop using dotenv secret injection and require existing `/opt/incomedy/server/.env` on host; updated server README staging secret list accordingly.
- Decisions: Accepted server-local env strategy in `D-029`.
- Next: Re-run `CD Server` with updated workflow and verify end-to-end deployment.

## 2026-02-23 22:10

- Context: Need domain-based HTTPS access for public API endpoints.
- Changes: Added `deploy/server/Caddyfile`, extended deploy compose with `caddy` service (80/443), switched app container to internal `expose` only, and updated CD workflow to copy Caddyfile to server before compose up.
- Decisions: Accepted Caddy-based TLS/domain routing strategy in `D-030`.
- Next: Re-run `CD Server` and verify `https://incomedy.ru/health` and `https://api.incomedy.ru/health`.

## 2026-02-24 13:06

- Context: Need to finish Telegram authorization on mobile against deployed backend/domain.
- Changes: Updated mobile auth data layer Telegram config to use real bot id and domain origin (`https://incomedy.ru`), switched backend base URL on Android/iOS to `https://api.incomedy.ru`, added `return_to` in Telegram launch URL, and improved Telegram callback parser to read params from both query and fragment.
- Decisions: Accepted mobile Telegram domain wiring rule in `D-031`.
- Next: Validate Telegram auth flow on physical Android/iOS device with real callback payload from Telegram and confirm token issuance end-to-end.

## 2026-02-24 13:32

- Context: Need mandatory observability for auth flow debugging across server and mobile.
- Changes: Added backend auth logs with request-id/call-id, added structured auth-stage logs in shared/auth Android/iOS entry points, enabled backend `CallId` plugin with `X-Request-ID`, and documented mandatory auth logging rules in engineering and quality context docs.
- Decisions: Accepted cross-layer auth logging rule in `D-032`.
- Next: Deploy updated server build and validate logs on real Telegram login attempt (`requestId` + auth stage sequence).

## 2026-02-24 13:36

- Context: Need explicit API contract documentation for current backend endpoints.
- Changes: Added OpenAPI contract `docs/context/engineering/api-contracts/v1/openapi.yaml` for health and Telegram auth endpoints; updated API contracts README to reference active contract file.
- Decisions: Accepted OpenAPI contract maintenance rule in `D-033`.
- Next: Keep OpenAPI file updated in the same change for any auth/backend API behavior update.

## 2026-02-24 13:46

- Context: CD build-and-push failed in Docker build stage when Gradle was executed inside buildx container.
- Changes: Switched to CI prebuild strategy (`:server:installDist`) before docker build; converted server Dockerfile to runtime-only image that copies prebuilt distribution; updated `.dockerignore` and server README accordingly.
- Decisions: Accepted prebuilt-distribution docker strategy in `D-034`.
- Next: Re-run `CD Server` workflow and verify successful image build/push and deploy.

## 2026-02-24 15:05

- Context: Mobile app must skip Telegram auth when server session is already valid.
- Changes: Added backend endpoint `GET /api/v1/auth/session/me` with JWT validation + user lookup; added shared restore intent/bridge contract; implemented Android/iOS startup token restore against backend; added Android `main` graph and auth/main auto-routing by auth state; limited current auth UI to Telegram entry.
- Decisions: Accepted startup session-restore behavior as explicit rule in `D-035`.
- Next: Deploy updated server image, verify `/api/v1/auth/session/me` on staging/prod, and run end-to-end checks on Android/iOS deep-link + cold start restore.

## 2026-02-24 15:24

- Context: Current mobile token persistence used plain local storage and required hardening.
- Changes: Android auth token storage migrated to `EncryptedSharedPreferences` with Keystore-backed key and one-time migration from legacy plain prefs; iOS auth token storage migrated to Keychain with one-time migration from legacy `UserDefaults`; docs updated with secure storage rule.
- Decisions: Accepted secure token storage policy in `D-036`.
- Next: Validate auth restore flow on Android emulator/device and iOS simulator/device after migration from old install.

## 2026-02-24 15:33

- Context: Need explicit permanent process for vulnerability communication and remediation tracking.
- Changes: Added mandatory vulnerability reporting/tracking policy, updated context checklist, and formalized risk-log security template with first tracked auth storage vulnerability record.
- Decisions: Accepted vulnerability communication + tracking rule in `D-037`.
- Next: Keep `risk-log` updated on each discovered vulnerability until remediation is closed.

## 2026-02-24 15:52

- Context: Detected architectural regression: platform auth wrappers contained direct HTTP calls for session restore, and provider list was accidentally reduced to Telegram-only in UI.
- Changes: Moved session token validation to shared domain+data flow (`SessionValidationService` + backend API adapter), added auth effect for invalid stored token cleanup, restored VK/Telegram/Google provider buttons in Android/iOS UI, and removed hardcoded backend URL usage from platform wrappers.
- Decisions: No new architecture decision; implementation aligned back to existing Clean rules.
- Next: Run end-to-end auth checks for Telegram and verify placeholder behavior for VK/Google until backend exchange endpoints are implemented.

## 2026-02-24 16:02

- Context: Telegram auth sometimes returned to domain root (`/`) where plain text response prevented deep-link handoff back to app.
- Changes: Reworked callback HTML to conditionally deep-link only when Telegram payload exists, and served the same bridge HTML on `/` as fallback.
- Decisions: No new decision; bugfix within existing Telegram callback flow.
- Next: Deploy updated server container and retest Telegram login from emulator/device.

## 2026-02-24 16:09

- Context: Telegram callback payload arrived without `state`, causing `Invalid auth state for TELEGRAM` despite successful deep-link return.
- Changes: Updated shared auth state validation rule for Telegram to accept callbacks without `state` and rely on Telegram signature verification in backend.
- Decisions: No new decision; compatibility fix for Telegram callback behavior.
- Next: Deploy updated client build and re-run Telegram auth flow end-to-end.

## 2026-02-24 16:15

- Context: Android app crashed during Telegram auth completion with `SecurityException: missing INTERNET permission`.
- Changes: Added `android.permission.INTERNET` to `composeApp` Android manifest.
- Decisions: No new decision; required Android networking permission fix.
- Next: Install updated Android build and re-test Telegram auth flow.

## 2026-02-24 16:20

- Context: Runtime error `Unable to resolve host api.incomedy.ru` blocked auth requests from mobile.
- Changes: Switched mobile backend base URL to `https://incomedy.ru` for Android/iOS (`AuthBackendConfig`) to match active DNS setup.
- Decisions: No new decision; environment compatibility fix.
- Next: Re-run Telegram auth flow and add `api` DNS record later if dedicated API subdomain is required.

## 2026-02-24 16:28

- Context: Telegram auth completion still surfaced raw JSON parsing errors in UI/logs when backend response included extra fields (`refresh_token`).
- Changes: Enabled tolerant JSON parsing (`ignoreUnknownKeys`) for data-layer HTTP client; added sanitized auth failure reasons in `AUTH_FLOW` logs to avoid leaking sensitive payload fragments into adb.
- Decisions: No new decision; robustness and observability hardening.
- Next: Install latest mobile build and verify Telegram login UI now shows server message without parser stacktrace.

## 2026-02-24 16:35

- Context: Startup session restore still fell back to auth screen in some restarts due aggressive token invalidation on any validation failure.
- Changes: Added session validation failure classification (`UNAUTHORIZED/NETWORK/UNKNOWN`), invalidating stored token only on `401`; retained token for transient network errors. Added Android fallback persistence to legacy prefs only when secure storage is unavailable.
- Decisions: No new decision; reliability fix within existing startup restore behavior.
- Next: Verify restart flow after successful login with online/offline toggles to confirm token survives transient network failures.

## 2026-02-24 16:47

- Context: Need explicit app-level logout that revokes backend session, not only local token removal.
- Changes: Added backend `POST /api/v1/auth/logout` with per-user session revocation timestamp and validation in `/api/v1/auth/session/me`; wired client-side sign-out intent/service and added logout button in Android main screen; updated OpenAPI contract.
- Decisions: No new decision; implementation follows existing auth/session architecture.
- Next: Deploy server update, rebuild mobile app, and validate flow: login -> main -> logout -> restart stays on auth.

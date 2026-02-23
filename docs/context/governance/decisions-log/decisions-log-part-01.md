# Decisions Log

Format:
- ID
- Date
- Status
- Decision
- Rationale
- Consequences

---

## D-001

- Date: 2026-02-23
- Status: accepted
- Decision: Use chat-first event model (event card + dedicated chat) as core UX.
- Rationale: Communication and operational updates are central to standup events.
- Consequences: Messaging, moderation, and notification systems become foundational platform capabilities.

## D-002

- Date: 2026-02-23
- Status: accepted
- Decision: Start with three explicit product roles: Audience, Comedian, Organizer.
- Rationale: Matches real-world workflows and simplifies permission boundaries.
- Consequences: Role-based access model is required from early architecture stages.

## D-003

- Date: 2026-02-23
- Status: accepted
- Decision: Prioritize MVP around discovery + ticketing + applications + chat + donations.
- Rationale: Covers full event lifecycle with monetization and validates main value proposition quickly.
- Consequences: Advanced analytics/recommendations are deferred until post-MVP.

## D-004

- Date: 2026-02-23
- Status: accepted
- Decision: Use `Clean` architecture style as a mandatory engineering baseline.
- Rationale: Improves maintainability, separation of concerns, and long-term scalability of feature development.
- Consequences: New modules must enforce layer boundaries and dependency direction rules.

## D-005

- Date: 2026-02-23
- Status: accepted
- Decision: Build all `ViewModel` logic using `MVI`.
- Rationale: Predictable state transitions are important for chat-first UX and cross-feature consistency.
- Consequences: Each feature must define clear `Intent`, `State`, and `Effect` contracts.

## D-006

- Date: 2026-02-23
- Status: accepted
- Decision: Treat testability and automated tests as release criteria for every feature.
- Rationale: Reduces regression risk and supports fast iteration on multi-role product flows.
- Consequences: Feature work is incomplete without related tests (domain, ViewModel, and data integration where needed).

## D-007

- Date: 2026-02-23
- Status: accepted
- Decision: Adopt formal quality rules covering DoD, error handling, API contract/versioning, CI gates, test minimums, dependency direction, observability, and security/privacy.
- Rationale: A stable quality framework is required to keep delivery predictable as product and team complexity grows.
- Consequences: `docs/context/engineering/quality-rules.md` is now mandatory reference for implementation and review.

## D-008

- Date: 2026-02-23
- Status: accepted
- Decision: Adopt a unified context transfer protocol for cross-chat continuity.
- Rationale: Long-running product work needs deterministic handoff to avoid context loss.
- Consequences: `docs/context/handoff/context-protocol.md` is mandatory onboarding reference for any new chat/session.

## D-009

- Date: 2026-02-23
- Status: accepted
- Decision: Enforce context document splitting when files become too large for efficient chat synchronization.
- Rationale: Large single files increase context load and reduce handoff reliability between chats.
- Consequences: Use index + part files (`name.md`, `name-part-XX.md`) based on the split rule in `docs/context/handoff/context-protocol.md`.

## D-010

- Date: 2026-02-23
- Status: accepted
- Decision: Organize context documents by domains (`product`, `engineering`, `governance`, `handoff`) instead of a flat folder.
- Rationale: Prevents document sprawl, improves scan speed, and makes handoff context predictable.
- Consequences: New context docs must be added to the correct subfolder and indexed in `docs/context/README.md`.

## D-011

- Date: 2026-02-23
- Status: accepted
- Decision: Implement auth as a dedicated `feature/auth` module with MVI ViewModel and provider abstractions for VK, Telegram, and Google.
- Rationale: Keeps social auth isolated, testable, and aligned with Clean architecture rules.
- Consequences: OAuth launch URL generation is implemented in the client module; production token exchange must be delegated to backend Ktor endpoints in the next increment.

## D-012

- Date: 2026-02-23
- Status: accepted
- Decision: Keep UI platform-specific: Android screens on Compose, iOS screens on SwiftUI; shared modules contain only platform-agnostic logic.
- Rationale: Preserves native UX quality per platform and prevents coupling shared feature modules to UI frameworks.
- Consequences: Compose UI was moved out of `feature/auth` into Android app layer; iOS auth screen is implemented separately in SwiftUI.

## D-013

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize iOS integration of shared ViewModels via a shared bridge + Swift ObservableObject adapter pattern.
- Rationale: Provides one scalable and repeatable integration strategy for many ViewModels without duplicating business logic.
- Consequences: Added `AuthFeatureBridge` in shared module and `AuthScreenModel` adapter in iOS feature layer as reference implementation.

## D-014

- Date: 2026-02-23
- Status: accepted
- Decision: Introduce reusable base bridge primitives (`BaseFeatureBridge`, `BridgeHandle`, `CompositeBridgeHandle`) and require feature bridges to build on them.
- Rationale: Reduces boilerplate and enforces consistent lifecycle/scope handling across many ViewModels.
- Consequences: `AuthFeatureBridge` migrated to base bridge pattern; iOS gained reusable `BridgeBackedObservableObject` for binding lifecycle management.

## D-015

- Date: 2026-02-23
- Status: accepted
- Decision: Standardize dependency injection on `Koin` across shared/mobile layers.
- Rationale: Koin provides a practical KMP-first DI model and keeps wiring consistent between shared logic and platform apps.
- Consequences: Auth feature wiring moved from manual object factory to Koin modules and shared `InComedyKoin` entry point.


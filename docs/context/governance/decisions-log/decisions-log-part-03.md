# Decisions Log Part 03

## D-045

- Date: 2026-03-06
- Status: accepted
- Decision: Model product roles as capabilities on a single account rather than separate mutually exclusive accounts.
- Rationale: Real users can be audience, comedian, organizer, or invited event staff at the same time; separate accounts would fragment identity, purchases, and permissions.
- Consequences: User identity must support multiple linked roles and context switching; organizer staff permissions become workspace-scoped rather than standalone account types.

## D-046

- Date: 2026-03-06
- Status: accepted
- Decision: Prioritize organizer operations, venue-aware ticketing, lineup management, live stage status, and check-in for MVP; defer full public audience chat to later phases.
- Rationale: Operational workflows create launch value and revenue sooner, while public chat multiplies moderation and abuse complexity before core event flows are stable.
- Consequences: MVP communication layer is reduced to announcements/event feed + push notifications; broad public chat becomes P1/P2.

## D-047

- Date: 2026-03-06
- Status: accepted
- Decision: Treat hall layouts and ticket inventory as first-class domain models with reusable venue templates and event-specific hall snapshots.
- Rationale: Standup event monetization depends on accurate inventory control; layout editing cannot remain a loose UI concern or free-form text configuration.
- Consequences: Venue/hall/ticketing become explicit bounded contexts; seat holds, order issuance, and hall versioning require dedicated domain invariants and storage models.

## D-048

- Date: 2026-03-06
- Status: accepted
- Decision: Include Sign in with Apple in iOS scope and design donations as a compliance-constrained flow separated from ticket checkout.
- Rationale: Current App Store policy makes third-party-login-only iOS release risky, and donation flows have stricter interpretation than real-world ticket purchases.
- Consequences: Auth scope expands beyond Telegram/VK/Google; donation architecture must support pass-through or web-checkout fallback and verified payout profiles.

## D-049

- Date: 2026-03-10
- Status: accepted
- Decision: Keep Telegram as the only implemented auth provider for the next delivery slice, but build identity/session/roles/workspace foundations on a provider-agnostic internal `User + AuthIdentity` model so VK/Google/Apple can be added later without domain refactor.
- Rationale: The current repository already has the deepest Telegram integration, while the highest-value next step is unblocking role-based product domains rather than multiplying partial provider implementations. Provider-specific identifiers leaking into profile, RBAC, or workspace domains would create expensive rework when additional providers are added.
- Consequences: Telegram remains an interim entry path only; internal user/session models, profile state, role switching, and organizer workspace membership must be detached from `telegram_id`. Future VK/Google/Apple work should plug into the same internal identity model instead of introducing parallel user representations.

## D-050

- Date: 2026-03-10
- Status: accepted
- Decision: Move backend database evolution to versioned SQL migrations executed during startup, and stop relying on ad-hoc schema bootstrap DDL embedded in application code as the primary rollout mechanism.
- Rationale: The project has already accumulated multiple schema changes across auth hardening and identity/workspace work. Without versioned migrations, deploy safety depends on mutable startup code, there is no persistent schema history, and existing databases cannot be reasoned about or upgraded confidently across environments.
- Consequences: Backend stack now includes a migration tool and `db/migration` scripts. Schema changes must be forward-only and recorded as versioned migrations with rollout compatibility for both clean databases and already initialized deployments. Application startup may trigger migration execution, but should no longer contain the schema definition itself.

## D-051

- Date: 2026-03-12
- Status: accepted
- Decision: Require repository code comments at class/object/interface, method/function, and field/property level, and enforce comment backfill for touched classes in the same change.
- Rationale: The project is moving from foundation slices to broader product surfaces, and handoff/debugging cost rises quickly when classes and public behavior are under-documented. A mandatory comment baseline improves maintainability across shared, Android, iOS, and server code.
- Consequences: `engineering-standards.md` and `quality-rules.md` now treat code comments as a required engineering rule. When editing an existing class, its class/method/property comments must be brought up to the rule in the same change instead of leaving mixed documentation quality behind.

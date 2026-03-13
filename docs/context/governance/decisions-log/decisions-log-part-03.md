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

## D-052

- Date: 2026-03-13
- Status: accepted
- Decision: Expose recent backend diagnostic events through an operator-only, sanitized retrieval mechanism keyed by request correlation ids instead of relying on raw server logs as the only live-debugging path.
- Rationale: Current mobile/server troubleshooting depends on separately reading device logs and server logs without a safe retrieval channel. That slows down auth and production incident analysis, and raw log access is a poor default because it increases secret/PII exposure risk.
- Consequences: Backend must retain a bounded set of sanitized diagnostic events, protect retrieval with a dedicated operator token, and keep request-id correlation visible in client-side failures. New backend routes should emit safe diagnostic stages instead of assuming SSH/log tailing will remain the primary investigation path.

## D-053

- Date: 2026-03-13
- Status: accepted
- Decision: Keep Telegram mobile auth on an HTTPS callback bridge hosted on `https://incomedy.ru/auth/telegram/callback`, and let that bridge page hand control back to the app via the registered deep link rather than sending Telegram directly to a custom-scheme `return_to`.
- Rationale: Real-device Telegram/browser behavior is not reliable when the OAuth flow attempts to jump straight from Telegram to the custom scheme. An HTTPS bridge keeps the return on the approved domain, gives the browser a stable page to run handoff logic from, and provides a place to attach safe diagnostics when the app callback does not arrive.
- Consequences: `data/auth` must generate Telegram launch URLs with the HTTPS callback bridge, server/OpenAPI/docs must treat `/auth/telegram/callback` as a first-class auth surface, and Android/iOS callback debugging should distinguish `Telegram -> bridge` from `bridge -> app` rather than treating the handoff as a single opaque step.

## D-054

- Date: 2026-03-13
- Status: accepted
- Decision: Align Telegram mobile login with the current official Telegram OIDC authorization-code flow (`/auth` -> `code` -> `/token` + `id_token` verification with PKCE), while preserving the existing HTTPS callback bridge back into the app.
- Rationale: The current implementation mixes `oauth.telegram.org/auth` with the archived widget-style `id/auth_date/hash` verification contract, which no longer matches the current official Telegram login documentation and leaves the mobile flow broken. The supported Telegram login documentation now centers on authorization code exchange and OIDC token validation, while also explicitly framing the authorization URL as a browser-opened flow rather than a Telegram-app-specific custom scheme contract.
- Consequences: Mobile Telegram auth must handle `code` callbacks instead of relying on legacy `hash` payloads, the backend must own Telegram code exchange and `id_token` verification against official discovery/JWKS metadata, and repository docs/tests/config must move to the official OIDC contract. The HTTPS callback bridge remains the required browser-to-app return surface, while unsupported Telegram-app-first launch heuristics are not part of the supported auth path unless Telegram documents them explicitly later.

# Session Log Part 13

## 2026-03-16 21:57

- Context: After the modular auth/session split was reviewed, the user challenged the remaining architecture shape, specifically pointing out that `data` must not depend on `feature/domain` and asking to make the layering explicit and to codify it as a repository standard.
- Changes: Introduced explicit `:domain:auth` and `:domain:session` modules, moved auth/session business contracts out of `feature/*/domain`, removed the obsolete `:feature:session` module, rewired `data`, `shared`, and app dependencies to `domain`, and updated engineering standards, quality rules, architecture overview, and active decision traceability to describe the enforced `core / domain / data / feature` layering.
- Decisions: Treat `core` as technical-only shared support, treat `domain` as the only home for business contracts and use-case-facing models, allow `data -> domain` but forbid `data -> feature/presentation`, and treat any business contracts under `feature/*` as architectural debt to be moved.
- Next: Keep new auth/session work on top of the explicit domain layer, then continue shrinking remaining app-composition debt where `shared` still owns feature orchestration that could later become dedicated feature modules.

## 2026-03-16 22:14

- Context: After clarifying the project plan, the user explicitly removed the previously mentioned live smoke of the current organizer workspace invitation/role-management flow from the active task list and asked to treat it as no longer required for near-term execution planning.
- Changes: Updated governance planning memory to drop the workspace smoke follow-up from the active queue and to use the next documented product slice from the `P0` backlog as the immediate execution target.
- Decisions: Do not keep live smoke of the current workspace flow as an explicit blocking project task; treat `Venue management and hall template builder v1` as the next active product slice while the `shared` orchestration cleanup remains a secondary technical follow-up rather than the primary plan item.
- Next: Formalize and start `Venue management and hall template builder v1`.

## 2026-03-16 22:21

- Context: The user postponed the next venue/hall product slice to a later chat and redirected the current session to backend cleanup: remove confusing legacy Telegram naming from the server code where the implementation is already provider-agnostic, review the backend structure, and propose practical improvements.
- Changes: Renamed the generic PostgreSQL persistence implementation from `PostgresTelegramUserRepository` to `PostgresUserRepository` and the server test double from `InMemoryTelegramUserRepository` to `InMemoryUserRepository`; updated server wiring, tests, and traceability paths; and introduced narrower backend repository ports (`SessionUserRepository`, `WorkspaceRepository`) so session and organizer code no longer depend on the full `UserRepository` surface when they only need bounded subsets.
- Decisions: Treat Telegram-specific naming outside the actual Telegram auth slice as architecture debt and remove it when the code is already provider-agnostic; keep the large persistence implementation behavior-preserving for now, but start shrinking compile-time coupling by moving route/service consumers onto smaller repository ports before attempting a deeper repository split.
- Next: Use the new backend naming and narrower ports as the baseline, then continue with a deeper server cleanup pass focused on splitting the oversized persistence and route surfaces (`PostgresUserRepository`, `WorkspaceRoutes`) into bounded modules/helpers.

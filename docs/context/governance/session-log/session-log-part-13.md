# Session Log Part 13

## 2026-03-16 21:57

- Context: After the modular auth/session split was reviewed, the user challenged the remaining architecture shape, specifically pointing out that `data` must not depend on `feature/domain` and asking to make the layering explicit and to codify it as a repository standard.
- Changes: Introduced explicit `:domain:auth` and `:domain:session` modules, moved auth/session business contracts out of `feature/*/domain`, removed the obsolete `:feature:session` module, rewired `data`, `shared`, and app dependencies to `domain`, and updated engineering standards, quality rules, architecture overview, and active decision traceability to describe the enforced `core / domain / data / feature` layering.
- Decisions: Treat `core` as technical-only shared support, treat `domain` as the only home for business contracts and use-case-facing models, allow `data -> domain` but forbid `data -> feature/presentation`, and treat any business contracts under `feature/*` as architectural debt to be moved.
- Next: Keep new auth/session work on top of the explicit domain layer, then continue shrinking remaining app-composition debt where `shared` still owns feature orchestration that could later become dedicated feature modules.

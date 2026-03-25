# Documentation Map

`docs/` contains three documentation layers with different roles.

## Start Here

- For any new chat or onboarding pass, read `context/00-current-state.md` first.
- `context/*` is the primary source of truth for ongoing product and engineering work.
- If a task needs detailed target-state clarification, continue into `standup-platform-ru/*`.
- `project-reference/*` is a derived repository handbook for faster human navigation; it does not override `context/*` or code.

## Layers

- `context/`: compact working memory, rules, decisions, handoff protocol, and ongoing delivery state.
- `standup-platform-ru/`: detailed Russian-language product and technical specification package for the target platform.
- `project-reference/`: static HTML handbook built from the repository and context docs for newcomer/reviewer orientation.

## Derived Artifacts

- `screen-graph/`: static HTML screen-flow map for design preparation, derived from `context/*` and `standup-platform-ru/*`.

## Working Rule

- If `project-reference/*` conflicts with `context/*`, update `context/*` first.
- If `context/*` conflicts with actual implementation, reconcile and update `context/*` before continuing feature work.
- If `context/*` and `standup-platform-ru/*` diverge, treat `context/*` as the operational source of truth and `standup-platform-ru/*` as the detailed target-state layer that must be resynchronized.
- New and materially updated project documentation in `context/*` and top-level `docs/` navigation files should be written in Russian; untouched historical text may be normalized gradually when those files are edited.
- Repeated technical problems and their known repair paths should be accumulated in `context/engineering/issue-resolution-log.md`.
- Before new blocker diagnostics, first check `context/engineering/issue-resolution-log.md` for an existing matching entry.
- For iOS simulator / Xcode destination failures, first launch Xcode or restart it if it is hung.
- Executor runbook is split into a short checklist and a detailed policy under `context/handoff/`.

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

## Working Rule

- If `project-reference/*` conflicts with `context/*`, update `context/*` first.
- If `context/*` conflicts with actual implementation, reconcile and update `context/*` before continuing feature work.
- If `context/*` and `standup-platform-ru/*` diverge, treat `context/*` as the operational source of truth and `standup-platform-ru/*` as the detailed target-state layer that must be resynchronized.

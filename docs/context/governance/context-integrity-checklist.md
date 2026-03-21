# Context Integrity Checklist

Run before merge/commit of major changes.

- [ ] Relevant `docs/context/*` files were reviewed before implementation.
- [ ] `00-current-state.md` reflects the latest decision id, current focus, next step, and active cross-cutting constraints for this change (if applicable).
- [ ] New decisions were added to `governance/decisions-log.md` (if applicable).
- [ ] `governance/session-log.md` has a new entry for this work block.
- [ ] `handoff/task-request-log.md` reflects the major task request/outcome when the work required formalized task structuring.
- [ ] `product/backlog.md` reflects changed priorities/scope (if applicable).
- [ ] If an external provider is described as active/default/confirmed, the user's explicit confirmation is recorded in the current task/session/decision memory.
- [ ] Any discovered vulnerabilities are communicated to product owner immediately and logged in `product/risk-log.md` with remediation plan and target date.
- [ ] No conflicting rules introduced across engineering documents.

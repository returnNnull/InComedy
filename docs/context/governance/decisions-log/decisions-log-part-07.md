# Decisions Log Part 07

## D-078

- Date: 2026-03-25
- Status: accepted
- Decision: `EPIC-069` должен использовать `Ktor WebSockets` как подтвержденный realtime transport для live event channel-а `/ws/events/{eventId}`, а первый delivery шаг этого epic-а может опираться на server-local in-memory broadcaster только для lineup/live-stage событий текущего процесса без одновременного внедрения outbox/multi-instance fanout.
- Rationale: После явного user confirmation `EPIC-068` ближайшим `P0` gap стал realtime delivery для live stage updates. В `tooling-stack.md` `Ktor WebSockets` уже был кандидатом, стек Ktor присутствует на backend-е, а user request требует немедленно продолжить разработку, не тратя новый bounded slot на повторный транспортный выбор. Полный durable event bus/outbox и multi-instance delivery по-прежнему нужны, но их внедрение в тот же запуск расширило бы scope сверх одного bounded шага и задержало бы первую ценность для live stage flow.
- Consequences: `tooling-stack.md` переводит `Ktor WebSockets` в confirmed для realtime delivery; `EPIC-069` стартует с `TASK-084`, который доставляет backend WebSocket channel и audience-safe lineup/live-stage event payload-ы; текущая реализация должна явно фиксировать ограничение на single-process delivery в implementation/task memory; последующие bounded подзадачи продолжают этот epic client subscription/wiring-слоями без возврата к transport choice.

## D-079

- Date: 2026-03-25
- Status: accepted
- Decision: После `completed` или `docs_only` подзадачи executor обязан закрывать local commit boundary до переключения recovery/docs на следующий active `TASK`; dirty worktree в таком состоянии должен трактоваться как `ready_to_commit`, а не как разрешение продолжать новую подзадачу.
- Rationale: В `EPIC-069` recovery docs были переведены на `TASK-085`, хотя `TASK-084` остался незакоммиченным в worktree. Из-за этого новая сессия могла начать следующий bounded шаг поверх незакрытого предыдущего task-а. Пользователь потребовал guardrail, который ловит такую ситуацию уже на bootstrap и делает commit boundary обязательной частью closure sequence.
- Consequences: `executor-policy.md`, `executor-checklist.md`, `context-protocol.md`, `docs/context/README.md`, `docs/README.md`, `00-current-state.md`, `active-run.md` и task/session memory должны явно запрещать переключение `active_subtask_id`/recovery на следующий task до локального commit; если docs и git status расходятся таким образом, новая сессия обязана сначала закрыть commit boundary предыдущей подзадачи.

## D-080

- Date: 2026-03-25
- Status: accepted
- Decision: `docs/context/product/risk-log.md` становится каноническим реестром активных product/delivery/technical/security risks, а executor automation обязана обновлять его в том же work block, если meaningful task создал, изменил или снял активные residual limitations или rollout risks.
- Rationale: Commit message section `Ограничения и риски`, task memory и session log хорошо объясняют локальный outcome, но плохо работают как единый glanceable register текущих активных рисков. Пользователь явно потребовал централизованное место, которое сохраняет такие ограничения между запусками и не заставляет искать их по commit messages и разным handoff/docs файлам.
- Consequences: `product/risk-log.md` расширяется до общего active risk register, получает template для delivery/technical risks и новый realtime risk `R-013`; `automation-executor-prompt.md`, `executor-checklist.md`, `executor-policy.md`, `context-protocol.md`, `docs/context/README.md`, `docs/README.md`, `quality-rules.md`, `context-integrity-checklist.md`, `issue-resolution-log.md` и `00-current-state.md` должны явно требовать синхронного risk-log update, а commit message `Ограничения и риски` теперь считается только локальным summary, а не каноническим хранилищем.

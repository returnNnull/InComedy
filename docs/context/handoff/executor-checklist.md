# Чеклист executor

Короткий checklist для scheduled run-ов `InComedy Executor`.

Если нужен полный policy/rationale/edge cases, смотри [executor-policy.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/executor-policy.md).

## Старт

1. Прочитай [active-run.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/active-run.md), если файл существует.
2. Сверь текущую ветку и `git status`.
3. Если docs уже указывают на следующий `TASK`, но worktree остается dirty после `completed`/`docs_only` подзадачи, сначала закрой commit boundary предыдущей подзадачи, а не продолжай новый task.
4. Прочитай [00-current-state.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md).
5. Для полного read order следуй [context-protocol.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/context-protocol.md).

## Выбор работы

1. Продолжай текущий `EPIC/TASK`, если recovery state не завершён.
2. Выполняй ровно одну bounded подзадачу за запуск.
3. Не начинай новый epic, пока текущий не завершён или не зафиксирован как true external blocker.
4. Если текущий epic в `awaiting_user_review`, не открывай новый без явного user confirmation.

## Блокеры

1. Перед новой диагностикой blocker-а сначала проверь [issue-resolution-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/issue-resolution-log.md).
2. Для iOS simulator / Xcode destination симптомов первым действием считай запуск Xcode или его перезапуск, если он завис.
3. Только потом переходи к глубокой диагностике.
4. True external blocker фиксируй только после исчерпания локального repair path или при явной внешней зависимости.

## Проверка

1. Для code changes выполни минимальный релевантный набор checks из [test-strategy.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/test-strategy.md).
2. Перед закрытием meaningful task зафиксируй security review verdict.
3. Если task создает, меняет или снимает активные product/delivery/technical/security risks, обнови [risk-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/risk-log.md) в том же work block; commit message `Ограничения и риски` не заменяет этот реестр.
4. Для history конкретных прогонов используй [verification-memory.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/verification-memory.md).
5. Если подзадача дошла до `completed` или `docs_only`, переведи recovery state в `ready_to_commit`, создай локальный commit и только потом переключай active recovery на следующий task.

## Обязательные обновления

Обновляй в том же work block:

- [00-current-state.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md)
- [risk-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/risk-log.md) при новых/измененных активных рисках, остаточных ограничениях или уязвимостях
- [session-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/session-log.md)
- [decision-traceability.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decision-traceability.md)
- [decisions-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decisions-log.md) при новых решениях
- [issue-resolution-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/issue-resolution-log.md) при новых blocker-ах или repair path
- [task-request-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-log.md)
- [active-run.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/active-run.md)

## Завершение

1. Для `completed`/`docs_only` подзадачи closure gate обязателен: `verification -> security verdict -> docs sync -> local commit`.
2. Пока commit не создан, не переключай `active_subtask_id`/`Active Subtask` на следующий task.
3. Если все подзадачи epic завершены, переведи epic в `awaiting_user_review`.
4. Не стартуй следующий epic без user confirmation.
5. В конце запуска подтверди:
   - последний ID решения;
   - текущий `P0 focus`;
   - следующий шаг;
   - статус ключевых активных рисков;
   - статус ключевых решений;
   - ровно одну следующую подзадачу.

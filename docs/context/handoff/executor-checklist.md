# Чеклист executor

Короткий checklist для scheduled run-ов `InComedy Executor`.

Если нужен полный policy/rationale/edge cases, смотри [executor-policy.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/executor-policy.md).

## Старт

1. Прочитай [active-run.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/active-run.md), если файл существует.
2. Сверь текущую ветку и `git status`.
3. Прочитай [00-current-state.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md).
4. Для полного read order следуй [context-protocol.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/context-protocol.md).

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
3. Для history конкретных прогонов используй [verification-memory.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/verification-memory.md).

## Обязательные обновления

Обновляй в том же work block:

- [00-current-state.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md)
- [session-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/session-log.md)
- [decision-traceability.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decision-traceability.md)
- [decisions-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decisions-log.md) при новых решениях
- [issue-resolution-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/issue-resolution-log.md) при новых blocker-ах или repair path
- [task-request-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-log.md)
- [active-run.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/active-run.md)

## Завершение

1. Если все подзадачи epic завершены, переведи epic в `awaiting_user_review`.
2. Не стартуй следующий epic без user confirmation.
3. В конце запуска подтверди:
   - последний ID решения;
   - текущий `P0 focus`;
   - следующий шаг;
   - статус ключевых решений;
   - ровно одну следующую подзадачу.

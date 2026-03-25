# Runbook automation executor

Обязательный runbook для всех automation-запусков `InComedy Executor`.

Automation `prompt` в TOML не должен дублировать длинные process-правила.
Он должен ссылаться на этот документ как на основной источник процесса.

## Порядок чтения для automation run

1. [active-run.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/active-run.md)
2. [executor-checklist.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/executor-checklist.md)
3. [00-current-state.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md)
4. [context-protocol.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/context-protocol.md), если нужен полный read order
5. [executor-policy.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/executor-policy.md), если чеклиста уже недостаточно или возник edge case / policy question

## Зачем этот файл остаётся

- Automation TOML prompts по-прежнему должны ссылаться именно на этот файл как на стабильную entry point.
- Операционный startup намеренно разделён:
  - fast path: [executor-checklist.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/executor-checklist.md)
  - detailed rules: [executor-policy.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/executor-policy.md)

## Правило

- Если эти документы и репозиторий расходятся, сначала обнови docs, затем продолжай работу.
- Если meaningful task создает, меняет или снимает активные ограничения/риски, синхронно обнови [product/risk-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/risk-log.md) в том же work block.

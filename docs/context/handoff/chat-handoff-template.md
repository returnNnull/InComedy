# Chat Handoff Template

Copy and send this message to start a new chat with full context sync:

```text
Работаем в репозитории: /Users/abetirov/AndroidStudioProjects/InComedy

Ключевое правило: `docs/context/*` — primary source of truth.
Если новая информация конфликтует с документами, сначала обнови документы, потом код.

Сначала синхронизируй контекст по порядку:
1) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/product-brief.md
2) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/tooling-stack.md
3) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/engineering-standards.md
4) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/quality-rules.md
5) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/non-functional-requirements.md
6) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/architecture-overview.md
7) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/test-strategy.md
8) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decisions-log.md
9) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/backlog.md
10) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/session-log.md (последние записи сначала)
11) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decision-traceability.md

После чтения:
- Подтверди последний ID решения из governance/decisions-log.
- Подтверди текущий P0 приоритет из product/backlog.
- Подтверди следующий шаг из governance/session-log.
- Подтверди текущий статус исполнения ключевых решений из governance/decision-traceability.
- Только после этого переходи к реализации.

Режим постановки задач:
- Пользователь ставит задачу в свободной форме.
- Ты обязан сам формализовать задачу в структуру `task-request-template.md` и синхронизировать контекст-документы.

Если в ходе задачи меняются scope/решения/стек/правила:
- Обнови соответствующие файлы в `docs/context/*` в том же изменении.

Правило размера контекста:
- Если документ в `docs/context/*` становится слишком большим (примерно 8,000+ символов), дроби его на части по `context-protocol.md`.
- Части храни в отдельной подпапке документа (`name/name-part-01.md`, `name/name-part-02.md`), чтобы не засорять директорию.
- В новом чате сначала читай индексный файл (`name.md`), потом только нужные части.
```

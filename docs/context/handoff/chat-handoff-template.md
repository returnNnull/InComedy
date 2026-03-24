# Chat Handoff Template

Copy and send this message to start a new chat with full context sync:

```text
Работаем в репозитории: /Users/abetirov/AndroidStudioProjects/InComedy

Ключевое правило: `docs/context/*` — primary source of truth.
Если новая информация конфликтует с документами, сначала обнови документы, потом код.

Постоянные правила реализации:
- Новый и существенно измененный код нужно комментировать на уровне class/object/interface, method/function и значимых field/property; комментарии должны быть на русском языке, объяснять ответственность и ход потока, а не пересказывать синтаксис.
- Для backend production-significant flows используй structured logging через sanitized diagnostics system; `println`, raw container logs и другой ad-hoc unstructured logging не считаются основным путем диагностики.
- Выбор внешнего auth/payment/push/PSP-провайдера не может считаться принятым автоматически по прошлому ответу чата, существующему коду, примеру env/config или draft-документации; нужен явный `user confirmation`.

Сначала синхронизируй контекст по порядку:
0) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/active-run.md, если файл существует
1) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md
2) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/product-brief.md
3) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/backlog.md
4) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/tooling-stack.md
5) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/engineering-standards.md
6) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/quality-rules.md
7) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/non-functional-requirements.md
8) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/architecture-overview.md
9) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/test-strategy.md
10) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decisions-log.md + latest part from `00-current-state.md`
11) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/session-log.md + latest part from `00-current-state.md` (последние записи сначала)
12) /Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/decision-traceability.md + latest part from `00-current-state.md`
13) /Users/abetirov/AndroidStudioProjects/InComedy/docs/standup-platform-ru/README.md и релевантные подробные spec-файлы, если задача требует продуктовой детализации

Сразу после чтения шага 0:
- Сверь `active-run.md` с `git status` и текущей веткой.
- Если `active-run.md` показывает `in_progress`, `awaiting_verification` или `partial` и есть незакоммиченные изменения, продолжай тот же `active_epic_id` и `active_subtask_id`.
- Проблемы verification/test-runtime для активной задачи не считаются отдельным blocker/task по умолчанию: их нужно дожимать внутри того же `active_epic_id` / `active_subtask_id`, пока не появится настоящий внешний blocker или граница user confirmation.
- Не выбирай новую задачу, пока recovery state не приведен в консистентное состояние.

После чтения:
- Подтверди последний ID решения из governance/decisions-log.
- Подтверди текущий P0 focus из `00-current-state.md` и product/backlog.
- Подтверди следующий шаг из `00-current-state.md` и governance/session-log.
- Подтверди текущий статус исполнения ключевых решений из governance/decision-traceability.
- Только после этого переходи к реализации.

Режим постановки задач:
- Пользователь ставит задачу в свободной форме.
- Для значимой задачи сначала используй `/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-template.md` как структуру постановки.
- Историю формализованных задач и implementation outcome-ов веди в `/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-log.md` (append в latest part из индекса).
- В ходе каждой значимой задачи кратко фиксируй ход переписки и работы в `docs/context/governance/session-log.md`:
  - что запросил пользователь,
  - что изменилось по ходу обсуждения,
  - какие решения приняты,
  - какой следующий шаг.
- Не копируй raw transcript. Нужна короткая аналитическая сводка в формате `Context / Changes / Decisions / Next`.
- Никогда не записывай в session log секреты, токены и другую чувствительную информацию.
- Если задача требует server diagnostics или production triage, используй `/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/server-diagnostics-runbook.md`.

Если в ходе задачи меняются scope/решения/стек/правила:
- Обнови соответствующие файлы в `docs/context/*` в том же изменении.
- Если меняется latest decision, current P0 focus, next step или active cross-cutting constraints, обнови `/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md` в том же изменении.

Правило размера контекста:
- Если документ в `docs/context/*` становится слишком большим (примерно 8,000+ символов), дроби его на части по `context-protocol.md`.
- Части храни в отдельной подпапке документа (`name/name-part-01.md`, `name/name-part-02.md`), чтобы не засорять директорию.
- В новом чате сначала читай индексный файл (`name.md`), потом только нужные части.
```

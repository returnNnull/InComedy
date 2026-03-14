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
- В ходе каждой значимой задачи кратко фиксируй ход переписки и работы в `docs/context/governance/session-log.md`:
  - что запросил пользователь,
  - что изменилось по ходу обсуждения,
  - какие решения приняты,
  - какой следующий шаг.
- Не копируй raw transcript. Нужна короткая аналитическая сводка в формате `Context / Changes / Decisions / Next`.
- Никогда не записывай в session log секреты, токены и другую чувствительную информацию.

Операционный доступ к логам/диагностике сервера:
- Сначала предпочитай sanitized server diagnostics, а не raw container logs.
- Для diagnostics используй helper `/Users/abetirov/AndroidStudioProjects/InComedy/scripts/fetch_server_diagnostics.sh`.
- Base URL: `https://incomedy.ru`
- Diagnostics token брать из `/Users/abetirov/AndroidStudioProjects/InComedy/deploy/server/.env` (`DIAGNOSTICS_ACCESS_TOKEN`), но никогда не печатать его в ответах или в docs.
- Пример:
  `INCOMEDY_DIAGNOSTICS_BASE_URL=https://incomedy.ru INCOMEDY_DIAGNOSTICS_TOKEN=<token from deploy/server/.env> /Users/abetirov/AndroidStudioProjects/InComedy/scripts/fetch_server_diagnostics.sh --route-prefix /api/v1/auth/telegram --from 2026-03-13T00:00:00Z`
- Для корреляции используй `requestId` из backend error messages, Android `AUTH_FLOW`, и diagnostics events.
- Если нужны raw container logs/compose state на хосте:
  `ssh -i ~/.ssh/incomedy_gha root@83.222.24.63`
  `cd /opt/incomedy/server`
  `docker compose ps`
  `docker logs --tail 200 incomedy-server`
- Если нужен restart/recreate сервера, сначала проверь `/opt/incomedy/server/.env`: не полагайся на `IMAGE=ghcr.io/returnnnull/incomedy-server:latest`, потому что `latest` на этом хосте может быть stale; предпочитай явно pinned SHA image.

Если в ходе задачи меняются scope/решения/стек/правила:
- Обнови соответствующие файлы в `docs/context/*` в том же изменении.

Правило размера контекста:
- Если документ в `docs/context/*` становится слишком большим (примерно 8,000+ символов), дроби его на части по `context-protocol.md`.
- Части храни в отдельной подпапке документа (`name/name-part-01.md`, `name/name-part-02.md`), чтобы не засорять директорию.
- В новом чате сначала читай индексный файл (`name.md`), потом только нужные части.
```

# Executor Policy

Подробный policy-документ для scheduled run-ов `InComedy Executor`.

Короткий operational path смотри в [executor-checklist.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/executor-checklist.md).

## Scope

- `docs/context/*` — primary source of truth.
- Документацию веди на русском, архивные записи переводить не нужно.
- Если новая информация конфликтует с документами, сначала обнови документы, потом код.
- Если пользователь меняет process/governance rules, сначала синхронно обнови соответствующие context-документы и decision log, потом продолжай работу.
- Scheduled executor runs по умолчанию работают в `implementation mode`.
- Для явно аналитических user-request-ов без изменения репозитория разрешен `read-only mode`, но только если такой запрос действительно пришел от пользователя.

## Status Model

- `Epic status`: `planned`, `in_progress`, `blocked_external`, `awaiting_user_review`, `done`
- `Subtask status`: `planned`, `in_progress`, `completed`, `docs_only`
- `Run status` в `active-run.md`: `in_progress`, `verifying`, `ready_to_commit`, `completed`, `docs_only`, `blocked_external`

Подзадача должна быть закрыта в рамках одной сессии. Задача считается закрытой, если код компилируется и все тесты пройдены.
Если возникают проблемы с запуском эмулятора или тестами, проблема должна быть устранена в той же сессии в течение 90 минут.
Если в течение 90 минут не удалось полностью устранить проблему, нужно зафиксировать текущий прогресс так, чтобы новый запуск не проходил тот же путь заново, а продолжил repair с последнего recovery checkpoint.
Локальные simulator/emulator, build/test-harness, toolchain, cache и device-set проблемы не считаются true external blocker сами по себе: следующий bounded run должен продолжать их repair в рамках того же `TASK`.
Блокерами являются лишь решения по выбору внешних провайдеров, непопулярных и не состоящих в списках рекомендаций Google библиотек, изменение архитектурных решений и ключевых правил разработки.
Если блокер является внешним и требует вмешательства пользователя, нужно зафиксировать конкретное указание пользователю  (`Выбрать ...`, `Обновить ...`, `Перезагрузить ...` и т.д.).
Для `completed` и `docs_only` подзадачи closure sequence обязателен и не может быть сокращен: `verification -> security verdict -> docs sync -> ready_to_commit -> local commit -> switch next task`.

## AutomationState

Поддерживай в [00-current-state.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md) YAML-блок `AutomationState` с полями:

- `cycle_id`
- `cycle_window=10:00-04:00 Europe/Moscow`
- `active_epic_id`
- `active_subtask_id`
- `active_branch`
- `epic_status`
- `run_slots_used_in_cycle`
- `last_run_at`
- `last_run_result`

`run_slots_used_in_cycle` считает количество запусков в cycle.

## Execution Rules

- Держи один active epic до завершения или true external blocker.
- Если active epic отсутствует, выбери самую приоритетную незавершенную задачу из:
  - [00-current-state.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/00-current-state.md)
  - [backlog.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/backlog.md)
  - [task-request-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-log.md)
- Назначь `EPIC-ID` и `TASK-ID`.
- Для нового epic сначала зафиксируй ordered subtask plan в [task-request-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-log.md).
- Следующий bounded run должен брать ближайшую незавершённую подзадачу из этого ordered plan.
- Новая product-подзадача может появиться только как документированное дробление уже запланированной подзадачи или как явно зафиксированное изменение плана с причиной в task/session memory.
- Для каждого epic используй отдельную локальную ветку `codex/<EPIC-ID>-<short-slug>`.
- В одном запуске выполняй ровно одну bounded подзадачу с одним четким outcome.
- Эта подзадача должна завершиться только статусом `completed` или `docs_only`.

## True External Stop Factors

Остановись без написания продуктового кода только если есть настоящий внешний стоп-фактор:

- нужен user confirmation;
- нужен выбор или смена auth/payment/push/PSP provider;
- нужна migration/backfill/irreversible data change;
- нужен destructive change;
- acceptance criteria неясны;
- отсутствуют обязательные секреты/доступы;
- после исчерпания локальных способов ремонта подтверждено, что нужен внешний host/service, недоступный в этой среде.

В таком случае:

- не оставляй implementation task подвешенным;
- закрой текущий запуск отдельным завершенным `docs_only` blocker outcome;
- зафиксируй точный blocker verdict, доказательства и следующий action;
- при необходимости переведи `epic_status` в `blocked_external`.

## Documentation And Governance Rules

- Для значимой задачи сначала используй [task-request-template.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-template.md) как структуру постановки, а outcome/history фиксируй в [task-request-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/task-request-log.md).
- Если задача требует server diagnostics или production triage, используй [server-diagnostics-runbook.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/server-diagnostics-runbook.md).
- Если по задаче найден повторяемый technical blocker или неочевидный repair path, зафиксируй его в [issue-resolution-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/issue-resolution-log.md).
- Если meaningful task создаёт, меняет или снимает активный product/delivery/technical/security risk, обнови [product/risk-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/product/risk-log.md) в том же work block. Commit message section `Ограничения и риски` используется как локальный summary, но не заменяет канонический risk register.
- Перед новой диагностикой blocker-а сначала проверь [issue-resolution-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/issue-resolution-log.md), нет ли там уже существующей записи с теми же симптомами и известным repair path.
- Для iOS simulator / Xcode destination симптомов уровня `CoreSimulatorService`, `showdestinations`, placeholder destinations и похожих первым repair step считай запуск Xcode; если Xcode уже открыт, но завис или не отвечает, перезапусти его.
- В [session-log.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/governance/session-log.md) оставляй только короткую аналитическую сводку в формате `Context / Changes / Decisions / Next`.
- Новые и существенно обновляемые project docs в `docs/context/*`, `docs/README.md` и смежных index-файлах веди на русском.
- Если context-документ в `docs/context/*` разрастается примерно выше 8,000 символов, дроби его по правилу из [context-protocol.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/handoff/context-protocol.md).
- Выбор внешнего auth/payment/push/PSP provider не считай подтвержденным по коду, env/config примеру, draft docs или inference; нужен явный `user confirmation`.

## Verification

- Если изменен код, выполни минимальный релевантный набор тестов/checks/сборку по [test-strategy.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/test-strategy.md).
- Verification должна быть достаточной для закрытия текущей bounded подзадачи в этом же запуске.
- Историю конкретных прогонов и coverage memory веди в [verification-memory.md](/Users/abetirov/AndroidStudioProjects/InComedy/docs/context/engineering/verification-memory.md).
- Перед закрытием meaningful task выполни обязательный security review, соразмерный измененному scope, и кратко зафиксируй результат в governance/task memory.
- Если после task-а остаются активные residual limitations или rollout risks, зафиксируй их в `product/risk-log.md` с mitigation, owner, status и связанными артефактами.
- Если запуск был только docs/process sync и security-impacting surface не менялся, явно зафиксируй этот нулевой security verdict вместо пропуска проверки.

## Commit Rules

Локальный git commit обязателен для `completed` или `docs_only` подзадачи в том же run. До создания этого commit нельзя переводить recovery state на следующий active `TASK`.

Статус `ready_to_commit` означает, что verification, security review и обязательный docs sync уже завершены, но commit boundary для текущей bounded подзадачи еще не закрыт.

Правила commit:

- не более одного commit на подзадачу;
- только в `active_branch`;
- без `push`, если пользователь явно не запросил отправку в remote.
- пока локальный commit текущей `completed`/`docs_only` подзадачи не создан, нельзя менять `active_subtask_id`, `Active Subtask` или recovery `Next` на следующий task; можно фиксировать только будущий следующий шаг после commit;
- если `git status` все еще dirty изменениями уже закрытой подзадачи, recovery posture должен оставаться на commit boundary (`ready_to_commit`), а не на следующем task.
- если commit message содержит `Ограничения и риски`, все still-active ограничения из этого блока должны быть уже отражены в `product/risk-log.md` или явно сняты в том же work block.

Commit message делай подробным, на русском, с `TASK-ID` и `EPIC-ID`, в структуре:

- заголовок;
- Контекст;
- Что сделано;
- Проверки;
- Ограничения и риски;
- Следующий шаг.

## Finish

Если все подзадачи epic завершены:

- переведи `epic_status` в `awaiting_user_review`;
- оставь ветку checkout-нутой;
- обнови документы;
- подготовь summary для review;
- остановись.

Не начинай новый epic, пока пользователь явно не подтвердит review.
Статус `done` ставь только после явного user confirmation.

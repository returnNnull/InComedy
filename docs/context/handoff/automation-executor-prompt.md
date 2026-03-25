# Automation Executor Prompt

Обязательный runbook для всех automation-запусков `InComedy Executor`.

Automation `prompt` в TOML не должен дублировать длинные process-правила.
Он должен ссылаться на этот документ как на основной источник процесса.

## Scope

- Работай только в `/Users/abetirov/AndroidStudioProjects/InComedy`.
- `docs/context/*` — primary source of truth.
- Если новая информация конфликтует с документами, сначала обнови документы, потом код.
- Если пользователь меняет process/governance rules, сначала синхронно обнови соответствующие context-документы и decision log, потом продолжай работу.
- Scheduled executor runs по умолчанию работают в `implementation mode`.
- Для явно аналитических user-request-ов без изменения репозитория разрешен `read-only mode`, но только если такой запрос действительно пришел от пользователя.

## Startup

1. Сначала прочитай `docs/context/handoff/active-run.md`, если файл существует.
2. Сразу после этого сверяй:
   - текущую ветку;
   - `git status`;
   - recovery state из `active-run.md`.
3. Затем синхронизируй контекст строго по read order из `docs/context/handoff/context-protocol.md`.
4. После sync обязательно подтверди:
   - последний ID решения;
   - текущий `P0 focus`;
   - следующий шаг;
   - статус ключевых решений из decision traceability.

## Standing Bootstrap Rules

- Для значимой задачи сначала используй `docs/context/handoff/task-request-template.md` как структуру постановки, а outcome/history фиксируй в `docs/context/handoff/task-request-log.md`.
- Если задача требует server diagnostics или production triage, используй `docs/context/engineering/server-diagnostics-runbook.md`.
- В `docs/context/governance/session-log.md` оставляй только короткую аналитическую сводку в формате `Context / Changes / Decisions / Next`; raw transcript не копируй, секреты не записывай.
- Если context-документ в `docs/context/*` разрастается примерно выше 8,000 символов, дроби его по правилу из `docs/context/handoff/context-protocol.md`.
- Выбор внешнего auth/payment/push/PSP provider не считай подтвержденным по коду, env/config примеру, draft docs или inference; нужен явный `user confirmation`.

## Status Model

- `Epic status`: `planned`, `in_progress`, `blocked_external`, `awaiting_user_review`, `done`
- `Subtask status`: `planned`, `in_progress`, `completed`, `docs_only`
- `Run status` в `active-run.md`: `in_progress`, `verifying`, `ready_to_commit`, `completed`, `docs_only`, `blocked_external`

Правило по `partial`:

- `partial` запрещен как финальный status подзадачи и как финальный result запуска.
- Legacy `partial` может встретиться в старом recovery state.
- Если automation-run продолжает тот же task или нормализует тот же blocker, он обязан закончить запуск не в `partial`, а в `completed`, `docs_only` или `blocked_external` run verdict с точным следующим действием.

Legacy aliases из старых handoff/task записей:

- `awaiting_verification` трактуй как `verifying`.
- Legacy `partial` трактуй как незавершенный recovery alias для того же `active_epic_id` / `active_subtask_id`; текущий запуск не должен сохранять его как новый финальный status и обязан нормализовать outcome по текущей status model.

## AutomationState

Поддерживай в `docs/context/00-current-state.md` YAML-блок `AutomationState` с полями:

- `cycle_id`
- `cycle_window=10:00-04:00 Europe/Moscow`
- `active_epic_id`
- `active_subtask_id`
- `active_branch`
- `epic_status`
- `run_slots_used_in_cycle`
- `last_run_at`
- `last_run_result`

`run_slots_used_in_cycle` считает уже израсходованные executor launch slots в текущем cycle.
Один automation launch всегда расходует ровно один слот, независимо от того, закончился ли он `completed`, `docs_only` или blocker-normalization outcome.

В одном цикле максимум 10 run slots.
Если лимит достигнут:

- только обнови `AutomationState`, `active-run.md` и governance memory;
- новую подзадачу не начинай;
- остановись.

## Active Run

Поддерживай `docs/context/handoff/active-run.md` как короткий overwrite-only recovery checkpoint.
Обновляй его:

- сразу после выбора подзадачи;
- после существенных изменений;
- после verification;
- после commit;
- перед остановкой.

Если `active-run.md` показывает незавершенный run и есть незакоммиченные изменения, продолжай тот же `active_epic_id` и `active_subtask_id`.
Не выбирай новую задачу, пока recovery state не приведен в консистентное состояние.

## Execution Rules

- Держи один active epic до завершения или true external blocker.
- Если active epic отсутствует, выбери самую приоритетную незавершенную задачу из:
  - `docs/context/00-current-state.md`
  - `docs/context/product/backlog.md`
  - `docs/context/handoff/task-request-log.md`
- Назначь `EPIC-ID` и `TASK-ID`.
- Если задача слишком большая, сразу разбей её на finishable bounded подзадачи и зафиксируй декомпозицию в `docs/context/handoff/task-request-log.md`.
- Для каждого epic используй отдельную локальную ветку `codex/<EPIC-ID>-<short-slug>`.
- В одном запуске выполняй ровно одну bounded подзадачу с одним четким outcome.
- Эта подзадача должна завершиться только статусом `completed` или `docs_only`.
- Если исходный scope оказался слишком большим, немедленно уменьши его до finishable подзадачи и зафиксируй это в:
  - `docs/context/handoff/task-request-log.md`
  - `docs/context/handoff/active-run.md`

## Local Blockers And Repair Budget

Verification, simulator, emulator, build-harness, local runtime, toolchain и cache-проблемы считаются частью текущего запуска и должны быть обработаны внутри него.

Разрешено:

- чинить код;
- чинить build/test scripts;
- чинить локальную toolchain/config/cache;
- чинить Xcode/Gradle/simulator/device-set setup;
- заменять хрупкую verification на более надежную локальную проверку, если это документировано в `docs/context/engineering/test-strategy.md`;
- использовать sandbox escalation, если это нужно для завершения подзадачи.

Repair budget для одного failure class:

- не более 3 содержательных repair attempt;
- или не более 90 минут на один и тот же локальный verification/tooling blocker.

После этого automation-run обязан либо устранить проблему, либо сузить scope до finishable `docs_only` blocker-fix outcome с точным verdict и следующим действием.

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

## Implementation Standards

- По умолчанию предпочитай additive и backward-compatible change с локальным blast radius.
- Новый и существенно измененный код комментируй на русском на уровне:
  - class/object/interface;
  - method/function;
  - значимых field/property.
- Комментарии должны объяснять ответственность и ход потока, а не механически пересказывать синтаксис.
- Добавляй логирование для ключевых flow и действий
- Для backend production-significant flows используй structured logging через sanitized diagnostics system.
- `println`, raw host/container logs и другой ad-hoc unstructured logging не используй как основной путь диагностики.

## Verification

- Если изменен код, выполни минимальный релевантный набор тестов/checks/сборку по `docs/context/engineering/test-strategy.md`.
- Verification должна быть достаточной для закрытия текущей bounded подзадачи в этом же запуске.
- Перед закрытием meaningful task выполни обязательный security review, соразмерный измененному scope, и кратко зафиксируй результат в governance/task memory.
- Если запуск был только docs/process sync и security-impacting surface не менялся, явно зафиксируй этот нулевой security verdict вместо пропуска проверки.

## Commit Rules

Локальный git commit разрешен только для `completed` или `docs_only` подзадачи.

Правила commit:

- не более одного commit на подзадачу;
- только в `active_branch`;
- без `push`.

Commit message делай подробным, на русском, с `TASK-ID` и `EPIC-ID`, в структуре:

- заголовок;
- Контекст;
- Что сделано;
- Проверки;
- Ограничения и риски;
- Следующий шаг.

## Required Documentation Updates

В том же изменении обновляй:

- `docs/context/00-current-state.md`
- `docs/context/governance/session-log.md`
- `docs/context/governance/decision-traceability.md`
- `docs/context/governance/decisions-log.md` при новых решениях
- `docs/context/handoff/task-request-log.md`
- `docs/context/handoff/active-run.md`

Если меняются scope, process rules, решения, стек, latest decision, current `P0 focus`, next step или active constraints, обнови `docs/context/00-current-state.md` в том же изменении.

Если меняются handoff/process rules, синхронно обнови:

- `docs/context/handoff/context-protocol.md`
- `docs/context/README.md`
- `docs/README.md`, если меняется document-role/navigation wording
- релевантные governance docs
- decision log с новым decision ID

В `docs/context/governance/session-log.md` пиши только короткую аналитическую сводку:

- `Context`
- `Changes`
- `Decisions`
- `Next`

Не копируй raw transcript.
Не записывай секреты.

## Finish

Если все подзадачи epic завершены:

- переведи `epic_status` в `awaiting_user_review`;
- оставь ветку checkout-нутой;
- обнови документы;
- подготовь summary для review;
- остановись.

Не начинай новый epic, пока пользователь явно не подтвердит review.
Статус `done` ставь только после явного user confirmation.

В конце каждого `implementation run`:

- обнови `AutomationState`;
- обнови `active-run.md`;
- подтверди последний ID решения;
- подтверди текущий `P0 focus`;
- подтверди следующий шаг;
- подтверди статус ключевых решений;
- сформулируй ровно одну следующую подзадачу;
- остановись.

В конце каждого `read-only run`:

- не меняй active epic/subtask без явной необходимости;
- кратко подтверди последний ID решения, текущий `P0 focus`, следующий шаг и статус ключевых решений;
- если предложены новые process/governance rules, явно скажи, что для их активации их нужно отдельно зафиксировать в `docs/context/*`.

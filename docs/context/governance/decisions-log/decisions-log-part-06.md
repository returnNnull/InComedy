# Decisions Log Part 06

## D-072

- Date: 2026-03-25
- Status: accepted
- Decision: Host-local simulator/emulator/build/test-runtime blockers discovered while closing an active automation task must remain repair work inside the same `EPIC/TASK`, and the next bounded run must continue that local repair path before repository docs classify the task as `blocked_external` or redirect verification to another host.
- Rationale: `TASK-073` already has the repo-side live-stage UI implementation and generic iOS build stabilization in place, but the recorded recovery posture had drifted into a standing “use an unrestricted host” instruction after repeated `CoreSimulatorService` failures on the same machine. That posture encouraged future runs to stop at blocker acknowledgment instead of spending the next bounded slot on actual repair. The desired executor behavior is to keep the blocker inside the same task, continue local repair from the last checkpoint, and only escalate after the local repair path is explicitly exhausted and documented.
- Consequences: `active-run.md`, `00-current-state.md`, test/governance memory, and task history must describe the current simulator/XCUITest issue as an in-progress local repair target for `TASK-073`; `blocked_external` should be reserved for true external prerequisites or an explicitly documented exhaustion of local repair options; and the next executor run must start by attempting local simulator/device-set repair plus the targeted XCUITest rerun on the current host.

## D-073

- Date: 2026-03-25
- Status: accepted
- Decision: Каждый active epic должен иметь заранее зафиксированный ordered subtask plan в task memory; следующие product-подзадачи должны браться из этого плана, а не вычисляться на лету без отдельной фиксации изменения плана.
- Rationale: Текущая практика позволяла epic direction держать в backlog/current-state, а конкретные bounded подзадачи уточнять по ходу работ. Это давало гибкость, но делало future step selection менее предсказуемым: новый запуск мог интерпретировать “следующий шаг” слишком свободно. Пользователь потребовал более строгий процесс, в котором epic сначала раскладывается на подзадачи, а дальнейшее движение идёт по явному плану.
- Consequences: для каждого active epic в `task-request-log.md` и актуальном task-request part должен существовать ordered список подзадач с их статусами; новая product-подзадача или изменение порядка допустимы только через явное обновление плана с причиной в task/governance memory; `00-current-state.md` и `active-run.md` должны ссылаться на ближайшую подзадачу из этого плана.

## D-074

- Date: 2026-03-25
- Status: accepted
- Decision: Новые и существенно обновляемые project docs в `docs/context/*`, `docs/README.md` и смежных governance/handoff index-файлах должны вестись на русском языке; исторические англоязычные фрагменты не требуют немедленного массового перевода, но должны нормализоваться при касании соответствующего документа.
- Rationale: Пользователь потребовал, чтобы документация дальше велась только на русском. В репозитории уже есть русскоязычные code-comment и product-spec правила, но project context/governance docs всё ещё частично смешаны с английским. Нужна явная process-норма, чтобы новые правки были консистентны без отдельной тотальной миграции всего архива в этом же запуске.
- Consequences: runbook, protocol, current-state, README navigation, governance memory и quality rules должны закреплять русский как язык новых и существенно обновляемых docs; future doc edits должны писать новые разделы на русском, а старые англоязычные участки нормализовать по мере касания; точные технические имена и API/SDK terms могут оставаться на английском, если это необходимо для корректности.

## D-075

- Date: 2026-03-25
- Status: accepted
- Decision: В проекте должен вестись отдельный журнал повторяемых технических проблем и путей их решения в `docs/context/engineering/issue-resolution-log.md`; при обнаружении нового повторяемого blocker-а или существенного уточнения repair path этот журнал должен обновляться в том же work block.
- Rationale: Session/task memory хорошо хранит хронологию работы, но плохо подходит как долговременный playbook для повторяющихся технических проблем. Без отдельного журнала новый запуск рискует заново проходить те же команды, гипотезы и тупиковые шаги. Пользователь явно потребовал хранить найденные проблемы и способы их решения так, чтобы при повторении не стартовать с нуля.
- Consequences: `issue-resolution-log.md` становится постоянным местом для симптомов, диагностических команд, шагов ремонта, уже опробованных тупиков и критериев эскалации; runbook/protocol/checklists должны ссылаться на него; и текущий `CoreSimulatorService` blocker по `TASK-073` должен быть занесён туда как первая запись.

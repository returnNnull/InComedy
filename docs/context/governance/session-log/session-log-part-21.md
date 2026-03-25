# Session Log Part 21

## 2026-03-25 14:39

- Context: Пользователь потребовал отдельную документацию для накопления повторяемых проблем и уже найденных путей их решения, чтобы при повторении не восстанавливать repair path с нуля.
- Changes: Добавлен `docs/context/engineering/issue-resolution-log.md` как индекс журнала проблем и решений и `issue-resolution-log-part-01.md` с первой реальной записью `I-001` по текущему `CoreSimulatorService` / `xcodebuild -showdestinations` blocker для `TASK-073`. Обновлены runbook, protocol, README/navigation, quality rules, governance checklist, `00-current-state.md`, task memory и recovery snapshot, чтобы при повторяемом technical blocker-е требовалась запись в этот журнал. Принят `D-075`. Security review verdict: docs/process-only sync, security-impacting runtime surface не менялся.
- Decisions: Принят `D-075`: журнал проблем и решений становится обязательным durable memory для повторяемых technical blocker-ов и известных repair path.
- Next: Ближайшая подзадача по active epic plan не меняется: продолжить `TASK-073`, использовать `engineering/issue-resolution-log.md` как playbook по `I-001`, починить current-host iOS simulator/XCUITest blocker и затем пере-запустить targeted `testLineupTabShowsApplicationsAndReorderSurface`.

## 2026-03-25 14:42

- Context: Пользователь потребовал закоммитить весь накопленный пакет docs-only governance/process sync изменений одним подробным commit.
- Changes: Recovery checkpoint переведён в `ready_to_commit`; пакет включает sync по recovery posture для `TASK-073`, ordered epic plan rule, Russian-only docs policy и новый `issue-resolution-log` с записью `I-001`. Product code не менялся. Security review verdict остаётся прежним: docs/process-only sync, security-impacting runtime surface не менялся.
- Decisions: Новое governance decision не принималось; подготовлен единый локальный commit для уже зафиксированных `D-072`, `D-073`, `D-074` и `D-075`.
- Next: После локального commit продуктовый следующий шаг не меняется: продолжить `TASK-073`, использовать `I-001` как playbook и добить current-host iOS simulator/XCUITest repair перед targeted rerun.

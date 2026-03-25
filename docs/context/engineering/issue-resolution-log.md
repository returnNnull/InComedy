# Журнал проблем и решений

Этот журнал хранит повторяемые технические проблемы, признаки их возникновения, уже пройденный путь диагностики и известные шаги ремонта.

Используй его, когда:

- проблема уже повторялась или с высокой вероятностью повторится;
- найденный repair path неочевиден и его важно не потерять между сессиями;
- новый запуск иначе рискует заново проходить тот же диагностический путь;
- нужен короткий playbook для локальной triage/repair работы без восстановления контекста с нуля.

Не используй этот журнал вместо:

- `product/risk-log.md` для активных product/delivery/technical/security risks, residual limitations и уязвимостей;
- `governance/session-log.md` для хронологии сессий;
- `handoff/active-run.md` для crash-safe текущего recovery checkpoint.

## Symptom Index

- Xcode/simulator:
  - `I-001` — `CoreSimulatorService` / placeholder destinations / targeted XCUITest device selection failure
- Gradle/KMP:
  - пока нет записей
- Android emulator:
  - пока нет записей
- CI:
  - пока нет записей

## Формат записи

Каждая запись должна содержать:

- ID проблемы;
- короткий заголовок;
- симптомы и условия воспроизведения;
- уже подтверждённые команды/наблюдения;
- известный порядок действий для диагностики и ремонта;
- что уже проверено и не помогло;
- критерий успеха;
- когда эскалировать наружу;
- связанные `EPIC/TASK`, решения и файлы.

## Parts (Exact Order)

1. `issue-resolution-log/issue-resolution-log-part-01.md` (`I-001` and later entries)

## Append Rule

- Append new issue/solution entries to `issue-resolution-log/issue-resolution-log-part-01.md`.
- If the latest part grows above ~8,000 characters, create `issue-resolution-log/issue-resolution-log-part-02.md`, update this index, and continue appending there.

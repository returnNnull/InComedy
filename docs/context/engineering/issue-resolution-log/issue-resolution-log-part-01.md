# Журнал проблем и решений — Part 01

## I-001

- Заголовок: `CoreSimulatorService` недоступен, `xcodebuild -showdestinations` показывает только placeholder destinations, targeted iOS XCUITest не может выбрать устройство.
- Категория: iOS simulator / Xcode host environment.
- Впервые зафиксировано: 2026-03-25.
- Последний раз подтверждено: 2026-03-25.
- Затронутые контуры: `iosApp`, `iosAppUITests`, `EPIC-068`, `TASK-073`.
- Статус: open.

### Симптомы

- `xcrun simctl list devices available` завершается с `CoreSimulatorService connection became invalid` и `Failed to initialize simulator device set`.
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -showdestinations` отключает simulator device support и показывает только placeholder destinations:
  - `My Mac`
  - `Any iOS Device`
  - `Any iOS Simulator Device`
- Targeted XCUITest с конкретным simulator destination завершается `exit code 70` / `Unable to find a device matching the provided destination specifier`.

### Подтверждённые команды

```bash
xcrun simctl list devices available
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -showdestinations
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosAppUITests \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' \
  -derivedDataPath /tmp/incomedy-uitest-deriveddata-20260325-1400 \
  -parallel-testing-enabled NO \
  -maximum-parallel-testing-workers 1 \
  -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface \
  test CODE_SIGNING_ALLOWED=NO
```

### Что уже известно

- Repo-side generic iOS build уже стабилизирован:
  - `iosApp/scripts/build-shared.sh` bootstrap-ит repo-local Kotlin/Native bundle;
  - оставшиеся SwiftUI `#Preview` блоки заменены на `PreviewProvider`.
- Проблема находится не в текущем product code, а в host-level simulator/device-set path.

### Рекомендуемый порядок действий

1. Проверить, что ветка и recovery state соответствуют активному `TASK-073`.
2. Сразу подтвердить текущий статус simulator stack:
   - `xcrun simctl list devices available`
   - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -showdestinations`
3. Если `CoreSimulatorService` по-прежнему invalid, чинить именно host-level simulator path:
   - проверять доступность simulator device set;
   - проверять, не деградировал ли Xcode simulator support;
   - после каждой содержательной попытки повторять две команды из шага 2.
4. Только после появления реального simulator destination повторять targeted XCUITest для `testLineupTabShowsApplicationsAndReorderSurface`.
5. Если repair снова не завершён в bounded окне, обновить эту запись:
   - что пробовали;
   - что сработало/не сработало;
   - какой следующий шаг остаётся.

### Что уже проверено и не помогло

- Повторный rerun verification без изменения host environment.
- Повторный `xcodebuild -showdestinations` при уже подтверждённом отказе `CoreSimulatorService`.
- Прямой targeted XCUITest run до появления usable simulator destination.

### Критерий успеха

- `xcrun simctl list devices available` возвращает рабочий список реальных устройств.
- `xcodebuild ... -showdestinations` показывает конкретные simulator destinations, а не только placeholder entries.
- Targeted XCUITest `testLineupTabShowsApplicationsAndReorderSurface` доходит до terminal результата.

### Когда эскалировать

- После исчерпания bounded local repair window без прогресса по `CoreSimulatorService` / usable destinations.
- Если становится ясно, что нужен внешний host/service или действие вне текущей среды.

### Связанные материалы

- `docs/context/handoff/active-run.md`
- `docs/context/engineering/test-strategy.md`
- `docs/context/handoff/task-request-template/task-request-template-part-34.md`
- `docs/context/governance/decisions-log/decisions-log-part-06.md` (`D-072`, `D-073`, `D-075`)

# Журнал проблем и решений — Part 01

## I-001

- Заголовок: `CoreSimulatorService` недоступен, `xcodebuild -showdestinations` показывает только placeholder destinations, targeted iOS XCUITest не может выбрать устройство.
- Категория: iOS simulator / Xcode host environment.
- Впервые зафиксировано: 2026-03-25.
- Последний раз подтверждено: 2026-03-25.
- Затронутые контуры: `iosApp`, `iosAppUITests`, `EPIC-068`, `TASK-073`.
- Статус: resolved.

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
- Для этой симптоматики пользователь отдельно зафиксировал операционную причину первого уровня: часто проблема связана с тем, что Xcode не запущен или завис; поэтому первым repair step должен быть запуск Xcode или его перезапуск.
- Bounded repair run `2026-03-25 14:49-14:52 MSK` подтвердил дополнительные факты:
  - `launchctl print user/$(id -u)/com.apple.CoreSimulator.CoreSimulatorService` показывает сервис в состоянии `running`;
  - `launchctl print system/com.apple.CoreSimulator.simdiskimaged` показывает сервис в состоянии `running`;
  - runtime bundles реально лежат на диске в `/Library/Developer/CoreSimulator/Volumes/iOS_21E213/.../iOS 17.4.simruntime` и `/Library/Developer/CoreSimulator/Volumes/iOS_23C54/.../iOS 26.2.simruntime`;
  - альтернативный device set в `/tmp` не меняет симптоматику;
  - `launchctl kickstart -k user/$(id -u)/com.apple.CoreSimulator.CoreSimulatorService` в текущем executor sandbox запрещён (`Operation not permitted`).
- Повторный rerun `2026-03-25 15:10-15:15 MSK` показал, что симптоматика не была постоянной:
  - `xcrun simctl list devices available` снова вернул реальный список simulator-устройств;
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -showdestinations` снова увидел реальные iOS Simulator destinations;
  - targeted XCUITest `testLineupTabShowsApplicationsAndReorderSurface` на `iPhone 17 Pro (iOS 26.2)` прошёл успешно без дополнительных repo-side code changes.

### Рекомендуемый порядок действий

1. Сначала открыть эту запись и не начинать диагностику с нуля, если симптоматика совпадает.
2. Проверить, что ветка и recovery state соответствуют активному `TASK-073`.
3. Первым делом убедиться, что Xcode запущен и отвечает; если Xcode завис или не открыт, запустить его либо перезапустить.
4. После этого подтвердить текущий статус simulator stack:
   - `xcrun simctl list devices available`
   - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -showdestinations`
5. Если `CoreSimulatorService` по-прежнему invalid, чинить именно host-level simulator path:
   - проверять доступность simulator device set;
   - проверять, не деградировал ли Xcode simulator support;
   - проверять, доступны ли действия по перезапуску/ремонту host-level simulator services в текущей среде;
   - после каждой содержательной попытки повторять две команды из шага 4.
6. Только после появления реального simulator destination повторять targeted XCUITest для `testLineupTabShowsApplicationsAndReorderSurface`.
7. Если repair снова не завершён в bounded окне, обновить эту запись:
   - что пробовали;
   - что сработало/не сработало;
   - какой следующий шаг остаётся.
8. Если выяснилось, что оставшийся repair требует прав/действий вне текущего sandbox, немедленно фиксировать это как true external blocker для данного executor environment вместо повторения тех же команд без изменения host-level условий.

### Что уже проверено и не помогло

- Повторный rerun verification без изменения host environment.
- Повторный `xcodebuild -showdestinations` при уже подтверждённом отказе `CoreSimulatorService`.
- Прямой targeted XCUITest run до появления usable simulator destination.
- Альтернативный device set через `xcrun simctl --set /tmp/...` без восстановления host-level simulator service path.

### Критерий успеха

- `xcrun simctl list devices available` возвращает рабочий список реальных устройств.
- `xcodebuild ... -showdestinations` показывает конкретные simulator destinations, а не только placeholder entries.
- Targeted XCUITest `testLineupTabShowsApplicationsAndReorderSurface` доходит до terminal результата.

### Итог разрешения

- На `2026-03-25 15:15 MSK` критерий успеха выполнен полностью: simulator destinations снова стали доступны, а targeted XCUITest завершился `** TEST SUCCEEDED **`.
- Запись оставлена как resolved memory, потому что этот repair path уже пригодился несколько раз за день и может повторно понадобиться, если host-level simulator stack снова деградирует.

### Когда эскалировать

- После исчерпания bounded local repair window без прогресса по `CoreSimulatorService` / usable destinations.
- Если становится ясно, что нужен внешний host/service или действие вне текущей среды.
- Если `launchctl kickstart` / другие service-control шаги недоступны из текущего executor sandbox и без них repair path дальше не движется.

### Связанные материалы

- `docs/context/handoff/active-run.md`
- `docs/context/engineering/test-strategy.md`
- `docs/context/handoff/task-request-template/task-request-template-part-34.md`
- `docs/context/governance/decisions-log/decisions-log-part-06.md` (`D-072`, `D-073`, `D-075`)

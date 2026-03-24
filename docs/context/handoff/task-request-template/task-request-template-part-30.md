# Task Request Template Part 30

## Formalized Implementation Request (EPIC-067 Shared/Data/Feature Lineup Foundation)

### Epic

- `EPIC-067` — comedian applications and lineup foundation.

### Task

- `TASK-069` — shared/data/feature integration для organizer/comedian applications и lineup surfaces без Android/iOS UI wiring.

### Why This Step

- После `TASK-068` backend foundation уже покрывал submit/review/reorder, но shared/mobile слой еще не имел собственного bounded context-а для работы с comedian applications и lineup.
- Без KMP foundation следующий platform UI step вынудил бы Android/iOS экраны напрямую тянуть backend DTO или дублировать orchestration на каждой платформе.
- Нужен был один безопасный additive шаг с локальным blast radius: подготовить общие контракты, backend adapter, shared ViewModel и bridge, но не начинать platform UI в том же запуске.

### Scope For This Run

- Добавить dedicated `:domain:lineup`, `:data:lineup`, `:feature:lineup` и `shared/lineup`.
- Вынести KMP-модели comedian application / lineup entry и service contract для submit/list/review/reorder.
- Добавить backend adapter, Koin wiring, shared MVI state и Swift-friendly bridge/snapshots.
- Добавить минимально релевантную verification для нового shared/data/feature slice-а и синхронизировать `docs/context/*`.

### Explicitly Out Of Scope

- Android Compose UI wiring
- iOS SwiftUI wiring
- новые backend routes или live-stage semantics
- push notifications и comedian-facing history/list beyond текущий backend contract

### Constraints

- Новый bounded context должен оставаться additive и не менять semantics `D-067`.
- Shared слой не должен зависеть от platform UI.
- Новый и существенно измененный код должен сохранять русские комментарии.
- Верификация должна опираться на локально исполнимые Gradle checks.

### Acceptance Signals

- В кодовой базе есть отдельный KMP bounded context `lineup` в `domain/data/feature/shared`.
- Shared ViewModel умеет обрабатывать comedian submit, organizer review и lineup reorder поверх backend foundation.
- Swift-слой получает stable bridge/snapshot API без прямой работы с backend DTO.
- Automated verification покрывает новый feature slice и KMP compile integration.

### Implementation Outcome

#### Delivered

- Добавлены `:domain:lineup`, `:data:lineup`, `:feature:lineup` и `shared/lineup` с доменными моделями, service contract, backend HTTP adapter, shared MVI state и Swift-friendly bridge/snapshots.
- Общий DI/Koin слой обновлен для нового bounded context-а без вмешательства в Android/iOS UI wiring.
- Контекст-документы синхронизированы: `TASK-069` помечен завершенным, следующий bounded step переведен на `TASK-070`.

#### Verification

- `./gradlew :feature:lineup:allTests`
- `./gradlew :data:lineup:compileKotlinMetadata :shared:compileKotlinMetadata`

#### Remaining Follow-Up

- `TASK-070`: Android/iOS UI wiring и executable coverage для comedian applications и lineup management.
- Позднее отдельно: richer comedian-facing visibility, live-stage semantics и дополнительные organizer editing rules.

## Formalized Implementation Request (EPIC-067 Platform Lineup UI Wiring)

### Epic

- `EPIC-067` — comedian applications and lineup foundation.

### Task

- `TASK-070` — Android/iOS UI wiring и executable platform coverage для comedian submit, organizer review и lineup reorder поверх готового shared foundation.

### Why This Step

- После `TASK-069` общий lineup context уже существовал, но organizer/comedian сценарии не были доступны в platform shells.
- Без platform wiring нельзя было проверить, что shared lineup state собирается в Android Compose и iOS SwiftUI без platform-specific orchestration drift.
- Нужен был один bounded additive шаг: подключить lineup surface в существующие main shells и добавить минимально релевантную executable coverage без расширения backend/public scope.

### Scope For This Run

- Добавить Android wrapper/ViewModel factory и Compose tab для lineup management внутри существующего main shell.
- Добавить iOS `ObservableObject` model и SwiftUI view/tab для lineup management внутри существующего main shell.
- Добавить Android Robolectric coverage и iOS XCUITest smoke target для новой lineup surface.
- Синхронизировать `docs/context/*` по фактическому результату верификации.

### Explicitly Out Of Scope

- новые backend routes, migration или изменение semantics `D-067`
- public comedian discovery/history beyond существующий contract
- live-stage semantics, push/notifications и production rollout hardening
- workaround-изменения в build tooling, требующие внепесочничных разрешений

### Constraints

- Изменение должно оставаться additive и использовать существующий shared/data/feature foundation без дублирования orchestration.
- Новый и существенно измененный код должен сохранять русские комментарии.
- Если локальная iOS verification остается ненадежной, подзадача не может считаться completed и должна завершиться со статусом `partial`.

### Acceptance Signals

- Android main shell показывает отдельный lineup tab с organizer/comedian controls поверх shared lineup state.
- iOS main shell показывает lineup tab с тем же bounded scope через SwiftUI model.
- Android executable coverage проверяет reachability и ключевые controls новой surface.
- Для iOS добавлен адресуемый smoke test, который можно исполнить после восстановления локального build path.

### Implementation Outcome

#### Outcome Status

- `partial`

#### Delivered

- Android: подключены `LineupAndroidViewModel`, `LineupManagementTab`, main-shell navigation/bindings, stable tags и shared test fixtures; добавлен targeted Robolectric coverage для lineup tab и расширен `MainScreenContentTest`.
- iOS: добавлены `LineupScreenModel`, `LineupManagementView`, новый main-shell tab `main.tab.lineup` и targeted XCUITest `testLineupTabShowsApplicationsAndReorderSurface`.
- Контекст-документы переведены в согласованное состояние `partial`, чтобы `EPIC-067` и `TASK-070` оставались активными до надежной iOS verification.

#### Verification

- `./gradlew :feature:lineup:allTests :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' --tests 'com.bam.incomedy.feature.lineup.ui.LineupManagementTabContentTest' :composeApp:compileDebugKotlin`
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO` — blocked by unwritable default `DerivedData` path and `CoreSimulatorService` startup issues inside the automation environment.
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/incomedy-deriveddata build CODE_SIGNING_ALLOWED=NO` — advanced further but failed in the Kotlin framework step because Gradle wrapper access under the sandbox remained restricted.
- `GRADLE_USER_HOME=/tmp/incomedy-gradle-home xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/incomedy-deriveddata build CODE_SIGNING_ALLOWED=NO` — still blocked by Gradle `FileLockContentionHandler` socket creation (`Operation not permitted`) inside the build script, so the targeted XCUITest could not be executed reliably.

#### Remaining Follow-Up

- Продолжить тот же `TASK-070`: восстановить исполнимый local iOS build/XCUITest path для lineup tab и прогнать `testLineupTabShowsApplicationsAndReorderSurface` без смены scope.
- Позднее отдельно: richer comedian-facing visibility/history и live-stage semantics.

#### Verification Re-check (2026-03-23 12:01)

- Повторная проверка не потребовала code changes и была ограничена recovery-проверкой исполнимости текущего iOS build path в automation environment.
- `HOME=/tmp/incomedy-home TMPDIR=/tmp xcrun simctl list devices` подтвердил, что `CoreSimulatorService` в среде недоступен: device set не инициализируется, поэтому XCUITest path остается ненадежным.
- `HOME=/tmp/incomedy-home TMPDIR=/tmp GRADLE_USER_HOME=/tmp/incomedy-gradle-home xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/incomedy-deriveddata build CODE_SIGNING_ALLOWED=NO` убрал прежний blocker на default `DerivedData`, но стабильно уперся в Kotlin framework script: Gradle не может создать `FileLockContentionHandler` socket (`java.net.SocketException: Operation not permitted`).
- Итог: `TASK-070` остается `partial`; следующий bounded step не меняется и по-прежнему сводится к надежной iOS build/XCUITest verification в менее ограниченной среде.

#### Verification Recovery Continuation (2026-03-23 12:18)

- После снятия sandbox-ограничений automation снова продолжила тот же `TASK-070` без выбора новой подзадачи.
- `xcrun simctl list devices` подтвердил, что simulator infrastructure теперь доступен, а iOS verification path больше не блокируется на `CoreSimulatorService` и Gradle socket creation.
- Первый retry `xcodebuild`/XCUITest дал только технический шум: параллельные Xcode build-и конфликтовали по одному `DerivedData` (`build.db` locked), поэтому финальный вывод по качеству кода от него не использовался.
- Чистый sequential retry с targeted `testLineupTabShowsApplicationsAndReorderSurface` выявил реальные Swift compile blockers:
  - `MainGraphView.swift`: три `fixture.map { ... }` ожидали явный аргумент closure в текущем Swift toolchain.
  - `LineupManagementView.swift`: `EventSelectionButtonStyle.Configuration` не поддерживает `isPressed` при `PrimitiveButtonStyle`.
- В этом же запуске blockers были исправлены узкими code changes:
  - в `MainGraphView.swift` для `Optional.map` добавлены явные `_ in` closure arguments;
  - `EventSelectionButtonStyle` переведен с `PrimitiveButtonStyle` на `ButtonStyle`, чтобы сохранить ожидаемое pressed-state поведение без compile error.
- Повторный targeted XCUITest после этих fixes прошел через Kotlin framework build, Swift compile, app packaging и test-runner packaging значительно дальше прежнего, но не вернул terminal pass/fail outcome в пределах текущего run window.
- Итог: `TASK-070` остается `partial`; следующий bounded step сузился до одного действия — повторить тот же targeted lineup XCUITest на уже исправленном iOS compile path и дождаться terminal результата.

#### Verification Re-check (2026-03-23 14:04)

- Повторная проверка в этом запуске не меняла продуктовый код и была ограничена только reliability-проверкой существующего iOS verification path.
- `xcrun simctl list devices available` и `xcodebuild -list -project iosApp/iosApp.xcodeproj` в дефолтном окружении снова показали `CoreSimulatorService connection became invalid`, поэтому simulator runtime в automation environment остается нестабильным.
- `HOME=/tmp/incomedy-home-run TMPDIR=/tmp GRADLE_USER_HOME=/tmp/incomedy-gradle-home-run xcodebuild ... build` без локального cache уперся в попытку wrapper скачать `gradle-9.2.1`, что в этой среде недоступно.
- Копирование уже существующего `~/.gradle/wrapper/dists` в `/tmp/incomedy-gradle-home-run4` убрало network/download blocker и позволило offline bootstrap Gradle 9.2.1, но не решило главный sandbox blocker: и `xcodebuild ... build`, и прямой `GRADLE_USER_HOME=/tmp/incomedy-gradle-home-run4 ./gradlew help` падают на `Could not create service of type FileLockContentionHandler ... java.net.SocketException: Operation not permitted`.
- Итог: `TASK-070` остается `partial`; следующий bounded step снова расширять нельзя, он по-прежнему сводится к одному действию — повторить targeted lineup XCUITest только в среде, где одновременно доступны `CoreSimulatorService` и Gradle lock-listener sockets.

#### Verification Re-check (2026-03-23 16:01)

- Повторная проверка в этом запуске снова не меняла продуктовый код и была ограничена только минимальными prerequisite-check командами.
- `xcrun simctl list devices available` еще раз подтвердил, что `CoreSimulatorService` в текущем automation environment недоступен: simulator device set не инициализируется.
- `xcodebuild -list -project iosApp/iosApp.xcodeproj` по-прежнему читает project metadata, но одновременно сообщает `Simulator device support disabled`, поэтому само наличие проекта/схем не считается надежной readiness-проверкой для XCUITest path.
- `GRADLE_USER_HOME=/tmp/incomedy-gradle-home-run5 ./gradlew help` с fresh writable cache не дошел до Kotlin build script: пустой cache сначала вызвал wrapper bootstrap, который офлайн упал на `UnknownHostException: services.gradle.org`.
- Итог: `TASK-070` остается `partial`; следующий bounded step снова не меняется и сводится к одному действию — повторить targeted lineup XCUITest только в среде, где `CoreSimulatorService` доступен, а writable Gradle home уже содержит usable wrapper/runtime и не требует сети.

#### Verification Re-check (2026-03-23 18:01)

- Повторная проверка в этом запуске снова не меняла продуктовый код и была ограничена только одной безопасной подзадачей: минимальным re-check readiness для iOS verification path.
- `HOME=/tmp/incomedy-home-check TMPDIR=/tmp xcrun simctl list devices available` еще раз завершился `CoreSimulatorService connection became invalid`, поэтому simulator runtime в текущем automation environment по-прежнему не считается пригодным для XCUITest.
- `HOME=/tmp/incomedy-home-check TMPDIR=/tmp xcodebuild -list -project iosApp/iosApp.xcodeproj` снова прочитал project metadata, но одновременно сообщил `Simulator device support disabled`, так что этот сигнал не меняет blocker status.
- `GRADLE_USER_HOME=/tmp/incomedy-gradle-home-check ./gradlew help` с новым writable cache опять не дошел до Gradle runtime startup: пустой cache вызвал wrapper bootstrap, который офлайн упал на `UnknownHostException: services.gradle.org`.
- Итог: `TASK-070` остается `partial`; следующий bounded step не меняется и по-прежнему сводится к одному действию — повторить targeted lineup XCUITest только в среде, где одновременно доступен healthy `CoreSimulatorService` и writable Gradle home уже содержит usable wrapper/runtime без сетевой загрузки.

#### Recovery Sync Stop (2026-03-23 20:01)

- В этом запуске automation не делала новую verification-попытку и не меняла продуктовый код: подзадача была ограничена обязательным context sync и решением, безопасно ли продолжать тот же iOS path в текущей среде.
- `active-run.md`, текущая ветка и dirty worktree были согласованы; recovery checkpoint по-прежнему показывает тот же blocker-set, поэтому повторять `xcodebuild` / XCUITest без смены среды было бы ненадежно и не соответствовало правилу bounded step.
- Итог: `TASK-070` остается `partial`; текущий запуск завершен как docs-only blocker sync, а следующая ровно одна подзадача не меняется — повторить `testLineupTabShowsApplicationsAndReorderSurface` только после восстановления healthy `CoreSimulatorService` и writable Gradle home с уже доступным wrapper/runtime.

#### Recovery Sync Stop (2026-03-23 22:01)

- В этом запуске automation снова не делала новую verification-попытку и не меняла продуктовый код: шаг был ограничен обязательным handoff sync, сверкой recovery state с текущей веткой/worktree и проверкой, не достигнут ли лимит подзадач цикла.
- Состояние осталось консистентным: `EPIC-067` / `TASK-070` все еще активны в ветке `codex/epic-067-comedian-applications-foundation`, completed-подзадач в цикле по-прежнему только три (`TASK-067`..`TASK-069`), а последний зафиксированный blocker-set для iOS verification не изменился.
- Итог: `TASK-070` остается `partial`; текущий запуск завершен как docs-only blocker sync без повторного запуска ненадежных команд, а следующая ровно одна подзадача не меняется — повторить `testLineupTabShowsApplicationsAndReorderSurface` только после восстановления healthy `CoreSimulatorService` и writable Gradle home с уже доступным wrapper/runtime.

#### Verification Root-Cause Re-check (2026-03-23 22:17)

- В этом запуске automation перешла от docs-only sync к прямой диагностике ошибки по запросу пользователя и не меняла продуктовый код, пока не будет понятен настоящий источник сбоя.
- Старый blocker-set частично снят: `xcrun simctl list devices available` и `xcodebuild -list -project iosApp/iosApp.xcodeproj` снова проходят, а `./gradlew --version` подтверждает, что с обычным `~/.gradle` wrapper/runtime доступен и прежний offline-bootstrap blocker больше не основной.
- Реальный текущий blocker сместился ниже: targeted `xcodebuild ... test` для `testLineupTabShowsApplicationsAndReorderSurface` зависает до terminal result, при этом stale concurrent `xcodebuild` процессы дополнительно загрязняют среду; после их остановки и даже после restart `CoreSimulatorService` + clean boot iOS 26.2 simulator базовые `simctl` команды (`bootstatus`, `listapps`) восстанавливаются, но `simctl install` / `simctl launch` для `InComedy` все еще подвисают до появления app process / test runner.
- Итог: `TASK-070` остается `partial`; следующий bounded step сузился до одного environment-level действия — стабилизировать simulator-side app install/launch orchestration на одном clean iOS 26.2 device, и только после этого снова запускать тот же targeted lineup XCUITest.

#### LaunchServices Root-Cause Re-check (2026-03-23 22:24)

- В этом запуске automation продолжила тот же `TASK-070` без product-code changes и сузила цель еще сильнее: проверить, может ли полностью erased и clean-booted iOS 26.2 simulator корректно зарегистрировать и запустить уже собранный `InComedy.app`.
- `shutdown all -> erase -> boot -> bootstatus -b` для `iPhone 16e (26.2)` отработали до terminal `Finished`; boot не застрял навсегда и прошел через `00LaunchServicesMigrator`, keychain/profile migrator-ы и `Waiting on System App`.
- Несмотря на clean state, `simctl install`, `simctl launch`, и `simctl listapps | rg com.bam.incomedy.InComedy` вокруг `InComedy` все еще подвисают. Решающий device log теперь точнее предыдущих symptom-ов: `CoreSimulatorBridge` начинает launch sequence для `com.bam.incomedy.InComedy`, но сразу пишет `Failed to get LSBundleProxy for 'com.bam.incomedy.InComedy'`.
- Это означает, что текущий blocker не в lineup SwiftUI code и не в старых simulator/Gradle prerequisites, а в том, что LaunchServices на clean iOS 26.2 simulator не видит/не завершает регистрацию собранного `InComedy.app`, поэтому app process и XCUITest runner так и не стартуют.
- Итог: `TASK-070` остается `partial`; следующий bounded step не меняет scope, но становится конкретнее — расследовать причину отсутствия `LSBundleProxy`/LaunchServices registration для `InComedy` на clean simulator, и только после этого повторять targeted lineup XCUITest.

#### Writable Path Root-Cause Re-check (2026-03-24 00:04)

- В этом запуске automation не меняла продуктовый код и ограничилась одной безопасной подзадачей: отделить repository/product config от локальных sandbox/orchestration blocker-ов в iOS build path.
- Проверка project config не выявила нового drift-а: `PRODUCT_BUNDLE_IDENTIFIER=com.bam.incomedy.InComedy`, `iosApp/Info.plist`, entitlements и lineup-related Swift changes выглядят консистентно с ожидаемым target packaging.
- Default `xcodebuild` path в sandbox по-прежнему ненадежен из-за unwritable `~/Library/Developer/Xcode/DerivedData`, но writable `/tmp` `HOME` + `TMPDIR` + `-derivedDataPath` уже снимают этот blocker и доводят сборку до `Compile Kotlin Framework`.
- Дополнительный шаг с preseeded `/tmp` `GRADLE_USER_HOME`, в который скопирован локальный `gradle-9.2.1` wrapper cache, снимает и старый `.gradle` lock blocker на `gradle-9.2.1-bin.zip.lck`: `xcodebuild` и прямой `./gradlew :shared:embedAndSignAppleFrameworkForXcode --stacktrace --info` стартуют daemon/toolchain и заходят глубже в pipeline без мгновенного permission failure.
- Несмотря на это, trustworthy terminal outcome все еще не получен: `HOME=/tmp/incomedy-home-run6 TMPDIR=/tmp xcrun simctl list devices available` снова падает на `CoreSimulatorService connection became invalid`, а в `/tmp/incomedy-derived-run7/Build/Products/Debug-iphonesimulator/InComedy.app` создается только пустой каталог. Это согласуется с уже зафиксированным `Failed to get LSBundleProxy for 'com.bam.incomedy.InComedy'`: bundle path существует, но packaging/install path не доходит до LaunchServices-visible состояния.
- Итог: `TASK-070` остается `partial`; следующий bounded step не расширяется и теперь конкретно сводится к повтору iOS verification только через writable `/tmp`-based setup с уже preseeded Gradle cache, после восстановления healthy `CoreSimulatorService` и доведения `InComedy.app` до non-empty / installable состояния перед повтором targeted lineup XCUITest.

#### Writable Path Cache Completeness Re-check (2026-03-24 10:35)

- В этом запуске automation снова не меняла продуктовый код и ограничилась одной verification-подзадачей: еще раз повторить writable `/tmp`-based iOS build path и зафиксировать, появляется ли хотя бы non-empty `InComedy.app`.
- Повторная проверка project config (`Config.xcconfig`, `project.pbxproj`, `Info.plist`, entitlements) не выявила нового drift-а в `PRODUCT_NAME`, `PRODUCT_BUNDLE_IDENTIFIER`, `Info.plist` или entitlements; repository-side packaging settings остаются консистентными.
- `HOME=/tmp/incomedy-home-run8 TMPDIR=/tmp xcrun simctl list devices available` снова сразу упал на `CoreSimulatorService connection became invalid`, так что simulator runtime в начале этого run по-прежнему не был пригоден для install/test stage.
- `HOME=/tmp/incomedy-home-run8 TMPDIR=/tmp GRADLE_USER_HOME=/tmp/incomedy-gradle-home-run8 xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/incomedy-derived-run8 build CODE_SIGNING_ALLOWED=NO` снова дошел до `PhaseScriptExecution Compile Kotlin Framework`, но не вернул trustworthy terminal result; `find /tmp/incomedy-derived-run8/Build/Products/Debug-iphonesimulator/InComedy.app -maxdepth 3 -ls` показал только пустой каталог `InComedy.app`.
- Новый конкретный signal этого run: copied `wrapper` + `native` cache для fresh `/tmp` `GRADLE_USER_HOME` недостаточны. В `/tmp/incomedy-gradle-home-run8/jdks/` появился `jbrsdk_jcef-21-JetBrains-21.0.10-osx-aarch64-b1163.108.tar.gz.part`, тогда как в обычном `~/.gradle/jdks` уже есть provisioned JetBrains 21 runtime (`jetbrains_s_r_o_-21-aarch64-os_x.2`). Значит, следующий bounded retry должен копировать в tmp home не только `wrapper` и `native`, но и `jdks`, иначе writable setup сам запускает новый toolchain/JDK provisioning path.
- Итог: `TASK-070` остается `partial`; следующий bounded step уточнен без расширения scope — повторить writable iOS build/test path только после healthy `CoreSimulatorService` и полного tmp cache preseed (`wrapper` + `native` + `jdks`), затем снова проверять non-empty/installable `InComedy.app` перед targeted lineup XCUITest.

#### Writable Path Full Cache Preseed Re-check (2026-03-24 12:04)

- В этом запуске automation снова не меняла продуктовый код и ограничилась одной verification-подзадачей: проверить, меняет ли полный tmp cache preseed (`wrapper` + `native` + `jdks`) outcome writable iOS build path.
- Подтверждено, что локальный `~/.gradle/jdks` уже содержит provisioned JetBrains 21 runtime и другие нужные toolchain artifacts, после чего был создан fresh writable setup `run9` c copied `wrapper`, `native` и `jdks`.
- `xcrun simctl list devices available` перед build все еще сразу падает на `CoreSimulatorService connection became invalid`, так что simulator runtime в начале run по-прежнему непригоден для install/test stage.
- `HOME=/tmp/incomedy-home-run9 TMPDIR=/tmp GRADLE_USER_HOME=/tmp/incomedy-gradle-home-run9 xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/incomedy-derived-run9 build CODE_SIGNING_ALLOWED=NO` снова дошел до `PhaseScriptExecution Compile Kotlin Framework`, но так и не вернул trustworthy terminal result; `find /tmp/incomedy-derived-run9/Build/Products/Debug-iphonesimulator/InComedy.app -maxdepth 3 -ls` показал тот же пустой каталог `InComedy.app`.
- Новый положительный signal этого run: copied `jdks` действительно снимают cache-completeness drift из `run8`. В `/tmp/incomedy-gradle-home-run9/jdks/` не появился новый `.part`; остались только ожидаемые `.lock` файлы рядом с уже скопированными runtime archives/directories.
- Итог: `TASK-070` остается `partial`; следующий bounded step снова сужен без расширения scope — считать tmp cache preseed подтвержденным и дальше работать только с recovery `CoreSimulatorService` / simulator-side install visibility, после чего еще раз проверять non-empty/installable `InComedy.app` перед targeted lineup XCUITest.

#### Bridge Fix + Install/Launch Recovery Re-check (2026-03-24 13:31)

- В этом запуске automation перешла от чистой environment-диагностики к минимальному repo-side fix для iOS verification path, не меняя feature code и не расширяя scope beyond `TASK-070`.
- После hard reset `Simulator` / `CoreSimulatorService` / `simdiskimaged` снова заработали `xcrun simctl list devices`, `erase`, `boot`, и `bootstatus -b` для `iPhone 17 Pro (26.2)`, а затем `simctl install`, `listapps` и `launch` подтвердили, что `com.bam.incomedy.InComedy` снова проходит LaunchServices registration и стартует на simulator.
- Найден и исправлен репозиторный build bridge: `iosApp/scripts/build-shared.sh` теперь по умолчанию использует persistent repo-local `GRADLE_USER_HOME=.gradle/xcode` и запускает `./gradlew --no-daemon --console=plain :shared:embedAndSignAppleFrameworkForXcode`. Это убирает зависимость Xcode script phase от fresh tmp-only cache и lingering external daemons.
- После preseed `.gradle/xcode` из локального `~/.gradle` targeted `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test CODE_SIGNING_ALLOWED=NO` уже не застревает на `Compile Kotlin Framework`: Gradle внутри script phase сообщает `BUILD SUCCESSFUL`, а Xcode продолжает linking/packaging `InComedy.app` и `iosAppUITests.xctest`.
- Новый оставшийся blocker теперь уже уже и чище: terminal test result все еще не получен, потому что во время активного XCUITest execution simulator session теряется; параллельный `simctl spawn ... launchctl print system` возвращает `device is not booted / Bad or unknown session`, хотя build/install path уже healthy.
- Итог: `TASK-070` остается `partial`, но старая проблема исправлена частично и существенно сужена. Следующий bounded step — не менять feature code, а добить стабильность simulator session во время targeted lineup XCUITest поверх уже исправленного build bridge.

#### Runtime-Only Re-check After D-068 (2026-03-24 13:39)

- В этом запуске automation сначала зафиксировала новое governance-правило `D-068`: verification/test-runtime issues для активной задачи не выносятся в отдельный blocker/task по умолчанию, а должны дожиматься внутри той же подзадачи.
- После этого automation продолжила тот же `TASK-070` без смены scope и убрала rebuild noise из repro path: `DerivedData` уже содержит `InComedy.app` и `iosAppUITests-Runner.app`, поэтому следующий retry шел через `xcodebuild ... test-without-building`.
- `xcrun simctl boot 5EC0AE38-9521-40C0-B43F-874924578A0F` + `bootstatus -b` снова довели `iPhone 17 Pro (26.2)` до terminal `Finished`, после чего `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test-without-building CODE_SIGNING_ALLOWED=NO` стартовал без повторного KMP build path.
- Даже в этом narrowed runtime-only path итог тот же: `xcodebuild` и `testmanagerd` остаются живы, но `xcrun simctl list devices` снова показывает target device в `Shutdown`, а `simctl spawn ... launchctl print system` возвращает `device is not booted / Bad or unknown session`.
- Итог: `TASK-070` остается `partial`; теперь подтверждено, что remaining blocker не в build bridge и не в packaging/install stage, а в simulator-session lifecycle во время targeted XCUITest runtime.

#### Second Device Runtime Re-check (2026-03-24 13:43)

- В этом запуске automation продолжила тот же `TASK-070` и не меняла feature code; цель была уже совсем узкой: проверить, привязан ли remaining simulator-session drop к одному конкретному iOS 26.2 device profile.
- Для этого тот же runtime-only path (`test-without-building`) был повторен не на `iPhone 17 Pro`, а на `iPhone 16e (26.2)`: `shutdown all -> erase -> boot -> bootstatus -b` довели девайс до terminal `Finished`.
- Затем `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=C6E6DAA6-382F-4F78-9B79-7E397E1EA02B' -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test-without-building CODE_SIGNING_ALLOWED=NO` снова стартовал без rebuild stage.
- Итог повторился почти идентично: `xcodebuild` и `testmanagerd` остаются активны, но `xcrun simctl list devices` снова показывает target device в `Shutdown` до terminal XCUITest result.
- Итог: `TASK-070` остается `partial`; теперь подтверждено, что remaining blocker не привязан к одному simulator profile, а воспроизводится как минимум на двух iOS 26.2 devices (`iPhone 17 Pro` и `iPhone 16e`).

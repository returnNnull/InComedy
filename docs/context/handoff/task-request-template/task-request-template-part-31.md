# Task Request Template Part 31

## EPIC-067 Review-Ready Completion

### Epic

- `EPIC-067` — comedian applications and lineup foundation.

### Task

- `TASK-070` — Android/iOS UI wiring и executable platform coverage для comedian submit, organizer review и lineup reorder поверх готового shared foundation.

### Completion Outcome

#### Outcome Status

- `completed`

#### Delivered

- Android surface остается delivered и green: отдельный lineup tab, `LineupManagementTab`, `LineupAndroidViewModel`, main-shell wiring, shared fixtures и targeted Robolectric coverage.
- iOS surface delivered end-to-end: `LineupScreenModel`, `LineupManagementView`, новый main-shell tab, targeted XCUITest `testLineupTabShowsApplicationsAndReorderSurface`, plus earlier Swift compile fixes in `MainGraphView.swift`.
- Repo-side Xcode/KMP bridge stabilized in `iosApp/scripts/build-shared.sh`: Xcode script phase reuses repo-local `.gradle/xcode` and launches `./gradlew --no-daemon --console=plain`, which removes the old cold-cache / empty-bundle build blocker.
- Final runtime fixes for trustworthy iOS verification:
  - `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosAppUITests.xcscheme` disables parallelizable cloned-device execution so targeted UI tests stay on the already booted primary simulator instead of the unstable Xcode clone path.
  - `LineupManagementView.swift` preserves child accessibility identifiers inside application/entry cards with `.accessibilityElement(children: .contain)`, so XCUITest can address `lineup.application.status.*`, `lineup.entry.moveDown.*`, and `lineup.entry.order.*` directly.
  - `LineupScreenModel.swift` explicitly reassigns fixture arrays to guarantee SwiftUI publishes review/reorder updates in fixture mode.

#### Root Cause Summary

- The last remaining failure was no longer product logic or app packaging.
- Xcode UI-test runtime still used a cloned simulator path that died before a terminal result, and even when the test reached real UI logic, card-level accessibility identifiers were collapsing child identifiers so the test could not see updated status/order elements.
- Disabling clone-style parallel execution plus preserving child accessibility identifiers removed the final blockers.

#### Verification

- `Previously passed: ./gradlew :feature:lineup:allTests :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' --tests 'com.bam.incomedy.feature.lineup.ui.LineupManagementTabContentTest' :composeApp:compileDebugKotlin`
- `Passed: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'id=5EC0AE38-9521-40C0-B43F-874924578A0F' -only-testing:iosAppUITests/iosAppUITests/testLineupTabShowsApplicationsAndReorderSurface test CODE_SIGNING_ALLOWED=NO`

#### Review Summary

- `EPIC-067` now covers the full intended bounded slice: backend comedian applications foundation, organizer review -> lineup materialization, shared/data/feature client wiring, Android lineup shell, iOS lineup shell, and executable verification on both platforms.
- No new epic may start automatically from automation after this point; the branch stays on `codex/epic-067-comedian-applications-foundation` until explicit user review/confirmation.

#### Next

- `Ровно одна следующая подзадача: user review EPIC-067 / TASK-070 и явное confirmation, что epic можно перевести из awaiting_user_review в done.`

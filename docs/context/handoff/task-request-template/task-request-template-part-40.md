# Task Request Template Part 40

## Implementation Outcome (EPIC-070 TASK-089 Android/iOS Donations Surface)

### Epic

- `EPIC-070` — donations/payout foundation.

### Task

- `TASK-089` — Android/iOS donation и comedian payout surfaces с executable verification для delivered foundation без выбора внешнего PSP.

### Status

- `completed`

### Delivered

- Добавлен новый shared `:feature:donations` модуль с `DonationsState`, `DonationsViewModel` и common tests для donation history и comedian payout profile surface.
- `shared` теперь экспортирует `DonationsBridge` и snapshot-модели для Swift-слоя, а `InComedyKoin` регистрирует общий `DonationsViewModel`.
- Android main shell получил вкладку `Донаты` с `DonationHubTab`, `DonationsAndroidViewModel`, payout-profile формой и sent/received donation history.
- iOS main shell получил `DonationHubView` и `DonationHubModel`; SwiftUI форма payout profile теперь синхронизируется по `updatedAtIso`, чтобы backend-normalized update с тем же `id` не оставлял stale beneficiary значение в поле.
- Scope остаётся provider-agnostic/manual-settlement-ready и по-прежнему не включает external checkout, webhook ingestion, operator verification workflow или payout automation.

### Verification

- `Passed: ./gradlew :feature:donations:allTests :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.donations.ui.DonationHubTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' :composeApp:compileDebugKotlin`
- `Passed: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testDonationTabShowsPayoutAndHistorySurface test`

### Review-Driven Follow-Up

- После commit `1059945` найден и устранён donations/session lifecycle regression: `DonationsViewModel` теперь сбрасывает весь `DonationsState`, если активная сессия пропала, чтобы sent/received history и payout profile не оставались в process-lived памяти после logout или потери access token.
- `DonationsAndroidViewModel` больше не инициирует eager refresh в `init`; Android donations tab использует уже существующий session-driven `LaunchedEffect(accessToken)` внутри `DonationHubTab`.
- `DonationHubModel` больше не делает eager refresh при создании, а `MainGraphView` запускает `donationModel.refresh()` только когда `isAuthorized == true` и `isLoadingContext == false`, чтобы initial iOS donations load не опережал готовность session context.
- Добавлены regression checks для shared state reset и Android session-bootstrap refresh path; iOS путь повторно подтверждён через targeted donations XCUITest.

### Follow-Up Verification

- `Passed: ./gradlew :feature:donations:allTests`
- `Passed: ./gradlew :composeApp:compileDebugKotlin :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.donations.ui.DonationHubTabContentTest.refreshesWhenAccessTokenBecomesAvailable'`
- `Passed: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testDonationTabShowsPayoutAndHistorySurface test`

### Notes

- Security review verdict: базовый client/shared slice и review-driven follow-up остаются внутри authenticated donation history и comedian payout-profile UI поверх уже существующего provider-agnostic backend surface; provider secrets, checkout activation, webhook ingestion, payout execution и implicit reuse ticketing PSP credentials не добавлялись.
- `R-005` остаётся open: platform UX уже доставлен, но legal/financial scheme, operator verification workflow и explicit donation/payout provider choice всё ещё не подтверждены.

### User Confirmation Outcome

- Пользователь явно подтвердил review и запросил merge/push finished branch.
- `EPIC-070` переведён из `awaiting_user_review` в `done`; reopen допустим только для post-merge regression или нового explicit follow-up request.

### Next

- `После merge/push считать следующим future candidate EPIC-071 из next-epic-queue, но не открывать новый epic автоматически без отдельного user request.`

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

### Notes

- Security review verdict: новый client/shared slice ограничен authenticated donation history и comedian payout-profile UI поверх уже существующего provider-agnostic backend surface; provider secrets, checkout activation, webhook ingestion, payout execution и implicit reuse ticketing PSP credentials не добавлялись.
- `R-005` остаётся open: platform UX уже доставлен, но legal/financial scheme, operator verification workflow и explicit donation/payout provider choice всё ещё не подтверждены.

### Review Boundary

- `EPIC-070` переведён в `awaiting_user_review`; новый implementation epic до explicit user confirmation не открывать.

### Next

- `Ровно один следующий шаг: получить explicit user review confirmation по EPIC-070.`

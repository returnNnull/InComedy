# Verification Memory Part 02

## Recent Verification Outcomes

- `EPIC-070 TASK-087` backend donations/payout foundation relies on `./gradlew --no-build-cache :domain:donations:compileKotlinJvm :server:compileKotlin`, which passed on `2026-03-25 18:18 MSK`.
- `EPIC-070 TASK-087` server verification relies on `./gradlew --no-build-cache :server:test --tests 'com.bam.incomedy.server.donations.DonationRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'`, which passed on `2026-03-25 18:19-18:22 MSK`.
- `EPIC-070 TASK-088` shared/data donations transport verification relies on `./gradlew --no-build-cache :data:donations:allTests`, which passed on `2026-03-25 20:05-20:06 MSK`.
- `EPIC-070 TASK-088` shared DI integration verification relies on `./gradlew --no-build-cache :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64`, which passed on `2026-03-25 20:05-20:06 MSK`.
- `EPIC-070 TASK-089` Android/shared donations surface verification relies on `./gradlew :feature:donations:allTests :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.donations.ui.DonationHubTabContentTest' --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest' :composeApp:compileDebugKotlin`, which passed on `2026-03-26 02:24-02:25 MSK`.
- `EPIC-070 TASK-089` targeted iOS donations verification relies on `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testDonationTabShowsPayoutAndHistorySurface test`, which passed on `2026-03-26 03:41-04:26 MSK`.

# Verification Memory Part 02

## Recent Verification Outcomes

- `EPIC-070 TASK-087` backend donations/payout foundation relies on `./gradlew --no-build-cache :domain:donations:compileKotlinJvm :server:compileKotlin`, which passed on `2026-03-25 18:18 MSK`.
- `EPIC-070 TASK-087` server verification relies on `./gradlew --no-build-cache :server:test --tests 'com.bam.incomedy.server.donations.DonationRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'`, which passed on `2026-03-25 18:19-18:22 MSK`.
- `EPIC-070 TASK-088` shared/data donations transport verification relies on `./gradlew --no-build-cache :data:donations:allTests`, which passed on `2026-03-25 20:05-20:06 MSK`.
- `EPIC-070 TASK-088` shared DI integration verification relies on `./gradlew --no-build-cache :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64`, which passed on `2026-03-25 20:05-20:06 MSK`.
- `EPIC-070 TASK-089` Android/shared donations surface verification relies on `./gradlew --no-build-cache :feature:donations:allTests :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64`, which passed on `2026-03-26 04:26 MSK`.
- `EPIC-070 TASK-089` targeted iOS donations verification relies on `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,id=48100E42-0C0F-4794-9570-4DA5185BAB28' -only-testing:iosAppUITests/iosAppUITests/testDonationTabShowsPayoutAndHistorySurface test`, which passed on `2026-03-26 02:58-03:15 MSK`.

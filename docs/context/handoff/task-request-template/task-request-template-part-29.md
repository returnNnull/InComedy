# Task Request Template Part 29

## Formalized Implementation Request (Shared/Mobile Ticket Wallet + Checker Scan Surfaces)

## Why This Step

- `D-066` had already redirected the active ticketing path toward provider-agnostic order/ticket/check-in delivery, and the backend/shared contracts for issued tickets plus QR/check-in were already in place.
- Without buyer/staff mobile surfaces, the MVP ticketing path was still incomplete for real user flows even though the server foundation already existed.
- This increment still needed to stay provider-agnostic and avoid implying any external PSP choice.

## Scope

- Add shared ticketing presentation state for ticket loading, error/result handling, and checker scan commands.
- Wire Android Compose and iOS SwiftUI `Билеты` tab surfaces for `My Tickets`, QR presentation, and checker scan UX.
- Add executable Android/iOS UI coverage and synchronize `docs/context/*` in the same change.

## Explicitly Out Of Scope

- selecting or activating a concrete external PSP
- complimentary tickets, refund/cancel lifecycle, or `sold_out` automation
- offline scanner buffering, attendance analytics, or wallet-pass export

## Constraints

- Existing provider-specific code does not count as provider selection; the ticketing client slice must remain provider-agnostic.
- New and materially changed repository code must keep Russian comments.
- UI automation should use stable identifiers/test tags rather than brittle text-only selectors.
- Active context docs must be updated in the same change when the next bounded step moves.

## Acceptance Signals

- Android and iOS main shells expose the `Билеты` tab with issued tickets.
- Buyer can reveal a QR representation for an issued ticket.
- Staff can submit a QR payload and observe a deterministic check-in result.
- Automated verification covers the new shared/mobile ticketing slice.

## Implementation Outcome

## Delivered

- Added shared `:feature:ticketing` MVI slice plus shared/iOS bridge wiring for ticket loading and checker scan actions.
- Added Android Compose `Билеты` tab UI with ticket wallet, QR rendering, checker scan form, and main-shell wiring.
- Added iOS SwiftUI `TicketWalletView` / `TicketWalletModel`, fixture-backed scan behavior, and fixed SwiftUI accessibility identifier collisions so child controls retain stable ids for XCUITest.
- Updated active context docs so the ticketing client surfaces are marked delivered and the next bounded `P0` step moves to comedian applications + lineup ordering.

## Verification

- `./gradlew :feature:ticketing:allTests :shared:compileKotlinMetadata :composeApp:compileDebugKotlin`
- `./gradlew :composeApp:testDebugUnitTest --tests 'com.bam.incomedy.feature.main.ui.MainScreenContentTest'`
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2' -only-testing:iosAppUITests/iosAppUITests/testTicketTabShowsWalletAndCheckInSurface test CODE_SIGNING_ALLOWED=NO`

## Remaining Follow-Up

- Implement comedian applications plus organizer approve/reject/waitlist and lineup ordering as the next bounded `P0` slice.
- Return to concrete PSP selection/activation only in the final pre-publication stage after explicit user confirmation.
- Keep later ticketing follow-ups separate: complimentary issuance, refund/cancel, `sold_out` automation, wallet pass, and offline-tolerant checker tooling.

# Task Request Template Part 39

## Implementation Outcome (EPIC-070 TASK-088 Shared/Data Donations Transport)

### Epic

- `EPIC-070` — donations/payout foundation.

### Task

- `TASK-088` — shared/data donation service contract и transport integration для payout profile, donation history и intent creation без platform UI.

### Status

- `completed`

### Delivered

- Добавлен новый `:data:donations` модуль с backend Ktor transport для `GET/PUT /api/v1/comedian/me/payout-profile`, `GET /api/v1/me/donations`, `GET /api/v1/comedian/me/donations` и `POST /api/v1/events/{eventId}/donations`.
- `DonationBackendApi` изолирует transport DTO и wire contract, а `BackendDonationService` закрывает доменный `DonationService` для payout profile, donation history и donation intent creation.
- `shared` Koin wiring теперь включает `donationsDataModule`, поэтому следующий bounded шаг может строить Android/iOS surfaces поверх уже подключенного shared/data foundation без дублирования transport логики.
- Добавлены common tests для DTO/domain mapping нового transport слоя; delivery остаётся provider-agnostic/manual-settlement-ready и по-прежнему не включает external checkout, webhook ingestion, operator verification workflow или payout automation.

### Verification

- `Passed: ./gradlew --no-build-cache :data:donations:allTests`
- `Passed: ./gradlew --no-build-cache :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64`

### Notes

- Security review verdict: новый shared/data slice добавляет только authenticated KMP transport и DI wiring над уже существующим provider-agnostic backend surface без provider secrets, checkout activation, webhook ingestion или implicit reuse ticketing PSP credentials.
- `R-005` остаётся open: legal/financial scheme, operator verification workflow, platform UX и explicit donation/payout provider choice всё ещё не подтверждены.

### Next

- `Ровно одна следующая подзадача: TASK-089 — Android/iOS donation и comedian payout surfaces с executable verification для delivered foundation.`

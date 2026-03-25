# Task Request Template Part 38

## Implementation Outcome (EPIC-070 TASK-087 Backend Donations Foundation)

### Epic

- `EPIC-070` — donations/payout foundation.

### Task

- `TASK-087` — backend foundation для comedian payout profile и donation intents с verified-payout gate, protected self-service routes и server coverage.

### Status

- `completed`

### Delivered

- Добавлен новый `:domain:donations` модуль с payout legal/verification моделями, donation intent status и provider-agnostic service seam для дальнейшего shared/data wiring.
- На backend-е добавлены миграция `V15__donation_payout_foundation.sql`, `DonationRepository`/`PostgresDonationRepository` и persistence foundation для `comedian_payout_profiles` и `donation_intents`.
- Зарегистрированы защищённые routes `GET/PUT /api/v1/comedian/me/payout-profile`, `GET /api/v1/me/donations`, `GET /api/v1/comedian/me/donations` и `POST /api/v1/events/{eventId}/donations` с auth, rate limiting и diagnostics hooks.
- Donation creation now enforces comedian verified payout profile, published/public event gating, lineup membership, donor idempotency, currency match, positive amount и запрет self-donation.
- Первый slice остаётся provider-agnostic/manual-settlement-only: payout profile хранит `manual_settlement`, а donation intent response возвращает `checkout_available=false` без external checkout, webhook processing или payout automation.

### Verification

- `Passed: ./gradlew --no-build-cache :domain:donations:compileKotlinJvm :server:compileKotlin`
- `Passed: ./gradlew --no-build-cache :server:test --tests 'com.bam.incomedy.server.donations.DonationRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'`

### Notes

- Active docs cleanup завершён: живые handoff/governance ссылки синхронизированы с текущей семантикой счётчика запусков, а `run_slots_used_in_cycle` закреплён только как launch counter.
- Security review verdict: delivered slice добавляет только authenticated payout-profile и donation-intent surface с provider-agnostic/manual-settlement persistence; в этом шаге не появлялись provider secrets, payout execution, webhook ingestion или reuse ticket checkout credentials.
- `R-005` остаётся open: verified-payout gate уже реализован, но legal/financial scheme, operator verification workflow и explicit donation/payout provider choice всё ещё не подтверждены.

### Next

- `Ровно одна следующая подзадача: TASK-088 — shared/data donation service contract и transport integration для payout profile, donation history и intent creation без platform UI и без выбора внешнего PSP.`

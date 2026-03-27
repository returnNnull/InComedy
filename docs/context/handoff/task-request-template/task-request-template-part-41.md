# Task Request Template Part 41

## Implementation Outcome (EPIC-071 TASK-091 Shared/Data Notifications Transport)

### Epic

- `EPIC-071` — notifications / announcements delivery foundation.

### Task

- `TASK-091` — shared/data announcement service contract и transport integration для public event feed без platform UI.

### Status

- `completed`

### Delivered

- Добавлен новый `:data:notifications` модуль с `NotificationBackendApi`, transport DTO для `GET /api/v1/public/events/{eventId}/announcements` и `POST /api/v1/events/{eventId}/announcements`, а также `BackendNotificationService`, реализующий shared `NotificationService`.
- Добавлен `notificationsDataModule`, а `shared` и `InComedyKoin` теперь регистрируют `:data:notifications` и `:domain:notifications`, чтобы следующий Android/iOS шаг получил готовый shared DI seam без дополнительной модульной подготовки.
- Добавлены common transport tests для public list и protected create announcement flow, чтобы shared/data contract был зафиксирован исполняемым покрытием до platform UI.
- Scope остаётся provider-agnostic и по-прежнему не включает Android/iOS surfaces, `/api/v1/me/notifications`, device-token registration, moderation/reporting, durable outbox или activation FCM/APNs.

### Verification

- `Passed: ./gradlew --no-daemon --no-build-cache --max-workers=1 :data:notifications:allTests :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64`

### Notes

- Security review verdict: delivered slice добавляет только audience-safe/shared transport и DI wiring поверх уже существующего backend announcements surface; push-provider secrets, background delivery, device-token registration, staff/private payloads и implicit provider choice не появлялись.
- `R-014` остаётся open: shared/data gap закрыт, но Android/iOS feed surfaces, `/api/v1/me/notifications`, moderation, durable outbox и push/background path всё ещё отсутствуют.

### Next

- `Ровно одна следующая подзадача: TASK-092 — Android/iOS announcement/feed surfaces и executable verification без push-provider activation.`

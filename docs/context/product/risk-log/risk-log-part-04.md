# Risk Log Part 04

Новые и обновленные entries после split, выполненного в рамках `D-080`.

Используй этот part как текущую append-point зону для:

- новых active risks;
- materially updated mitigation/exposure/status existing risk entries;
- новых security vulnerabilities, если они появляются после split.

Если какой-то entry требует большой ретроспективной переработки старых записей, сначала обнови этот part, а потом при необходимости синхронизируй исторические parts без изменения append rule.

## R-013 (updated 2026-03-25)

- Дата: 2026-03-25
- Категория: `delivery/technical`
- Риск: Первый realtime slice для live stage всё ещё не является rollout-ready: Android/iOS уже потребляют public live feed, но backend fanout остаётся single-process, а degraded-network reconnect/push fallback пока отсутствуют.
- Текущая экспозиция / триггер: `TASK-086` закрыл lifecycle wiring и executable verification delivered live-update behavior, однако при multi-instance deployment без durable outbox/fanout и при плохой venue connectivity live updates всё ещё могут теряться.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Держать `EPIC-069` на review boundary без ложного rollout-ready verdict, а следующим bounded шагом после review проектировать durable outbox/multi-instance fanout и reconnect/fallback strategy перед production rollout.
- Владелец: Engineering
- Связанные артефакты: `D-078`, `TASK-084`, `TASK-085`, `TASK-086`, `docs/context/handoff/task-request-template/task-request-template-part-37.md`
- Статус: open

## R-005 (updated 2026-03-26)

- Дата: 2026-03-26
- Категория: `product/compliance`
- Риск: Donation payout model всё ещё может не пройти legal/compliance review, хотя backend теперь уже требует verified payout profile и хранит manual-settlement-ready donation intents.
- Текущая экспозиция / триггер: `TASK-087`, `TASK-088` и `TASK-089` уже доставили provider-agnostic backend, shared/data transport и Android/iOS payout history surfaces, но явная legal/financial scheme, operator verification workflow и внешний donation/payout provider всё ещё не подтверждены, а foundation UI не делает flow rollout-ready без checkout/webhook/payout execution path.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Держать уже закрытый `EPIC-070` provider-agnostic/manual-settlement-ready, не считать ticketing PSP выбором donation provider, не расширять donations foundation до checkout/webhook/payout automation без отдельного подтверждения, а до релизного включения donations получить явное product/finance confirmation по legal scheme и user-approved provider choice.
- Владелец: Product + Finance + Engineering
- Связанные артефакты: `D-081`, `TASK-087`, `TASK-088`, `TASK-089`, `docs/context/handoff/task-request-template/task-request-template-part-37.md`, `docs/context/handoff/task-request-template/task-request-template-part-39.md`, `docs/context/handoff/task-request-template/task-request-template-part-40.md`
- Статус: open

## R-014 (updated 2026-03-26)

- Дата: 2026-03-26
- Категория: `delivery/technical`
- Риск: Первый notifications slice пока не является rollout-ready communication layer: backend already publishes organizer announcements/event feed, но `/api/v1/me/notifications`, mobile/shared feed surfaces, moderation controls, durable outbox и push-provider delivery всё ещё отсутствуют.
- Текущая экспозиция / триггер: `TASK-090` закрыл только backend foundation с public `GET /api/v1/public/events/{eventId}/announcements`, protected `POST /api/v1/events/{eventId}/announcements` и `announcement.created` live-event payload-ом; без shared/data/UI и без FCM/APNs activation users не получают background-critical notifications и не видят feed в app.
- Влияние: Medium
- Вероятность: High
- Смягчение / следующий шаг: Продолжать `EPIC-071` по плану `TASK-091 -> TASK-092`, держать push providers в candidate state до отдельного explicit user confirmation, не считать backend feed эквивалентом полноценного rollout-ready notifications layer и до rollout добавить moderation/reporting плюс durable outbox/fanout strategy.
- Владелец: Engineering + Product
- Связанные артефакты: `D-082`, `TASK-090`, `docs/context/handoff/task-request-template/task-request-template-part-40.md`, `docs/context/engineering/implementation-status/implementation-status-part-01.md`
- Статус: open

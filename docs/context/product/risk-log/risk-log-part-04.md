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

## R-005 (updated 2026-03-25)

- Дата: 2026-03-25
- Категория: `product/compliance`
- Риск: Donation payout model всё ещё может не пройти legal/compliance review, хотя backend теперь уже требует verified payout profile и хранит manual-settlement-ready donation intents.
- Текущая экспозиция / триггер: `TASK-087` и `TASK-088` уже доставили provider-agnostic backend плюс shared/data transport foundation для payout profile и donation intents, но явная legal/financial scheme, operator verification workflow, platform UX и внешний donation/payout provider всё ещё не подтверждены.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Держать `EPIC-070` provider-agnostic, не считать ticketing PSP выбором donation provider, завершить `TASK-089` без активации checkout, а до релизного включения donations получить явное product/finance confirmation по legal scheme и user-approved provider choice.
- Владелец: Product + Finance + Engineering
- Связанные артефакты: `D-081`, `TASK-087`, `TASK-088`, `docs/context/handoff/task-request-template/task-request-template-part-37.md`, `docs/context/handoff/task-request-template/task-request-template-part-39.md`
- Статус: open

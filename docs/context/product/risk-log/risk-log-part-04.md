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

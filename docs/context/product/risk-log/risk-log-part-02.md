## R-008

- Дата: 2026-03-10
- Категория: `technical`
- Риск: Live stage-status updates могут работать ненадёжно при плохой venue connectivity, что подорвёт доверие organizer-ов и comedians во время активных shows.
- Текущая экспозиция / триггер: Деградация сети и reconnect behavior ещё не проверены на полном rollout-ready path.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Проектировать live state с WebSocket plus push/polling fallback, держать state transitions idempotent и тестировать degraded-network behavior до rollout.
- Владелец: Engineering
- Статус: open

## R-009

- Дата: 2026-03-10
- Категория: `security`
- Риск: Некорректное role/workspace permission modeling может открыть organizer financial data или разрешить неавторизованные operational actions.
- Текущая экспозиция / триггер: Organizer/ticketing scope продолжает расширяться и зависит от общей permission matrix.
- Влияние: Critical
- Вероятность: Medium
- Смягчение / следующий шаг: Реализовать explicit permission matrix, shared authorization checks, audit logging и permission-focused automated tests до organizer/ticketing rollout.
- Владелец: Engineering
- Статус: open

## R-010

- Дата: 2026-03-10
- Категория: `product/operations`
- Риск: Refund и cancellation flows создадут высокий support load, если policies и operator tooling не будут определены до ticketing launch.
- Текущая экспозиция / триггер: Refund/cancel policy и recovery tooling пока не завершены.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Зафиксировать refund/cancel policy до ticketing implementation, сохранить manual recovery tooling и явно отслеживать post-payment recovery incidents.
- Владелец: Product + Engineering
- Статус: open

## R-011

- Дата: 2026-03-14
- Категория: `delivery`
- Риск: Поворот auth-стратегии away from Telegram/Google может оставить stale legacy provider entry points в docs, runtime config или UI, создавая product confusion и unsupported login attempts.
- Текущая экспозиция / триггер: Repository всё ещё содержит legacy auth code/docs, которые можно ошибочно принять за активный surface.
- Влияние: Critical
- Вероятность: Medium
- Смягчение / следующий шаг: Убирать Telegram/Google/phone-OTP assumptions из активного app/runtime surface в той же change, где фиксируется decision pivot, сохраняя provider abstractions только как extension seams.
- Владелец: Product + Engineering
- Статус: in-progress

## R-012

- Дата: 2026-03-15
- Категория: `delivery`
- Риск: У проекта нет Apple Developer Program account и Google Play developer account, поэтому platform-console-only capabilities, store rollout и часть provider validations легко планировать как доступные, хотя это не так.
- Текущая экспозиция / триггер: Любая mobile distribution/store/provider задача может опереться на отсутствующие platform-console prerequisites.
- Влияние: High
- Вероятность: High
- Смягчение / следующий шаг: Рассматривать Apple/Google developer-account access как явную зависимость во всех задачах, затрагивающих mobile distribution, store compliance, paid Apple entitlements, universal-link/device validation, Play Console setup или provider flows с platform metadata.
- Владелец: Product + Engineering
- Статус: open

## R-013

- Дата: 2026-03-25
- Категория: `delivery/technical`
- Риск: Первый realtime slice для live stage пока не является end-to-end rollout-ready: backend fanout работает только внутри одного backend process-а, а Android/iOS product flows ещё не потребляют новый realtime feed.
- Текущая экспозиция / триггер: `/ws/events/{eventId}` и KMP subscription contract уже доставлены, но текущие mobile clients ещё не стартуют этот feed в runtime, а при multi-instance deployment без durable outbox/fanout live updates не гарантированы end-to-end.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Держать ограничение явно зафиксированным в `EPIC-069`, закрыть `TASK-086` для Android/iOS wiring и executable verification delivered live-update behavior, затем только отдельным bounded шагом проектировать durable outbox/multi-instance fanout перед rollout-ready verdict.
- Владелец: Engineering
- Связанные артефакты: `D-078`, `TASK-084`, `TASK-085`, `TASK-086`, commit `ecb5b96`
- Статус: open

# 08. API и событийная модель

## 8.1. Общие принципы API

- Основной протокол: REST/JSON.
- Live-обновления: WebSocket.
- Версионирование: `/api/v1/...`.
- Авторизация: bearer access token.
- Идемпотентность: обязательна для checkout и donation creation.
- Критичные серверные ошибки не должны утекать в клиент как raw stack trace.

## 8.2. Основные группы endpoint'ов

## Identity

- `POST /api/v1/auth/{provider}/start`
- `POST /api/v1/auth/{provider}/complete`
- `GET /api/v1/auth/session/me`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/providers/link`

## Profile & roles

- `GET /api/v1/me`
- `PATCH /api/v1/me`
- `GET /api/v1/me/roles`
- `POST /api/v1/me/active-role`

## Organizer workspace

- `POST /api/v1/workspaces`
- `GET /api/v1/workspaces`
- `POST /api/v1/workspaces/{id}/members/invite`
- `PATCH /api/v1/workspaces/{id}/members/{memberId}`

## Venues & layouts

- `POST /api/v1/venues`
- `GET /api/v1/venues`
- `POST /api/v1/venues/{id}/hall-templates`
- `PATCH /api/v1/hall-templates/{id}`
- `POST /api/v1/hall-templates/{id}/clone`

## Events

- `POST /api/v1/events`
- `GET /api/v1/events`
- `GET /api/v1/events/{id}`
- `PATCH /api/v1/events/{id}`
- `POST /api/v1/events/{id}/publish`
- `POST /api/v1/events/{id}/sales/open`
- `POST /api/v1/events/{id}/sales/pause`
- `POST /api/v1/events/{id}/cancel`

## Applications & lineup

- `POST /api/v1/events/{id}/applications`
- `GET /api/v1/events/{id}/applications`
- `PATCH /api/v1/applications/{id}`
- `GET /api/v1/events/{id}/lineup`
- `PATCH /api/v1/events/{id}/lineup`
- `POST /api/v1/events/{id}/lineup/live-state`

## Ticketing

- `GET /api/v1/events/{id}/inventory`
- `POST /api/v1/events/{id}/holds`
- `DELETE /api/v1/holds/{id}`
- `POST /api/v1/events/{id}/orders`
- `GET /api/v1/orders/{id}`
- `POST /api/v1/orders/{id}/refund`
- `GET /api/v1/me/tickets`

## Check-in

- `POST /api/v1/checkin/scan`
- `GET /api/v1/events/{id}/checkin/stats`

## Donations

- `POST /api/v1/events/{id}/donations`
- `GET /api/v1/me/donations`
- `GET /api/v1/comedian/me/donations`

## Notifications & feed

- `GET /api/v1/me/notifications`
- `POST /api/v1/events/{id}/announcements`

## 8.3. Webhook endpoints

- `POST /api/v1/webhooks/payments`
- `POST /api/v1/webhooks/payouts`

Требования:
- подпись webhook;
- replay protection;
- idempotent processing;
- dead-letter/retry strategy.

## 8.4. WebSocket каналы

### Event live channel

`/ws/events/{eventId}`

Поток должен отдавать события:
- `lineup.changed`
- `stage.current_changed`
- `sales.status_changed`
- `inventory.changed`
- `announcement.created`

### Staff channel

`/ws/events/{eventId}/staff`

Для роли organizer/checker/host:
- `checkin.count_changed`
- `incident.created`
- `payment.issue_detected`

## 8.5. Доменная событийная модель

Нужен внутренний event bus уровня приложения, даже если он сначала реализован через БД + outbox.

### Обязательные доменные события

- `user.registered`
- `auth.provider_linked`
- `workspace.member_invited`
- `venue.template_published`
- `event.published`
- `event.sales_opened`
- `event.canceled`
- `application.submitted`
- `application.approved`
- `lineup.updated`
- `stage.current_changed`
- `seat.held`
- `seat.released`
- `order.paid`
- `ticket.issued`
- `ticket.checked_in`
- `donation.paid`
- `refund.completed`

## 8.6. Правила API-дизайна

- Любая финансовая операция должна иметь `request_id`/`idempotency_key`.
- Любая критичная mutation должна оставлять audit trail.
- Response contracts не должны меняться silently.
- Для мобильных клиентов предпочтительны короткие и стабильные DTO.
- Перечни статусов должны быть централизованы и документированы.

## 8.7. Что не нужно делать в первой версии API

- GraphQL как обязательный слой.
- Слишком “умные” generic endpoints типа `/actions/execute`.
- Скрытые сайд-эффекты в `GET` и “магические” implicit transitions.

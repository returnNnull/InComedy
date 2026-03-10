# 09. NFR, безопасность и риски

## 9.1. Performance targets

- Холодный старт приложения: до `3.0s` на средних устройствах.
- Тёплый старт: до `2.0s`.
- P95 latency для большинства CRUD-операций: до `400ms`.
- P95 latency для hold/create order: до `700ms` без учёта PSP UI.
- Обновление live stage status до клиента: до `2s`.

## 9.2. Reliability targets

- Ни одно место не может быть продано дважды.
- Webhook payment не может обрабатываться небезопасно при повторной доставке.
- После успешной оплаты билет либо выдан, либо инцидент автоматически попадает в recovery queue.
- Критичные флоу должны иметь user-visible status:
  - auth;
  - checkout;
  - refund;
  - donation;
  - check-in.

## 9.3. Security requirements

- Secure storage на клиентах.
- TLS для всех внешних интеграций.
- Short-lived access token, rotated refresh token.
- RBAC для всех organizer/team операций.
- Request body limits и rate limiting для публичных endpoint'ов.
- Структурные логи без PII/секретов.
- Верификация webhook signatures.
- Replay protection для auth и платежей.
- Audit log по критичным действиям:
  - изменение цен;
  - назначение ответственных;
  - refund;
  - complimentary issuance;
  - изменение лайнапа live.

## 9.4. Privacy and compliance

- Минимизировать сбор персональных данных.
- Не логировать токены и сырые платежные реквизиты.
- Удаление/анонимизация пользовательских данных должно быть поддержано операционно.
- Правила оферты, возвратов и payout должны быть зафиксированы до релиза.

## 9.5. Observability

Нужно собирать:
- application logs;
- request IDs;
- payment correlation IDs;
- auth provider correlation;
- Sentry/ошибки;
- технические метрики;
- бизнес-метрики.

## 9.6. Тестовая стратегия

### Обязательно

- unit-тесты domain правил;
- ViewModel/MVI тесты;
- integration-тесты на ticketing и payment webhooks;
- contract tests на auth/payment integrations;
- smoke tests на release ветках.

### Особые сценарии

- double booking race;
- duplicate webhook;
- expired hold;
- refund after check-in;
- role escalation bug;
- lineup live reorder conflict.

## 9.7. Основные риски

| ID | Риск | Вероятность | Влияние | Смягчение |
| --- | --- | --- | --- | --- |
| R-01 | App Store не примет iOS-приложение без `Sign in with Apple` | Высокая | Высокое | Закладывать Apple login в MVP scope |
| R-02 | Donation flow окажется конфликтным для App Review или локальной юрсхемы | Высокая | Высокое | Делать pass-through/web checkout, верифицировать payout profiles |
| R-03 | Oversell мест из-за ошибок concurrency | Средняя | Критичное | Seat hold TTL, row-level locking, integration tests |
| R-04 | Организаторы не захотят вручную рисовать сложные схемы зала | Средняя | Высокое | Venue templates, clone, импорт типовых шаблонов, ограничить MVP до 2D builder |
| R-05 | Слишком широкий MVP замедлит релиз | Высокая | Высокое | Урезать публичный чат и сложные social features, оставить operational core |
| R-06 | Комики без легального payout-профиля не смогут принимать донаты | Высокая | Среднее | Верификация, manual settlement, staged rollout донатов |
| R-07 | Live-режим сцены будет работать нестабильно в плохой сети | Средняя | Высокое | WebSocket + push fallback + resilient polling |
| R-08 | Возвраты и отмены дадут большой саппорт-нагрузку | Средняя | Высокое | Ясные policy, статусы, admin tooling |
| R-09 | Неверная модель ролей приведёт к утечке финансовых данных | Средняя | Критичное | Чёткий RBAC, audit log, tests on permissions |
| R-10 | Продукт превратится в копию общего ticketing без стендап-дифференциатора | Средняя | Высокое | Фокус на lineup/live/donations/team ops |

## 9.8. Launch blockers

До релиза нельзя оставлять нерешёнными:

- store-compliance по auth и donations;
- double booking protection;
- refund/cancel policy;
- payout verification;
- live stage fallback behavior;
- check-in duplicates handling;
- observability для auth/payment incidents.

## 9.9. Что должно быть в release checklist

- Тестовый прогон оплаты билета.
- Тестовый прогон доната.
- Тест check-in валидного и повторно использованного QR.
- Тест переключения ролей.
- Тест приглашения staff user.
- Тест live update “кто на сцене”.
- Тест sold out -> release -> seat available again.

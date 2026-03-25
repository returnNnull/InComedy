# Реестр рисков

Этот файл является каноническим реестром активных продуктовых, delivery, technical и security risks проекта.

Automation и ручные сессии обязаны обновлять его в том же work block, если meaningful task:

- создаёт новый остаточный риск;
- materially changes mitigation, owner, status или current exposure уже существующего риска;
- снимает риск и переводит его в `closed` или `superseded`;
- оставляет после commit-а ограничения/риски, которые переживут текущий task и должны быть явно видны следующему запуску.

Не используй этот файл вместо:

- `engineering/issue-resolution-log.md` для повторяемых technical problems и repair path;
- `governance/session-log.md` для хронологии сессий;
- `handoff/active-run.md` для crash-safe recovery checkpoint.

## Шаблон риска

- ID:
- Дата:
- Категория:
- Риск:
- Текущая экспозиция / триггер:
- Влияние:
- Вероятность:
- Смягчение / следующий шаг:
- Владелец:
- Связанные артефакты:
- Статус:

## Шаблон уязвимости

- ID:
- Дата обнаружения:
- Категория: `security`
- Уязвимость:
- Затронутые компоненты:
- Severity:
- Exploitability:
- Current exposure:
- Immediate containment:
- Remediation plan:
- Target fix date:
- Owner:
- Связанные артефакты:
- Status:

---

## R-001

- Дата: 2026-02-23
- Категория: `product`
- Риск: Задержка интеграции платёжного провайдера сдвигает MVP ticketing и donations.
- Текущая экспозиция / триггер: Реальный PSP ещё не выбран и не доведён до sandbox/live flow.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Выбрать провайдера заранее, сначала пройти sandbox flow и сохранить manual refund fallback.
- Владелец: TBD
- Статус: open

## R-002

- Дата: 2026-02-23
- Категория: `product`
- Риск: Event feed или будущие user-generated communication features создадут abuse/spam risk без базовых moderation controls.
- Текущая экспозиция / триггер: Публичные коммуникационные функции могут быть добавлены раньше moderation/reporting baseline.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Не включать широкий public chat в MVP, добавить базовые moderation/reporting controls для announcements и будущих community features до rollout.
- Владелец: TBD
- Статус: open

## R-003

- Дата: 2026-03-06
- Категория: `product/compliance`
- Риск: iOS release может быть заблокирован, если third-party login будет shipped без Sign in with Apple или donation flow конфликтует с App Review expectations.
- Текущая экспозиция / триггер: Apple-specific compliance dependencies ещё не закрыты.
- Влияние: High
- Вероятность: High
- Смягчение / следующий шаг: Держать Sign in with Apple в MVP scope, заранее сверять donation UX с App Store guidelines и сохранять iOS donation flow совместимым с pass-through/web-checkout fallback.
- Владелец: Product + Engineering
- Статус: open

## R-004

- Дата: 2026-03-06
- Категория: `technical`
- Риск: Oversell мест или неконсистентное inventory state во время concurrent checkout подрывают доверие и увеличивают refund/support cost.
- Текущая экспозиция / триггер: Ticketing slice зависит от корректной concurrency/idempotency semantics.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Явно моделировать sellable inventory, использовать row-level locking/transactional holds, enforce idempotent order/payment handling и держать concurrency integration tests.
- Владелец: Engineering
- Статус: open

## R-005

- Дата: 2026-03-06
- Категория: `product/compliance`
- Риск: Donation payout model может не пройти legal/compliance review для comedians без verified payout identity.
- Текущая экспозиция / триггер: Верифицированная payout identity и settlement model ещё не определены.
- Влияние: High
- Вероятность: High
- Смягчение / следующий шаг: Требовать payout profile verification до включения donations, сохранить manual settlement fallback и запускать donations только после утверждённой legal/financial scheme.
- Владелец: Product + Finance
- Статус: open

## R-006

- Дата: 2026-03-06
- Категория: `delivery`
- Риск: Scope hall builder-а разрастается и задерживает MVP, если относиться к нему как к generic CAD editor.
- Текущая экспозиция / триггер: Hall tooling легко расширяется за пределы bounded organizer workflow.
- Влияние: High
- Вероятность: Medium
- Смягчение / следующий шаг: Держать hall builder v1 ограниченным 2D templates, rows/seats/tables/zones/stage/blocking и откладывать advanced freeform editing.
- Владелец: Product + Engineering
- Статус: open

## R-007

- Дата: 2026-03-06
- Категория: `delivery`
- Риск: Over-scoped MVP (public chat, complex payouts, advanced recommendations) задерживает delivery operational core, который создаёт ценность.
- Текущая экспозиция / триггер: Backlog легко раздувается за пределы organizer operations, ticketing, lineup, check-in и live stage core.
- Влияние: High
- Вероятность: High
- Смягчение / следующий шаг: Приоритизировать organizer operations, ticketing, lineup, check-in и live stage status; broad social/community scope держать в P1/P2.
- Владелец: Product
- Статус: open

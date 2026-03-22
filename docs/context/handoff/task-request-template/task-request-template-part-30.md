# Task Request Template Part 30

## Formalized Implementation Request (EPIC-067 Shared/Data/Feature Lineup Foundation)

### Epic

- `EPIC-067` — comedian applications and lineup foundation.

### Task

- `TASK-069` — shared/data/feature integration для organizer/comedian applications и lineup surfaces без Android/iOS UI wiring.

### Why This Step

- После `TASK-068` backend foundation уже покрывал submit/review/reorder, но shared/mobile слой еще не имел собственного bounded context-а для работы с comedian applications и lineup.
- Без KMP foundation следующий platform UI step вынудил бы Android/iOS экраны напрямую тянуть backend DTO или дублировать orchestration на каждой платформе.
- Нужен был один безопасный additive шаг с локальным blast radius: подготовить общие контракты, backend adapter, shared ViewModel и bridge, но не начинать platform UI в том же запуске.

### Scope For This Run

- Добавить dedicated `:domain:lineup`, `:data:lineup`, `:feature:lineup` и `shared/lineup`.
- Вынести KMP-модели comedian application / lineup entry и service contract для submit/list/review/reorder.
- Добавить backend adapter, Koin wiring, shared MVI state и Swift-friendly bridge/snapshots.
- Добавить минимально релевантную verification для нового shared/data/feature slice-а и синхронизировать `docs/context/*`.

### Explicitly Out Of Scope

- Android Compose UI wiring
- iOS SwiftUI wiring
- новые backend routes или live-stage semantics
- push notifications и comedian-facing history/list beyond текущий backend contract

### Constraints

- Новый bounded context должен оставаться additive и не менять semantics `D-067`.
- Shared слой не должен зависеть от platform UI.
- Новый и существенно измененный код должен сохранять русские комментарии.
- Верификация должна опираться на локально исполнимые Gradle checks.

### Acceptance Signals

- В кодовой базе есть отдельный KMP bounded context `lineup` в `domain/data/feature/shared`.
- Shared ViewModel умеет обрабатывать comedian submit, organizer review и lineup reorder поверх backend foundation.
- Swift-слой получает stable bridge/snapshot API без прямой работы с backend DTO.
- Automated verification покрывает новый feature slice и KMP compile integration.

### Implementation Outcome

#### Delivered

- Добавлены `:domain:lineup`, `:data:lineup`, `:feature:lineup` и `shared/lineup` с доменными моделями, service contract, backend HTTP adapter, shared MVI state и Swift-friendly bridge/snapshots.
- Общий DI/Koin слой обновлен для нового bounded context-а без вмешательства в Android/iOS UI wiring.
- Контекст-документы синхронизированы: `TASK-069` помечен завершенным, следующий bounded step переведен на `TASK-070`.

#### Verification

- `./gradlew :feature:lineup:allTests`
- `./gradlew :data:lineup:compileKotlinMetadata :shared:compileKotlinMetadata`

#### Remaining Follow-Up

- `TASK-070`: Android/iOS UI wiring и executable coverage для comedian applications и lineup management.
- Позднее отдельно: richer comedian-facing visibility, live-stage semantics и дополнительные organizer editing rules.

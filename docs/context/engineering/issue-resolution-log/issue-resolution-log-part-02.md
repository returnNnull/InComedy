# Журнал проблем и решений — Part 02

## I-002

- Заголовок: Kotlin/Gradle build cache collisions после параллельных daemon-сессий по одному worktree.
- Категория: Gradle / Kotlin daemon / local verification harness.
- Впервые зафиксировано: 2026-03-26.
- Последний раз подтверждено: 2026-03-26.
- Затронутые контуры: `server`, `domain:*`, `EPIC-071`, `TASK-090`.
- Статус: resolved.

### Симптомы

- Параллельные `./gradlew` процессы по одному репозиторию падают с сообщениями вида:
  - `Storage ... already registered`
  - `Could not close incremental caches`
  - `CorruptedException`
  - `Detected multiple Kotlin daemon sessions`
- Ошибки возникают не в продуктовой логике, а в Kotlin incremental caches внутри `build/kotlin/.../cacheable/...`.
- Повторный запуск тех же команд без cleanup часто лишь переносит симптом между модулями (`domain:notifications`, `domain:donations`, `domain:venue`, `server`).

### Подтверждённые команды

```bash
./gradlew --no-build-cache :domain:notifications:compileKotlinJvm :server:compileKotlin
./gradlew --no-build-cache :server:test --tests 'com.bam.incomedy.server.notifications.AnnouncementRoutesTest' --tests 'com.bam.incomedy.server.lineup.EventLiveChannelRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'
./gradlew --stop
rm -rf domain/notifications/build/kotlin/compileKotlinJvm/cacheable \
  domain/donations/build/kotlin/compileKotlinJvm/cacheable \
  domain/venue/build/kotlin/compileKotlinJvm/cacheable \
  server/build/kotlin/compileKotlin/cacheable \
  server/build/kotlin/compileTestKotlin/cacheable
./gradlew --no-daemon --no-build-cache --max-workers=1 :domain:notifications:compileKotlinJvm :server:compileKotlin
./gradlew --no-daemon --no-build-cache --max-workers=1 :server:test --tests 'com.bam.incomedy.server.notifications.AnnouncementRoutesTest' --tests 'com.bam.incomedy.server.lineup.EventLiveChannelRoutesTest' --tests 'com.bam.incomedy.server.db.DatabaseMigrationRunnerTest'
```

### Что уже известно

- Основной триггер — именно параллельные Gradle/Kotlin daemon-сессии по одному worktree, особенно когда они одновременно пишут в `build/kotlin/.../cacheable`.
- Проблема не подтверждает regression в product code сама по себе; сначала нужно стабилизировать build harness, а уже потом интерпретировать compile/test verdict.
- Последовательные rerun-ы с `--no-daemon --max-workers=1` после `./gradlew --stop` и cleanup cacheable directories снимают симптоматику.

### Рекомендуемый порядок действий

1. Если видишь `Storage ... already registered`, `CorruptedException` или аналогичные Kotlin cache сообщения, остановись и не продолжай параллельные rerun-ы.
2. Выполни `./gradlew --stop`.
3. Очисти только затронутые `build/kotlin/.../cacheable` директории в текущем worktree.
4. Повтори verification уже последовательно:
   - `--no-daemon`
   - `--max-workers=1`
   - без второго параллельного `gradlew` процесса на этот же репозиторий.
5. Только после этого делай вывод, есть ли реальные compile/test failures в продуктовой логике.
6. Если после repair path-а остаются уже обычные compile/test ошибки, чинить их как product code issue в рамках текущего `TASK`, а не путать с daemon/cache collision.

### Что уже проверено и не помогло

- Немедленный rerun тех же `gradlew` команд без `./gradlew --stop`.
- Параллельный запуск compile/test задач в нескольких отдельных `gradlew` процессах по одному worktree.
- Интерпретация первых daemon/cache stack trace как продуктовых compile ошибок до стабилизации build harness.

### Критерий успеха

- `gradlew` больше не падает на `Storage ... already registered` / `CorruptedException`.
- Последовательный compile/test rerun доходит до обычного terminal verdict.
- Дальнейшие ошибки, если они есть, становятся воспроизводимыми product-code failures без daemon/cache noise.

### Итог разрешения

- На `2026-03-26 18:18-18:19 MSK` repair path сработал полностью: после `./gradlew --stop`, cleanup cacheable Kotlin directories и sequential rerun-ов `:domain:notifications:compileKotlinJvm :server:compileKotlin` и targeted `:server:test` успешно завершились.

### Когда эскалировать

- Если симптом повторяется даже после `./gradlew --stop`, cleanup и single-run verification.
- Если collision возникает без параллельных `gradlew` процессов и уже на clean daemon state.
- Если проблема начинает воспроизводиться в CI или на другом host, а не только в локальном executor worktree.

### Связанные материалы

- `docs/context/handoff/active-run.md`
- `docs/context/engineering/verification-memory.md`
- `docs/context/governance/decisions-log/decisions-log-part-08.md` (`D-082`)
- `docs/context/governance/session-log/session-log-part-25.md`

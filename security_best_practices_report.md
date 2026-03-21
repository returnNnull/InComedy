# Security Best Practices Report

## Executive Summary

Провёл статический security review кодовой базы `InComedy` с фокусом на реально активную поверхность: Android-клиент, Ktor backend, auth/session flow, платежный webhook surface и runtime-конфигурацию.

В проекте уже есть несколько сильных базовых решений: пароли хэшируются через `Argon2id`, refresh tokens хранятся в виде SHA-256 hash, у большинства auth-route есть rate limits, request body размер ограничен, а чувствительная серверная диагностика отделена в отдельный operator-only endpoint.

Наиболее серьёзные риски сейчас сосредоточены вокруг Android/VK auth flow:

1. Browser-based VK callback можно перехватить на Android и довести до выдачи серверной сессии.
2. VK Android `client secret` встраивается в APK через manifest placeholders.
3. В in-memory fallback rate limiter нет eviction, что делает auth surface уязвимой к memory DoS при отсутствии Redis.
4. Android хранит session tokens локально, при этом приложение оставляет backup включённым по умолчанию.

Ниже findings отсортированы по приоритету.

## Critical

### SBP-001: VK browser callback допускает перехват и replay до серверной сессии

**Impact:** вредоносное Android-приложение, зарегистрировавшее тот же custom scheme, может перехватить VK callback, извлечь PKCE verifier из `state` и завершить login flow от имени пользователя.

**Почему это опасно**

- Android-клиент принимает auth callback через `incomedy://auth/vk`, а не через verified App Link:
  - `composeApp/src/main/AndroidManifest.xml:21-42`
- HTTPS callback bridge на сервере перекладывает весь query string как есть в deep link:
  - `server/src/main/resources/static/vk-callback.html:56-61`
  - `server/src/main/resources/static/vk-callback.html:104-107`
- Серверный `state` не шифруется: в него кладётся `codeVerifier`, после чего payload лишь подписывается HMAC и base64url-кодируется:
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/vk/VkIdLoginStateCodec.kt:22-35`
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/vk/VkIdLoginStateCodec.kt:52-65`
- Мобильный клиент затем парсит этот callback URL и отправляет `code + state + device_id` на backend для завершения сессии:
  - `data/auth/src/commonMain/kotlin/com/bam/incomedy/data/auth/backend/AuthBackendApi.kt:75-86`
  - `data/auth/src/commonMain/kotlin/com/bam/incomedy/data/auth/backend/AuthBackendApi.kt:162-178`
- Дополнительно shared auth-слой разрешает VK completion даже при отсутствии локального pending-state:
  - `feature/auth/src/commonMain/kotlin/com/bam/incomedy/feature/auth/mvi/AuthViewModel.kt:271-283`

**Сценарий атаки**

1. Пользователь начинает VK login.
2. VK возвращает пользователя на `https://incomedy.ru/auth/vk/callback?...`.
3. Bridge-страница формирует `incomedy://auth/vk?...` и передаёт в app весь callback payload.
4. Любое другое приложение, перехватившее этот custom scheme, получает `code`, `device_id` и подписанный `state`.
5. Так как `state` содержит `codeVerifier` в незашифрованном виде, злоумышленник может извлечь его из payload и отправить свой `POST /api/v1/auth/vk/verify`.
6. Backend выдаёт полноценную InComedy session.

**Рекомендация**

- Убрать PKCE verifier из клиентски-видимого `state`.
- Хранить verifier server-side по opaque nonce, а в `state` передавать только случайный идентификатор попытки.
- Заменить custom-scheme browser return path на Android App Links с domain verification.
- Как defence-in-depth: не разрешать VK completion без локального pending-state для browser flow.

## High

### SBP-002: VK Android client secret встраивается в APK manifest

**Impact:** любой получивший APK может извлечь provider credential, который должен считаться конфиденциальным.

**Почему это опасно**

- Build script читает `INCOMEDY_VK_ANDROID_CLIENT_SECRET` и кладёт его в `manifestPlaceholders`:
  - `composeApp/build.gradle.kts:65-70`
  - `composeApp/build.gradle.kts:107-110`
- Транситивный manifest VK SDK действительно разворачивает это значение в `<meta-data android:name="VKIDClientSecret" ...>`:
  - `/Users/abetirov/.gradle/caches/9.2.1/transforms/936a7b93262106996b4df07cc2ad6067/transformed/vkid-2.6.1/AndroidManifest.xml:62-67`

**Замечание**

Даже если текущий flow работает через PKCE и server-side exchange, сам факт публикации долгоживущего `client secret` внутри мобильного артефакта нарушает secure-by-default модель для public mobile client.

**Рекомендация**

- Не поставлять `VK_ANDROID_CLIENT_SECRET` в Android build вообще.
- Считать Android-приложение публичным клиентом.
- Оставить только публичный client id + PKCE, а любой действительно секретный обмен выполнять на backend.
- Пока это не исправлено, хотя бы не выпускать release без обфускации:
  - `composeApp/build.gradle.kts:135-138`

## Medium

### SBP-003: In-memory rate limiter не очищает старые ключи и допускает memory DoS

**Impact:** при запуске без Redis удалённый атакующий может создавать большое число уникальных rate-limit keys и постепенно раздувать heap процесса через auth endpoints.

**Почему это опасно**

- Сервер по умолчанию или при ошибке Redis silently падает обратно в `InMemoryAuthRateLimiter`:
  - `server/src/main/kotlin/com/bam/incomedy/server/Application.kt:86-99`
- In-memory реализация хранит buckets в `ConcurrentHashMap`, но не удаляет старые записи и не ограничивает cardinality:
  - `server/src/main/kotlin/com/bam/incomedy/server/security/AuthRateLimiter.kt:11-33`
- Credential auth использует отдельный ключ на каждый уникальный login hash:
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/credentials/CredentialsAuthRoutes.kt:119-124`

**Практический эффект**

Даже один источник трафика может слать запросы с бесконечным числом новых `login` значений и создавать всё новые buckets, которые никогда не эвиктятся.

**Рекомендация**

- Добавить TTL eviction / size cap для in-memory buckets.
- Либо использовать bounded cache (`Caffeine`, `expireAfterWrite`, `maximumSize`).
- Для production auth surface лучше fail closed или хотя бы явно алертить, если Redis недоступен и сервер откатывается на in-memory limiter.

## Low

### SBP-004: Android session tokens сохраняются локально, но app backup оставлен включённым

**Impact:** auth-артефакты попадают в стандартный backup surface Android, что увеличивает exposure area и создаёт лишние риски вокруг восстановления/переноса данных.

**Почему это опасно**

- Приложение сохраняет `access_token` и `refresh_token` в `EncryptedSharedPreferences`:
  - `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/viewmodel/AuthAndroidViewModel.kt:93-105`
  - `composeApp/src/main/kotlin/com/bam/incomedy/feature/auth/viewmodel/AuthAndroidViewModel.kt:127-169`
- При этом в manifest стоит `android:allowBackup="true"` и нет явного исключения auth storage из backup rules:
  - `composeApp/src/main/AndroidManifest.xml:5-12`

**Рекомендация**

- Для secure-by-default поведения отключить backup совсем (`android:allowBackup="false"`), если бизнесу это подходит.
- Если backup нужен, добавить `dataExtractionRules` / `fullBackupContent` и исключить `auth_session_secure`.

## Additional Secure-by-Default Improvements

### High-value next steps

1. Ввести отдельный `VK_ID_STATE_SECRET` без fallback на `JWT_SECRET`, чтобы не смешивать разные trust domains.
2. Добавить минимальные требования к длине/энтропии для `JWT_SECRET`, `VK_ID_STATE_SECRET`, `DIAGNOSTICS_ACCESS_TOKEN`.
3. Явно задать `android:usesCleartextTraffic="false"` и при необходимости `networkSecurityConfig`, чтобы Android networking был deny-by-default.
4. Перевести auth return surfaces на verified links там, где это возможно.
5. Включить release minification/obfuscation после настройки mapping-file процесса.

## Positive Observations

- `Argon2id` для паролей:
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/credentials/Argon2PasswordHasher.kt:5-18`
- Refresh tokens не хранятся в открытом виде:
  - `server/src/main/kotlin/com/bam/incomedy/server/auth/session/JwtSessionTokenService.kt:39-42`
  - `server/src/main/resources/db/migration/V1__auth_core.sql:16-21`
- Request body size limits внедрены системно на auth и webhook маршрутах.
- Diagnostics endpoint отделён и сравнивает access token в constant time:
  - `server/src/main/kotlin/com/bam/incomedy/server/observability/DiagnosticsRoutes.kt:17-24`
  - `server/src/main/kotlin/com/bam/incomedy/server/observability/DiagnosticsRoutes.kt:72-77`

## Scope Notes

- Это был статический code/config review. Live deployment, реальные секреты, cloud perimeter и mobile release artifact не тестировались.
- В skill reference directory не было Kotlin/Ktor-specific guidance, поэтому выводы основаны на общих secure-by-default практиках для Android, OAuth/OIDC, JWT и backend auth surfaces.

package com.bam.incomedy.server.auth.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Ответ backend-а с официальным Telegram auth URL и серверно подписанным state.
 *
 * @property authUrl URL, который нужно открыть во внешнем браузере.
 * @property state Серверно подписанный state текущей попытки входа.
 */
@Serializable
data class TelegramAuthStartResponse(
    @SerialName("auth_url")
    val authUrl: String,
    val state: String,
)

/**
 * Payload завершения Telegram auth после возврата из callback bridge.
 *
 * @property code Authorization code из Telegram OIDC callback.
 * @property state Серверно подписанный state, полученный на этапе старта auth.
 */
@Serializable
data class TelegramVerifyRequest(
    val code: String,
    val state: String,
)

/**
 * Внутреннее представление Telegram launch-конфигурации для мобильного клиента.
 *
 * @property authUrl URL авторизации, который должен быть открыт во внешнем браузере.
 * @property state Серверно подписанный state конкретной auth-попытки.
 */
data class TelegramAuthLaunch(
    val authUrl: String,
    val state: String,
)

/**
 * Нормализованный Telegram профиль после успешной OIDC-проверки.
 *
 * @property id Числовой Telegram user id.
 * @property firstName Отображаемое имя пользователя.
 * @property lastName Дополнительная часть имени, если доступна.
 * @property username Username пользователя Telegram.
 * @property photoUrl Ссылка на аватар Telegram.
 */
data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String?,
    val username: String?,
    val photoUrl: String?,
)

/**
 * Результат серверной проверки Telegram auth перед выпуском внутренней сессии.
 *
 * @property user Нормализованный профиль Telegram после OIDC-проверки.
 * @property assertionHash Хэш одноразового auth-утверждения для replay-защиты.
 * @property replayExpiresAt Время, до которого replay-запись должна храниться.
 */
data class VerifiedTelegramAuth(
    val user: TelegramUser,
    val assertionHash: String,
    val replayExpiresAt: Instant,
)

/**
 * Серверно выпущенный state и связанный с ним PKCE verifier.
 *
 * @property state Подписанный state, возвращаемый клиенту и Telegram.
 * @property codeVerifier PKCE verifier для последующего `/token` exchange.
 * @property redirectUri Redirect URI, с которым Telegram начал auth flow.
 */
data class IssuedTelegramLoginState(
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
)

/**
 * Проверенный сервером state Telegram login.
 *
 * @property codeVerifier PKCE verifier, восстановленный из state.
 * @property redirectUri Redirect URI, зафиксированный при старте auth.
 * @property expiresAt Момент истечения допустимости текущего state.
 */
data class VerifiedTelegramLoginState(
    val codeVerifier: String,
    val redirectUri: String,
    val expiresAt: Instant,
)

package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.data.auth.backend.TelegramAuthGateway
import com.bam.incomedy.data.auth.backend.TelegramVerifyPayload
import com.bam.incomedy.domain.auth.AuthLaunchRequest
import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.auth.SocialAuthProvider

/**
 * Telegram auth provider, который делегирует официальный launch/verify flow backend-у.
 *
 * @property gateway Backend gateway, знающий актуальный Telegram auth contract.
 */
class TelegramAuthProvider(
    private val gateway: TelegramAuthGateway,
) : SocialAuthProvider {
    /** Тип текущего провайдера во внутреннем auth-контракте. */
    override val type: AuthProviderType = AuthProviderType.TELEGRAM

    /** Запрашивает у backend-а официальный Telegram launch URL и state. */
    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        return gateway.startTelegramAuth().map { launch ->
            AuthLaunchRequest(
                provider = type,
                state = launch.state,
                url = launch.authUrl,
            )
        }
    }

    /** Обменивает callback URL с `code/state` на внутреннюю backend-сессию. */
    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        val callback = parseTelegramCallback(callbackUrl = code)
            ?: return Result.failure(IllegalArgumentException("Invalid Telegram callback payload"))

        return gateway.verifyTelegram(callback).map { session ->
            AuthSession(
                provider = type,
                userId = session.userId,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                user = session.user,
            )
        }
    }

    /** Извлекает `code` и `state` из query/fragment Telegram callback URL. */
    private fun parseTelegramCallback(callbackUrl: String): TelegramVerifyPayload? {
        val params = buildMap {
            putAll(parseKeyValuePart(callbackUrl.substringAfter('?', "").substringBefore('#')))
            putAll(parseKeyValuePart(callbackUrl.substringAfter('#', "")))
        }
        val code = params["code"]?.takeIf { it.isNotBlank() } ?: return null
        val state = params["state"]?.takeIf { it.isNotBlank() } ?: return null
        return TelegramVerifyPayload(
            code = code,
            state = state,
        )
    }

    /** Разбирает query-like часть callback URL без платформенных URI helper-ов. */
    private fun parseKeyValuePart(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return value
            .split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                pair.substring(0, index) to decodePercent(pair.substring(index + 1))
            }
            .toMap()
    }

    /** Выполняет percent-decoding значений query-параметров. */
    private fun decodePercent(value: String): String {
        val sb = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '%' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    sb.append(decoded.toChar())
                    index += 3
                    continue
                }
            }
            if (ch == '+') sb.append(' ') else sb.append(ch)
            index++
        }
        return sb.toString()
    }
}

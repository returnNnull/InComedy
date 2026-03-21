package com.bam.incomedy.server.payments.yookassa

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.net.InetAddress

/**
 * Security policy для входящих webhook-уведомлений YooKassa.
 *
 * Политика закрепляет опубликованные YooKassa IP-диапазоны и разрешает доверять
 * `X-Forwarded-For` только тогда, когда прямой peer запроса выглядит как локальный reverse proxy.
 */
object YooKassaWebhookSecurity {
    private val allowedNetworks = listOf(
        IpNetwork.parse("185.71.76.0/27"),
        IpNetwork.parse("185.71.77.0/27"),
        IpNetwork.parse("77.75.153.0/25"),
        IpNetwork.parse("77.75.156.11/32"),
        IpNetwork.parse("77.75.156.35/32"),
        IpNetwork.parse("77.75.154.128/25"),
        IpNetwork.parse("2a02:5180::/32"),
    )

    /** Извлекает IP источника webhook-а с учетом локального reverse proxy. */
    fun extractSourceIp(
        call: ApplicationCall,
    ): String {
        val directPeer = call.request.local.remoteHost
            .trim()
            .takeIf { it.isNotBlank() }
            ?: return "unknown"
        if (directPeer.isLocalProxyHost()) {
            val forwarded = call.request.headers[HttpHeaders.XForwardedFor]
                ?.split(',')
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (forwarded != null) {
                return forwarded
            }
        }
        return directPeer
    }

    /** Проверяет, входит ли IP источника в опубликованные YooKassa ranges. */
    fun isAllowedSourceIp(
        sourceIp: String,
    ): Boolean {
        val address = runCatching { InetAddress.getByName(sourceIp) }.getOrNull() ?: return false
        return allowedNetworks.any { network -> network.contains(address) }
    }
}

/**
 * Представление CIDR-сети для проверки принадлежности IP опубликованным YooKassa ranges.
 *
 * @property addressBytes Байт-массив network address.
 * @property prefixLength Длина сетевого префикса в битах.
 */
private data class IpNetwork(
    val addressBytes: ByteArray,
    val prefixLength: Int,
) {
    init {
        require(prefixLength in 0..(addressBytes.size * 8)) {
            "Invalid prefix length for network"
        }
    }

    /** Проверяет, попадает ли конкретный IP-адрес в текущую сеть. */
    fun contains(
        candidate: InetAddress,
    ): Boolean {
        val candidateBytes = candidate.address
        if (candidateBytes.size != addressBytes.size) return false
        val fullBytes = prefixLength / 8
        val remainingBits = prefixLength % 8
        for (index in 0 until fullBytes) {
            if (candidateBytes[index] != addressBytes[index]) return false
        }
        if (remainingBits == 0) return true
        val mask = (0xFF shl (8 - remainingBits)) and 0xFF
        return (candidateBytes[fullBytes].toInt() and mask) == (addressBytes[fullBytes].toInt() and mask)
    }

    companion object {
        /** Разбирает CIDR-строку в пригодное для матчей внутреннее представление. */
        fun parse(
            rawValue: String,
        ): IpNetwork {
            val (host, prefix) = rawValue.split('/', limit = 2).let { parts ->
                when (parts.size) {
                    1 -> parts[0] to null
                    2 -> parts[0] to parts[1]
                    else -> error("Invalid CIDR notation")
                }
            }
            val address = InetAddress.getByName(host)
            val effectivePrefix = prefix?.toIntOrNull() ?: (address.address.size * 8)
            return IpNetwork(
                addressBytes = address.address,
                prefixLength = effectivePrefix,
            )
        }
    }
}

/** Проверяет, что direct peer похож на локальный proxy/loopback, которому можно доверить XFF. */
private fun String.isLocalProxyHost(): Boolean {
    if (equals("localhost", ignoreCase = true)) return true
    if (equals("127.0.0.1")) return true
    if (equals("::1")) return true
    return false
}

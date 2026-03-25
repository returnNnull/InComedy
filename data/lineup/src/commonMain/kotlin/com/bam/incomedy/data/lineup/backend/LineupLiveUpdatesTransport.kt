package com.bam.incomedy.data.lineup.backend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation

/** Абстракция над transport-уровнем public live-event WebSocket channel-а. */
internal fun interface LineupLiveUpdatesTransport {
    /** Открывает live-event stream и возвращает сырой text payload каждого realtime сообщения. */
    fun observe(url: String): Flow<String>
}

/** Ktor-реализация transport-а для public live-event WebSocket channel-а. */
internal class KtorLineupLiveUpdatesTransport(
    private val httpClient: HttpClient,
) : LineupLiveUpdatesTransport {
    override fun observe(url: String): Flow<String> = flow {
        val session = httpClient.webSocketSession {
            url(url)
        }
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    emit(frame.readText())
                }
            }

            val closeReason = session.closeReason.await()
            if (closeReason != null && closeReason.code != CloseReason.Codes.NORMAL.code) {
                throw LineupLiveUpdatesClosedException(
                    code = closeReason.code,
                    message = closeReason.message,
                )
            }
        } finally {
            session.close()
        }
    }
}

/** Сигнализирует, что backend закрыл public live-event channel нештатной причиной. */
internal class LineupLiveUpdatesClosedException(
    val code: Short,
    message: String,
) : Exception(
    "Live updates stream closed (code=$code, reason=${message.ifBlank { "unknown" }})",
)

/** Создает Ktor client c WebSockets plugin для public live-event transport-а. */
internal fun createLineupLiveUpdatesHttpClient(parser: Json): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(parser)
        }
        install(WebSockets)
    }
}

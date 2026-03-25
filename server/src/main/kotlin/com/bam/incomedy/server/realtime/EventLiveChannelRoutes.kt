package com.bam.incomedy.server.realtime

import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.LineupRepository
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.directPeerFingerprint
import io.ktor.server.plugins.callid.callId
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * WebSocket event live channel для audience-safe live stage updates.
 *
 * Первый bounded шаг epic-а публикует только `lineup.changed` и `stage.current_changed`; staff
 * channel, sales/inventory/announcement события и push fallback останутся следующими задачами.
 */
object EventLiveChannelRoutes {
    private val logger = LoggerFactory.getLogger(EventLiveChannelRoutes::class.java)

    /** Регистрирует public live-event channel конкретного события. */
    fun register(
        route: Route,
        eventRepository: EventRepository,
        lineupRepository: LineupRepository,
        broadcaster: EventLiveChannelBroadcaster,
        rateLimiter: AuthRateLimiter,
    ) {
        route.webSocket("/ws/events/{eventId}") {
            val eventId = call.parameters["eventId"].orEmpty()
            if (!eventId.isUuid()) {
                closeWithViolation("Invalid event id")
                return@webSocket
            }
            if (!rateLimiter.allow(key = "event_live_ws:${call.directPeerFingerprint()}:$eventId", limit = 60, windowMs = 60_000L)) {
                closeWithViolation("Too many websocket connections")
                return@webSocket
            }
            val event = eventRepository.findEvent(eventId)
            if (event == null || !event.isAudienceLiveChannelAvailable()) {
                closeWithViolation("Event live channel is unavailable")
                return@webSocket
            }

            logger.info(
                "event.live.ws.connected requestId={} eventId={} peer={}",
                call.callId ?: "n/a",
                eventId,
                call.directPeerFingerprint(),
            )

            sendEnvelope(
                broadcaster.initialSnapshot(
                    eventId = eventId,
                    lineup = lineupRepository.listEventLineup(eventId),
                ),
            )

            try {
                broadcaster.subscribe(eventId).collect { envelope ->
                    sendEnvelope(envelope)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ClosedSendChannelException) {
                logger.info(
                    "event.live.ws.closed_send_channel requestId={} eventId={}",
                    call.callId ?: "n/a",
                    eventId,
                )
            } catch (error: Throwable) {
                logger.warn(
                    "event.live.ws.failure requestId={} eventId={} reason={}",
                    call.callId ?: "n/a",
                    eventId,
                    error.message ?: "unknown",
                )
            } finally {
                logger.info(
                    "event.live.ws.disconnected requestId={} eventId={}",
                    call.callId ?: "n/a",
                    eventId,
                )
            }
        }
    }

    /** Закрывает соединение через понятную policy violation причину. */
    private suspend fun DefaultWebSocketServerSession.closeWithViolation(message: String) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, message))
    }

    /** Отправляет envelope в виде text frame. */
    private suspend fun DefaultWebSocketServerSession.sendEnvelope(
        envelope: EventLiveEnvelope,
    ) {
        send(
            Frame.Text(
                eventLiveJson.encodeToString(
                    EventLiveEnvelope.serializer(),
                    envelope,
                ),
            ),
        )
    }

    /** Проверяет, что событие уже опубликовано и доступно audience live channel-у. */
    private fun com.bam.incomedy.server.db.StoredOrganizerEvent.isAudienceLiveChannelAvailable(): Boolean {
        return status == EventStatus.PUBLISHED.wireName &&
            visibility == EventVisibility.PUBLIC.wireName
    }

    /** Безопасно проверяет UUID path параметр. */
    private fun String.isUuid(): Boolean {
        return runCatching { UUID.fromString(this) }.isSuccess
    }
}

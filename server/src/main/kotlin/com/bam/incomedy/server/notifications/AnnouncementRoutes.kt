package com.bam.incomedy.server.notifications

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.AnnouncementRepository
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.WorkspaceRepository
import com.bam.incomedy.server.http.PayloadTooLargeException
import com.bam.incomedy.server.http.receiveJsonBodyLimited
import com.bam.incomedy.server.observability.DiagnosticsStore
import com.bam.incomedy.server.observability.recordCall
import com.bam.incomedy.server.realtime.EventLiveChannelBroadcaster
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.directPeerFingerprint
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * HTTP surface backend foundation slice-а для organizer announcements/event feed.
 */
object AnnouncementRoutes {
    /** Структурированный logger announcement surface-а. */
    private val logger = LoggerFactory.getLogger(AnnouncementRoutes::class.java)

    /** Максимальный размер request body для публикации announcement-а. */
    private const val MAX_CREATE_ANNOUNCEMENT_REQUEST_BYTES = 8 * 1024

    /** Peer-based limit публичного чтения feed-а, чтобы surface не превращался в log sink. */
    private const val PUBLIC_ANNOUNCEMENT_LIST_PEER_LIMIT = 180

    /** Регистрирует public read и protected create routes для event announcements. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        eventRepository: EventRepository,
        announcementRepository: AnnouncementRepository,
        rateLimiter: AuthRateLimiter,
        eventLiveChannelBroadcaster: EventLiveChannelBroadcaster? = null,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        val service = AnnouncementService(
            workspaceRepository = workspaceRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
        )

        route.route("/api/v1") {
            get("/public/events/{eventId}/announcements") {
                val directPeer = call.directPeerFingerprint()
                val eventId = call.parameters["eventId"]
                if (!rateLimiter.allow(
                        key = "event_public_announcements_list_peer:$directPeer:${eventId.orEmpty()}",
                        limit = PUBLIC_ANNOUNCEMENT_LIST_PEER_LIMIT,
                        windowMs = 60_000L,
                    )
                ) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.announcement.public_list.rate_limited",
                        status = HttpStatusCode.TooManyRequests.value,
                        safeErrorCode = "rate_limited",
                        metadata = mapOf("scope" to "peer"),
                    )
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                    return@get
                }
                if (!eventId.isUuid()) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.announcement.public_list.invalid_event_id",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                    return@get
                }

                try {
                    val announcements = service.listPublicEventAnnouncements(eventId.orEmpty())
                    logger.info(
                        "event.announcement.public_list.success requestId={} eventId={} count={}",
                        call.callId ?: "n/a",
                        eventId,
                        announcements.size,
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.announcement.public_list.success",
                        status = HttpStatusCode.OK.value,
                        metadata = mapOf("count" to announcements.size.toString()),
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        EventAnnouncementListResponse(
                            announcements = announcements.map(EventAnnouncementResponse::fromDomain),
                        ),
                    )
                } catch (error: Throwable) {
                    call.respondAnnouncementPublicReadError(
                        error = error,
                        diagnosticsStore = diagnosticsStore,
                    )
                }
            }

            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                post("/events/{eventId}/announcements") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "organizer_announcement_create:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.announcement.create.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val eventId = call.parameters["eventId"]
                    if (!eventId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.announcement.create.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<CreateEventAnnouncementRequest>(
                        json = notificationsJson,
                        maxBytes = MAX_CREATE_ANNOUNCEMENT_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondAnnouncementRequestDecodeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }

                    try {
                        val announcement = service.createEventAnnouncement(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            message = request.message,
                        )
                        logger.info(
                            "organizer.announcement.create.success requestId={} userId={} eventId={} announcementId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            announcement.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.announcement.create.success",
                            status = HttpStatusCode.Created.value,
                            metadata = mapOf("authorRole" to announcement.authorRole.wireName),
                        )
                        eventLiveChannelBroadcaster?.publishAnnouncementCreated(
                            eventId = eventId.orEmpty(),
                            announcement = announcement,
                            reason = "organizer_announcement_created",
                        )
                        call.respond(
                            HttpStatusCode.Created,
                            EventAnnouncementResponse.fromDomain(announcement),
                        )
                    } catch (error: Throwable) {
                        call.respondAnnouncementCreateError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }
            }
        }
    }

    /** Единообразно обрабатывает decode/payload ошибки create route-а. */
    private suspend fun ApplicationCall.respondAnnouncementRequestDecodeError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        if (error is PayloadTooLargeException) {
            diagnosticsStore?.recordCall(
                call = this,
                stage = "organizer.announcement.create.payload_too_large",
                status = HttpStatusCode.PayloadTooLarge.value,
                safeErrorCode = "payload_too_large",
            )
            respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Request body is too large"))
            return
        }
        diagnosticsStore?.recordCall(
            call = this,
            stage = "organizer.announcement.create.invalid_payload",
            status = HttpStatusCode.BadRequest.value,
            safeErrorCode = "bad_request",
        )
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid announcement request"))
    }

    /** Единообразно обрабатывает public read ошибки feed-а. */
    private suspend fun ApplicationCall.respondAnnouncementPublicReadError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        when (error) {
            is AnnouncementEventNotFoundException,
            is AnnouncementFeedUnavailableException,
            -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "event.announcement.public_list.not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Announcement feed is unavailable"))
            }

            else -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "event.announcement.public_list.failed",
                    status = HttpStatusCode.InternalServerError.value,
                    safeErrorCode = "internal_error",
                )
                respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Unexpected server error"))
            }
        }
    }

    /** Единообразно обрабатывает protected create ошибки feed-а. */
    private suspend fun ApplicationCall.respondAnnouncementCreateError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        when (error) {
            is AnnouncementEventNotFoundException,
            is AnnouncementScopeNotFoundException,
            -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.announcement.create.not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Event or workspace scope not found"))
            }

            is AnnouncementPermissionDeniedException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.announcement.create.forbidden",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = error.safeCode,
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse(error.safeCode, "Announcement management is forbidden"))
            }

            is AnnouncementFeedUnavailableException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.announcement.create.feed_unavailable",
                    status = HttpStatusCode.Conflict.value,
                    safeErrorCode = "invalid_state",
                )
                respond(HttpStatusCode.Conflict, ErrorResponse("invalid_state", "Announcement feed is unavailable for this event"))
            }

            is AnnouncementValidationException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.announcement.create.invalid_request",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "bad_request",
                )
                respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", error.safeMessage))
            }

            else -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.announcement.create.failed",
                    status = HttpStatusCode.InternalServerError.value,
                    safeErrorCode = "internal_error",
                )
                respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Unexpected server error"))
            }
        }
    }

    /** Безопасно проверяет UUID path-параметр. */
    private fun String?.isUuid(): Boolean {
        return this != null && runCatching { UUID.fromString(this) }.isSuccess
    }
}

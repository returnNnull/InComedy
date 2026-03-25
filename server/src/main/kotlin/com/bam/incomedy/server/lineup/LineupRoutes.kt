package com.bam.incomedy.server.lineup

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.LineupRepository
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.WorkspaceRepository
import com.bam.incomedy.server.http.PayloadTooLargeException
import com.bam.incomedy.server.http.receiveJsonBodyLimited
import com.bam.incomedy.server.observability.DiagnosticsStore
import com.bam.incomedy.server.observability.recordCall
import com.bam.incomedy.server.security.AuthRateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * HTTP surface backend lineup foundation slice-а.
 *
 * Роуты отдают organizer/host список lineup entries, позволяют безопасно переставлять полный
 * lineup события и переводить отдельную запись между live-stage статусами. Realtime delivery и
 * comedian-facing read surfaces остаются вне этого шага.
 */
object LineupRoutes {
    /** Структурированный logger lineup surface. */
    private val logger = LoggerFactory.getLogger(LineupRoutes::class.java)

    /** Максимальный размер request body для reorder lineup. */
    private const val MAX_REORDER_LINEUP_REQUEST_BYTES = 8 * 1024

    /** Максимальный размер request body для live-state mutation. */
    private const val MAX_LIVE_STATE_REQUEST_BYTES = 4 * 1024

    /** Регистрирует protected organizer lineup routes. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        eventRepository: EventRepository,
        lineupRepository: LineupRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        val service = LineupService(
            workspaceRepository = workspaceRepository,
            eventRepository = eventRepository,
            lineupRepository = lineupRepository,
        )

        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                get("/events/{eventId}/lineup") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "organizer_lineup_list:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.list.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }
                    val eventId = call.parameters["eventId"]
                    if (!eventId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.list.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@get
                    }

                    try {
                        val lineup = service.listEventLineup(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "organizer.lineup.list.success requestId={} userId={} eventId={} count={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            lineup.size,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.list.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("count" to lineup.size.toString()),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            LineupListResponse(
                                entries = lineup.map(LineupEntryResponse::fromStored),
                            ),
                        )
                    } catch (error: Throwable) {
                        call.respondLineupError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                            flow = "organizer.lineup.list",
                        )
                    }
                }

                patch("/events/{eventId}/lineup") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "organizer_lineup_reorder:${principal.user.id}", limit = 60, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.reorder.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@patch
                    }
                    val eventId = call.parameters["eventId"]
                    if (!eventId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.reorder.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@patch
                    }
                    val request = call.receiveJsonBodyLimited<ReorderLineupRequest>(
                        json = lineupJson,
                        maxBytes = MAX_REORDER_LINEUP_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondLineupDecodeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@patch
                    }

                    try {
                        val lineup = service.reorderEventLineup(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            updates = request.toOrderUpdates(),
                        )
                        logger.info(
                            "organizer.lineup.reorder.success requestId={} userId={} eventId={} count={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            lineup.size,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.reorder.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("count" to lineup.size.toString()),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            LineupListResponse(
                                entries = lineup.map(LineupEntryResponse::fromStored),
                            ),
                        )
                    } catch (error: Throwable) {
                        call.respondLineupError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                            flow = "organizer.lineup.reorder",
                        )
                    }
                }

                post("/events/{eventId}/lineup/live-state") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "organizer_lineup_live_state:${principal.user.id}", limit = 60, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.live_state.rate_limited",
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
                            stage = "organizer.lineup.live_state.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<UpdateLineupLiveStateRequest>(
                        json = lineupJson,
                        maxBytes = MAX_LIVE_STATE_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondLineupLiveStateDecodeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }
                    if (!request.entryId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.live_state.invalid_entry_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid lineup entry id"))
                        return@post
                    }
                    val targetStatus = request.toTargetStatusOrNull()
                    if (targetStatus == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.live_state.invalid_status",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid lineup live-state status"))
                        return@post
                    }

                    try {
                        val lineup = service.updateLineupEntryStatus(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            entryId = request.entryId,
                            status = targetStatus,
                        )
                        logger.info(
                            "organizer.lineup.live_state.success requestId={} userId={} eventId={} entryId={} status={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            request.entryId,
                            targetStatus.wireName,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.lineup.live_state.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf(
                                "entryId" to request.entryId,
                                "status" to targetStatus.wireName,
                            ),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            LineupListResponse(
                                entries = lineup.map(LineupEntryResponse::fromStored),
                            ),
                        )
                    } catch (error: Throwable) {
                        call.respondLineupError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                            flow = "organizer.lineup.live_state",
                        )
                    }
                }
            }
        }
    }

    /** Единообразно обрабатывает decode/payload ошибки lineup routes. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondLineupDecodeError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        if (error is PayloadTooLargeException) {
            diagnosticsStore?.recordCall(
                call = this,
                stage = "organizer.lineup.reorder.payload_too_large",
                status = HttpStatusCode.PayloadTooLarge.value,
                safeErrorCode = "payload_too_large",
            )
            respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Request body is too large"))
            return
        }
        diagnosticsStore?.recordCall(
            call = this,
            stage = "organizer.lineup.reorder.invalid_payload",
            status = HttpStatusCode.BadRequest.value,
            safeErrorCode = "bad_request",
        )
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid lineup reorder request"))
    }

    /** Единообразно обрабатывает decode/payload ошибки live-state route-а. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondLineupLiveStateDecodeError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        if (error is PayloadTooLargeException) {
            diagnosticsStore?.recordCall(
                call = this,
                stage = "organizer.lineup.live_state.payload_too_large",
                status = HttpStatusCode.PayloadTooLarge.value,
                safeErrorCode = "payload_too_large",
            )
            respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Request body is too large"))
            return
        }
        diagnosticsStore?.recordCall(
            call = this,
            stage = "organizer.lineup.live_state.invalid_payload",
            status = HttpStatusCode.BadRequest.value,
            safeErrorCode = "bad_request",
        )
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid lineup live-state request"))
    }

    /** Маппит domain/service ошибки lineup slice-а в безопасные HTTP responses. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondLineupError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
        flow: String,
    ) {
        when (error) {
            is LineupEventNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$flow.event_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "event_not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("event_not_found", "Event not found"))
            }

            is LineupEntryNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$flow.entry_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "lineup_entry_not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("lineup_entry_not_found", "Lineup entry not found"))
            }

            is LineupScopeNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$flow.scope_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "workspace_scope_not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("workspace_scope_not_found", "Workspace scope not found"))
            }

            is LineupPermissionDeniedException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$flow.forbidden",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = error.reasonCode,
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse(error.reasonCode, "Lineup action is forbidden"))
            }

            is LineupValidationException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$flow.validation_failed",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "bad_request",
                )
                respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", error.message))
            }

            else -> throw error
        }
    }

    /** Валидирует UUID path parameter без дублирования regex по файлу. */
    private fun String?.isUuid(): Boolean {
        val value = this ?: return false
        return runCatching { UUID.fromString(value) }.isSuccess
    }
}

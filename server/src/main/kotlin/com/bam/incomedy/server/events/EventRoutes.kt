package com.bam.incomedy.server.events

import com.bam.incomedy.domain.event.EventDraftValidator
import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.VenueRepository
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
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Organizer event management routes.
 *
 * Поверхность покрывает bounded foundation slice `create/list/publish` и хранит frozen
 * `EventHallSnapshot`, не смешивая event lifecycle с будущим ticketing/inventory flow.
 */
object EventRoutes {
    /** Структурированный logger organizer event surface. */
    private val logger = LoggerFactory.getLogger(EventRoutes::class.java)

    /** Максимальный размер request тела создания organizer event. */
    private const val MAX_CREATE_EVENT_REQUEST_BYTES = 16 * 1024

    /** Регистрирует organizer event routes внутри защищенного session scope. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        venueRepository: VenueRepository,
        eventRepository: EventRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        val eventService = OrganizerEventService(
            workspaceRepository = workspaceRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
        )

        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                get("/events") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "events_list:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.events.list.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }

                    val events = eventService.listEvents(principal.user.id)
                        .map(OrganizerEventResponse::fromStored)
                    logger.info(
                        "organizer.events.list.success requestId={} userId={} count={}",
                        call.callId ?: "n/a",
                        principal.user.id,
                        events.size,
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "organizer.events.list.success",
                        status = HttpStatusCode.OK.value,
                        metadata = mapOf("count" to events.size.toString()),
                    )
                    call.respond(HttpStatusCode.OK, EventListResponse(events))
                }

                post("/events") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "event_create:${principal.user.id}", limit = 40, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.create.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }

                    val request = call.receiveJsonBodyLimited<CreateEventRequest>(
                        json = eventJson,
                        maxBytes = MAX_CREATE_EVENT_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondEventRequestError(
                            error = error,
                            payloadTooLargeStage = "organizer.event.create.payload_too_large",
                            invalidPayloadStage = "organizer.event.create.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid event request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }

                    if (!request.workspaceId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.create.invalid_workspace_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace id"))
                        return@post
                    }
                    if (!request.venueId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.create.invalid_venue_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid venue id"))
                        return@post
                    }
                    if (!request.hallTemplateId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.create.invalid_template_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid hall template id"))
                        return@post
                    }

                    val draft = request.toDomain()
                    val validationError = EventDraftValidator.validateEventDraft(draft)
                    if (validationError != null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.create.validation_failed",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", validationError))
                        return@post
                    }

                    try {
                        val event = eventService.createEvent(
                            actorUserId = principal.user.id,
                            draft = draft,
                        )
                        logger.info(
                            "organizer.event.create.success requestId={} userId={} eventId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            event.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.create.success",
                            status = HttpStatusCode.Created.value,
                            metadata = mapOf("status" to event.status),
                        )
                        call.respond(HttpStatusCode.Created, OrganizerEventResponse.fromStored(event))
                    } catch (error: Throwable) {
                        call.respondEventScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/events/{eventId}/publish") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "event_publish:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.publish.rate_limited",
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
                            stage = "organizer.event.publish.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }

                    try {
                        val event = eventService.publishEvent(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "organizer.event.publish.success requestId={} userId={} eventId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            event.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.publish.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("status" to event.status),
                        )
                        call.respond(HttpStatusCode.OK, OrganizerEventResponse.fromStored(event))
                    } catch (error: Throwable) {
                        call.respondEventScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }
            }
        }
    }

    /** Унифицирует обработку payload/body ошибок organizer event surface. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondEventRequestError(
        error: Throwable,
        payloadTooLargeStage: String,
        invalidPayloadStage: String,
        payloadTooLargeMessage: String,
        invalidPayloadMessage: String,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        if (error is PayloadTooLargeException) {
            diagnosticsStore?.recordCall(
                call = this,
                stage = payloadTooLargeStage,
                status = HttpStatusCode.PayloadTooLarge.value,
                safeErrorCode = "payload_too_large",
            )
            respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", payloadTooLargeMessage))
            return
        }
        diagnosticsStore?.recordCall(
            call = this,
            stage = invalidPayloadStage,
            status = HttpStatusCode.BadRequest.value,
            safeErrorCode = "bad_request",
        )
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", invalidPayloadMessage))
    }

    /** Унифицирует not-found/forbidden/validation ошибки organizer event bounded context-а. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondEventScopeError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        when (error) {
            is EventScopeNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.event.scope_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Organizer workspace scope was not found"))
            }

            is EventVenueNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.event.venue_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Venue was not found"))
            }

            is EventTemplateNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.event.template_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Hall template was not found"))
            }

            is EventNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.event.not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Event was not found"))
            }

            is EventPermissionDeniedException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.event.permission_denied",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = error.reasonCode,
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse(error.reasonCode, "Event action is forbidden"))
            }

            is EventVenueScopeMismatchException,
            is EventTemplateVenueMismatchException,
            is EventValidationException,
            -> {
                val safeMessage = when (error) {
                    is EventValidationException -> error.safeMessage
                    is EventVenueScopeMismatchException -> "Площадка не принадлежит выбранному workspace"
                    is EventTemplateVenueMismatchException -> "Шаблон зала не принадлежит выбранной площадке"
                    else -> "Некорректный запрос события"
                }
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.event.validation_failed",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "bad_request",
                )
                respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", safeMessage))
            }

            else -> throw error
        }
    }

    /** Проверяет UUID-совместимость строкового идентификатора. */
    private fun String?.isUuid(): Boolean {
        val value = this ?: return false
        return runCatching { UUID.fromString(value) }.isSuccess
    }
}

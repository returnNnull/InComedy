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
import com.bam.incomedy.server.security.directPeerFingerprint
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
import java.time.LocalDate
import java.util.UUID

/**
 * Organizer event management routes plus public audience discovery surface.
 *
 * Поверхность покрывает bounded organizer slice `create/list/get/update/publish`, sales
 * open/pause/cancel controls, хранит frozen `EventHallSnapshot` и event-local overrides, а также
 * отдает audience-safe public discovery catalog без organizer-only полей.
 */
object EventRoutes {
    /** Структурированный logger organizer event surface. */
    private val logger = LoggerFactory.getLogger(EventRoutes::class.java)

    /** Максимальный размер request тела создания organizer event. */
    private const val MAX_CREATE_EVENT_REQUEST_BYTES = 16 * 1024

    /** Максимальный размер request тела обновления organizer event details. */
    private const val MAX_UPDATE_EVENT_REQUEST_BYTES = 32 * 1024

    /** Peer-based лимит публичного discovery route-а, чтобы catalog surface не превратился в log sink. */
    private const val PUBLIC_EVENT_DISCOVERY_PEER_LIMIT = 240

    /** Регистрирует organizer event routes и публичный audience discovery surface. */
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
        val publicEventDiscoveryService = PublicEventDiscoveryService(
            eventRepository = eventRepository,
            venueRepository = venueRepository,
        )

        route.route("/api/v1") {
            /** Публичный discovery-каталог опубликованных public-событий для audience surface. */
            get("/public/events") {
                val directPeer = call.directPeerFingerprint()
                if (!rateLimiter.allow(
                        key = "event_public_list_peer:$directPeer",
                        limit = PUBLIC_EVENT_DISCOVERY_PEER_LIMIT,
                        windowMs = 60_000L,
                    )
                ) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.public_list.rate_limited",
                        status = HttpStatusCode.TooManyRequests.value,
                        safeErrorCode = "rate_limited",
                        metadata = mapOf("scope" to "peer"),
                    )
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                    return@get
                }

                val city = call.request.queryParameters["city"]?.trim()?.takeIf(String::isNotBlank)
                val dateFrom = parseOptionalLocalDate(call.request.queryParameters["date_from"]).getOrElse {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.public_list.invalid_date_from",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid date_from"))
                    return@get
                }
                val dateTo = parseOptionalLocalDate(call.request.queryParameters["date_to"]).getOrElse {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.public_list.invalid_date_to",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid date_to"))
                    return@get
                }
                val priceMinMinor = parseOptionalPositiveInt(call.request.queryParameters["price_min_minor"]).getOrElse {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.public_list.invalid_price_min_minor",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid price_min_minor"))
                    return@get
                }
                val priceMaxMinor = parseOptionalPositiveInt(call.request.queryParameters["price_max_minor"]).getOrElse {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.public_list.invalid_price_max_minor",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid price_max_minor"))
                    return@get
                }

                if (dateFrom != null && dateTo != null && dateFrom > dateTo) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.public_list.invalid_date_range",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("bad_request", "date_from must be on or before date_to"),
                    )
                    return@get
                }
                if (priceMinMinor != null && priceMaxMinor != null && priceMinMinor > priceMaxMinor) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "event.public_list.invalid_price_range",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("bad_request", "price_min_minor must be less than or equal to price_max_minor"),
                    )
                    return@get
                }

                val events = publicEventDiscoveryService.listPublicEvents(
                    query = PublicEventDiscoveryQuery(
                        city = city,
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        priceMinMinor = priceMinMinor,
                        priceMaxMinor = priceMaxMinor,
                    ),
                )
                logger.info(
                    "event.public_list.success requestId={} count={} hasCityFilter={} hasDateFilter={} hasPriceFilter={}",
                    call.callId ?: "n/a",
                    events.size,
                    city != null,
                    dateFrom != null || dateTo != null,
                    priceMinMinor != null || priceMaxMinor != null,
                )
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "event.public_list.success",
                    status = HttpStatusCode.OK.value,
                    metadata = mapOf(
                        "count" to events.size.toString(),
                        "hasCityFilter" to (city != null).toString(),
                        "hasDateFilter" to (dateFrom != null || dateTo != null).toString(),
                        "hasPriceFilter" to (priceMinMinor != null || priceMaxMinor != null).toString(),
                    ),
                )
                call.respond(
                    HttpStatusCode.OK,
                    PublicEventListResponse(
                        events = events.map(PublicEventSummaryResponse::fromView),
                    ),
                )
            }

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

                get("/events/{eventId}") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "event_get:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.get.rate_limited",
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
                            stage = "organizer.event.get.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@get
                    }

                    try {
                        val event = eventService.getEvent(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "organizer.event.get.success requestId={} userId={} eventId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            event.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.get.success",
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

                patch("/events/{eventId}") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "event_update:${principal.user.id}", limit = 40, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.update.rate_limited",
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
                            stage = "organizer.event.update.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@patch
                    }

                    val request = call.receiveJsonBodyLimited<UpdateEventRequest>(
                        json = eventJson,
                        maxBytes = MAX_UPDATE_EVENT_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondEventRequestError(
                            error = error,
                            payloadTooLargeStage = "organizer.event.update.payload_too_large",
                            invalidPayloadStage = "organizer.event.update.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid event update request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@patch
                    }

                    val draft = try {
                        request.toDomain()
                    } catch (_: IllegalArgumentException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.update.invalid_payload",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event update request"))
                        return@patch
                    }

                    try {
                        val event = eventService.updateEvent(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            draft = draft,
                        )
                        logger.info(
                            "organizer.event.update.success requestId={} userId={} eventId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            event.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.update.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf(
                                "priceZones" to event.priceZones.size.toString(),
                                "availabilityOverrides" to event.availabilityOverrides.size.toString(),
                            ),
                        )
                        call.respond(HttpStatusCode.OK, OrganizerEventResponse.fromStored(event))
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

                post("/events/{eventId}/sales/open") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "event_sales_open:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.sales_open.rate_limited",
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
                            stage = "organizer.event.sales_open.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }

                    try {
                        val event = eventService.openEventSales(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "organizer.event.sales_open.success requestId={} userId={} eventId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            event.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.sales_open.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf(
                                "status" to event.status,
                                "salesStatus" to event.salesStatus,
                            ),
                        )
                        call.respond(HttpStatusCode.OK, OrganizerEventResponse.fromStored(event))
                    } catch (error: Throwable) {
                        call.respondEventScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/events/{eventId}/sales/pause") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "event_sales_pause:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.sales_pause.rate_limited",
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
                            stage = "organizer.event.sales_pause.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }

                    try {
                        val event = eventService.pauseEventSales(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "organizer.event.sales_pause.success requestId={} userId={} eventId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            event.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.sales_pause.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf(
                                "status" to event.status,
                                "salesStatus" to event.salesStatus,
                            ),
                        )
                        call.respond(HttpStatusCode.OK, OrganizerEventResponse.fromStored(event))
                    } catch (error: Throwable) {
                        call.respondEventScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/events/{eventId}/cancel") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "event_cancel:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.cancel.rate_limited",
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
                            stage = "organizer.event.cancel.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }

                    try {
                        val event = eventService.cancelEvent(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "organizer.event.cancel.success requestId={} userId={} eventId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            event.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.event.cancel.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf(
                                "status" to event.status,
                                "salesStatus" to event.salesStatus,
                            ),
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

    /** Парсит необязательный `YYYY-MM-DD` query parameter discovery route-а. */
    private fun parseOptionalLocalDate(
        rawValue: String?,
    ): Result<LocalDate?> {
        val normalized = rawValue?.trim()?.takeIf(String::isNotBlank) ?: return Result.success(null)
        return runCatching { LocalDate.parse(normalized) }
    }

    /** Парсит необязательный non-negative integer query parameter discovery route-а. */
    private fun parseOptionalPositiveInt(
        rawValue: String?,
    ): Result<Int?> {
        val normalized = rawValue?.trim()?.takeIf(String::isNotBlank) ?: return Result.success(null)
        return runCatching {
            normalized.toInt().also { value ->
                require(value >= 0)
            }
        }
    }
}

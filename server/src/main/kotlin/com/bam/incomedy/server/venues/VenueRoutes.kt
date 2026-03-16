package com.bam.incomedy.server.venues

import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.VenueDraftValidator
import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
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
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlinx.serialization.encodeToString
import java.time.ZoneId

/**
 * Organizer venue management routes.
 *
 * Поверхность покрывает первый bounded slice `Venue CRUD + HallTemplate CRUD/clone` и использует
 * ту же session auth protection, что и уже существующие organizer endpoints.
 */
object VenueRoutes {
    /** Структурированный logger organizer venue surface. */
    private val logger = LoggerFactory.getLogger(VenueRoutes::class.java)

    /** Максимальный размер create venue request. */
    private const val MAX_CREATE_VENUE_REQUEST_BYTES = 8 * 1024

    /** Максимальный размер create/update hall template request. */
    private const val MAX_TEMPLATE_MUTATION_REQUEST_BYTES = 64 * 1024

    /** Максимальный размер clone request. */
    private const val MAX_CLONE_TEMPLATE_REQUEST_BYTES = 2 * 1024

    /** Регистрирует organizer venue routes внутри защищенного session scope. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        venueRepository: VenueRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        val venueService = OrganizerVenueService(
            workspaceRepository = workspaceRepository,
            venueRepository = venueRepository,
        )

        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                get("/venues") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "venues_list:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.venues.list.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }
                    val venues = venueService.listVenues(principal.user.id)
                        .map(VenueResponse::fromStored)
                    logger.info(
                        "organizer.venues.list.success requestId={} userId={} count={}",
                        call.callId ?: "n/a",
                        principal.user.id,
                        venues.size,
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "organizer.venues.list.success",
                        status = HttpStatusCode.OK.value,
                        metadata = mapOf("count" to venues.size.toString()),
                    )
                    call.respond(HttpStatusCode.OK, VenueListResponse(venues))
                }

                post("/venues") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "venue_create:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.venue.create.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<CreateVenueRequest>(
                        json = venueJson,
                        maxBytes = MAX_CREATE_VENUE_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondVenueRequestError(
                            error = error,
                            payloadTooLargeStage = "organizer.venue.create.payload_too_large",
                            invalidPayloadStage = "organizer.venue.create.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid venue request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }
                    val draft = request.toDomain()
                    val validationError = VenueDraftValidator.validateVenueDraft(draft)
                    if (validationError != null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.venue.create.validation_failed",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", validationError))
                        return@post
                    }
                    if (!isValidTimezone(draft.timezone)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.venue.create.invalid_timezone",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Некорректная timezone площадки"))
                        return@post
                    }
                    try {
                        val venue = venueService.createVenue(
                            actorUserId = principal.user.id,
                            workspaceId = draft.workspaceId,
                            name = draft.name.trim(),
                            city = draft.city.trim(),
                            address = draft.address.trim(),
                            timezone = draft.timezone.trim(),
                            capacity = draft.capacity,
                            description = draft.description?.trim()?.takeIf(String::isNotBlank),
                            contactsJson = venueJson.encodeToString(request.contacts),
                        )
                        logger.info(
                            "organizer.venue.create.success requestId={} userId={} venueId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            venue.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.venue.create.success",
                            status = HttpStatusCode.Created.value,
                        )
                        call.respond(HttpStatusCode.Created, VenueResponse.fromStored(venue))
                    } catch (error: Throwable) {
                        call.respondVenueScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/venues/{venueId}/hall-templates") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "hall_template_create:${principal.user.id}", limit = 40, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.create.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val venueId = call.parameters["venueId"]
                    if (!venueId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.create.invalid_venue_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid venue id"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<HallTemplateMutationRequest>(
                        json = venueJson,
                        maxBytes = MAX_TEMPLATE_MUTATION_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondVenueRequestError(
                            error = error,
                            payloadTooLargeStage = "organizer.hall_template.create.payload_too_large",
                            invalidPayloadStage = "organizer.hall_template.create.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid hall template request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }
                    val draft = request.toDomain()
                    val validationError = VenueDraftValidator.validateHallTemplateDraft(draft)
                    if (validationError != null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.create.validation_failed",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", validationError))
                        return@post
                    }
                    try {
                        val template = venueService.createHallTemplate(
                            actorUserId = principal.user.id,
                            venueId = venueId.orEmpty(),
                            name = draft.name.trim(),
                            status = draft.status.wireName,
                            layoutJson = venueJson.encodeToString(request.layout),
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.create.success",
                            status = HttpStatusCode.Created.value,
                            metadata = mapOf("status" to draft.status.wireName),
                        )
                        call.respond(HttpStatusCode.Created, HallTemplateResponse.fromStored(template))
                    } catch (error: Throwable) {
                        call.respondVenueScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                patch("/hall-templates/{templateId}") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "hall_template_update:${principal.user.id}", limit = 50, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.update.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@patch
                    }
                    val templateId = call.parameters["templateId"]
                    if (!templateId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.update.invalid_template_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid hall template id"))
                        return@patch
                    }
                    val request = call.receiveJsonBodyLimited<HallTemplateMutationRequest>(
                        json = venueJson,
                        maxBytes = MAX_TEMPLATE_MUTATION_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondVenueRequestError(
                            error = error,
                            payloadTooLargeStage = "organizer.hall_template.update.payload_too_large",
                            invalidPayloadStage = "organizer.hall_template.update.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid hall template update request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@patch
                    }
                    val draft = request.toDomain()
                    val validationError = VenueDraftValidator.validateHallTemplateDraft(draft)
                    if (validationError != null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.update.validation_failed",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", validationError))
                        return@patch
                    }
                    try {
                        val template = venueService.updateHallTemplate(
                            actorUserId = principal.user.id,
                            templateId = templateId.orEmpty(),
                            name = draft.name.trim(),
                            status = draft.status.wireName,
                            layoutJson = venueJson.encodeToString(request.layout),
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.update.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("status" to draft.status.wireName),
                        )
                        call.respond(HttpStatusCode.OK, HallTemplateResponse.fromStored(template))
                    } catch (error: Throwable) {
                        call.respondVenueScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/hall-templates/{templateId}/clone") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "hall_template_clone:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.clone.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val templateId = call.parameters["templateId"]
                    if (!templateId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.clone.invalid_template_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid hall template id"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<CloneHallTemplateRequest>(
                        json = venueJson,
                        maxBytes = MAX_CLONE_TEMPLATE_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondVenueRequestError(
                            error = error,
                            payloadTooLargeStage = "organizer.hall_template.clone.payload_too_large",
                            invalidPayloadStage = "organizer.hall_template.clone.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid hall template clone request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }
                    try {
                        val template = venueService.cloneHallTemplate(
                            actorUserId = principal.user.id,
                            templateId = templateId.orEmpty(),
                            clonedName = request.name?.trim(),
                            clonedStatus = HallTemplateStatus.DRAFT.wireName,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.hall_template.clone.success",
                            status = HttpStatusCode.Created.value,
                        )
                        call.respond(HttpStatusCode.Created, HallTemplateResponse.fromStored(template))
                    } catch (error: Throwable) {
                        call.respondVenueScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }
            }
        }
    }

    /** Унифицирует обработку payload/body ошибок organizer venue surface. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondVenueRequestError(
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

    /** Унифицирует not-found/forbidden ошибки venue bounded context-а. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondVenueScopeError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        when (error) {
            is VenueScopeNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.venue.scope_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Organizer workspace scope was not found"))
            }

            is VenueNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.venue.not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Venue was not found"))
            }

            is HallTemplateNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.hall_template.not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Hall template was not found"))
            }

            is VenuePermissionDeniedException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "organizer.venue.permission_denied",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = error.reasonCode,
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse(error.reasonCode, "Venue action is forbidden"))
            }

            else -> throw error
        }
    }

    /** Проверяет UUID-совместимость параметра пути. */
    private fun String?.isUuid(): Boolean {
        val value = this ?: return false
        return runCatching { UUID.fromString(value) }.isSuccess
    }

    /** Выполняет строгую JVM-проверку IANA timezone для backend route validation. */
    private fun isValidTimezone(value: String): Boolean {
        return runCatching { ZoneId.of(value.trim()) }.isSuccess
    }
}

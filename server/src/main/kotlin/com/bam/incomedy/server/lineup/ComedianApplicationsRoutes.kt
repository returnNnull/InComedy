package com.bam.incomedy.server.lineup

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.ComedianApplicationRepository
import com.bam.incomedy.server.db.ComedianApplicationStatus
import com.bam.incomedy.server.db.EventRepository
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
 * HTTP surface первого backend slice-а `comedian applications`.
 *
 * Роуты покрывают ровно foundation-запросы текущего эпика: submit заявки комиком, organizer list
 * и organizer status change. UI, lineup ordering и live stage status остаются вне этого файла.
 */
object ComedianApplicationsRoutes {
    /** Структурированный logger applications surface. */
    private val logger = LoggerFactory.getLogger(ComedianApplicationsRoutes::class.java)

    /** Максимальный размер submit body с заметкой комика. */
    private const val MAX_SUBMIT_REQUEST_BYTES = 4 * 1024

    /** Максимальный размер request body смены review-статуса. */
    private const val MAX_UPDATE_STATUS_REQUEST_BYTES = 2 * 1024

    /** Регистрирует routes comedian applications foundation slice-а. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        eventRepository: EventRepository,
        comedianApplicationRepository: ComedianApplicationRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        val service = ComedianApplicationsService(
            sessionUserRepository = sessionUserRepository,
            workspaceRepository = workspaceRepository,
            eventRepository = eventRepository,
            comedianApplicationRepository = comedianApplicationRepository,
        )

        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                post("/events/{eventId}/applications") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "comedian_application_submit:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.application.submit.rate_limited",
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
                            stage = "comedian.application.submit.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<SubmitComedianApplicationRequest>(
                        json = comedianApplicationsJson,
                        maxBytes = MAX_SUBMIT_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondRequestDecodeError(
                            error = error,
                            payloadTooLargeStage = "comedian.application.submit.payload_too_large",
                            invalidPayloadStage = "comedian.application.submit.invalid_payload",
                            invalidPayloadMessage = "Invalid comedian application request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }
                    val note = request.note?.trim()?.takeIf(String::isNotBlank)
                    if (note != null && note.length > 1_000) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.application.submit.invalid_note_length",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Application note must be at most 1000 chars"))
                        return@post
                    }

                    try {
                        val application = service.submitApplication(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            note = note,
                        )
                        logger.info(
                            "comedian.application.submit.success requestId={} userId={} eventId={} applicationId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            application.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.application.submit.success",
                            status = HttpStatusCode.Created.value,
                            metadata = mapOf("status" to application.status.wireName),
                        )
                        call.respond(HttpStatusCode.Created, ComedianApplicationResponse.fromStored(application))
                    } catch (error: Throwable) {
                        call.respondComedianApplicationError(
                            error = error,
                            submitFlow = true,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                get("/events/{eventId}/applications") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "comedian_application_list:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.comedian_application.list.rate_limited",
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
                            stage = "organizer.comedian_application.list.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@get
                    }

                    try {
                        val applications = service.listEventApplications(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "organizer.comedian_application.list.success requestId={} userId={} eventId={} count={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            applications.size,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.comedian_application.list.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("count" to applications.size.toString()),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            ComedianApplicationListResponse(
                                applications = applications.map(ComedianApplicationResponse::fromStored),
                            ),
                        )
                    } catch (error: Throwable) {
                        call.respondComedianApplicationError(
                            error = error,
                            submitFlow = false,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                patch("/events/{eventId}/applications/{applicationId}") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "comedian_application_update:${principal.user.id}", limit = 60, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.comedian_application.update.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@patch
                    }
                    val eventId = call.parameters["eventId"]
                    val applicationId = call.parameters["applicationId"]
                    if (!eventId.isUuid() || !applicationId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.comedian_application.update.invalid_path",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid comedian application path"))
                        return@patch
                    }
                    val request = call.receiveJsonBodyLimited<UpdateComedianApplicationStatusRequest>(
                        json = comedianApplicationsJson,
                        maxBytes = MAX_UPDATE_STATUS_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondRequestDecodeError(
                            error = error,
                            payloadTooLargeStage = "organizer.comedian_application.update.payload_too_large",
                            invalidPayloadStage = "organizer.comedian_application.update.invalid_payload",
                            invalidPayloadMessage = "Invalid comedian application update request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@patch
                    }
                    val status = ComedianApplicationStatus.fromWireName(request.status.trim())
                    if (status == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.comedian_application.update.invalid_status",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unknown comedian application status"))
                        return@patch
                    }

                    try {
                        val application = service.updateApplicationStatus(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            applicationId = applicationId.orEmpty(),
                            status = status,
                        )
                        logger.info(
                            "organizer.comedian_application.update.success requestId={} userId={} eventId={} applicationId={} status={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            application.id,
                            application.status.wireName,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.comedian_application.update.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("status" to application.status.wireName),
                        )
                        call.respond(HttpStatusCode.OK, ComedianApplicationResponse.fromStored(application))
                    } catch (error: Throwable) {
                        call.respondComedianApplicationError(
                            error = error,
                            submitFlow = false,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }
            }
        }
    }

    /** Обрабатывает ошибки разбора request body единообразно для applications routes. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondRequestDecodeError(
        error: Throwable,
        payloadTooLargeStage: String,
        invalidPayloadStage: String,
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
            respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Request body is too large"))
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

    /** Маппит domain/service ошибки applications slice-а в безопасные HTTP responses. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondComedianApplicationError(
        error: Throwable,
        submitFlow: Boolean,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        val scopePrefix = if (submitFlow) "comedian.application.submit" else "organizer.comedian_application"
        when (error) {
            is ComedianApplicationEventNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$scopePrefix.event_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "event_not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("event_not_found", "Event not found"))
            }

            is ComedianApplicationScopeNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$scopePrefix.scope_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "workspace_scope_not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("workspace_scope_not_found", "Workspace scope not found"))
            }

            is ComedianApplicationNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$scopePrefix.application_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "application_not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("application_not_found", "Comedian application not found"))
            }

            is ComedianApplicationPermissionDeniedException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$scopePrefix.forbidden",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = error.reasonCode,
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse(error.reasonCode, "Comedian application action is forbidden"))
            }

            is ComedianApplicationConflictException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$scopePrefix.conflict",
                    status = HttpStatusCode.Conflict.value,
                    safeErrorCode = error.reasonCode,
                )
                respond(HttpStatusCode.Conflict, ErrorResponse(error.reasonCode, "Comedian application state conflict"))
            }

            is ComedianApplicationValidationException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "$scopePrefix.validation_failed",
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

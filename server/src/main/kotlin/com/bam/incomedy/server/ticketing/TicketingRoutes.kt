package com.bam.incomedy.server.ticketing

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.TicketingRepository
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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Ticketing foundation routes.
 *
 * Поверхность ограничена inventory list и hold create/release, работает поверх session auth и не
 * тянет вперед checkout, orders, QR issuance или check-in.
 */
object TicketingRoutes {
    /** Структурированный logger ticketing foundation surface. */
    private val logger = LoggerFactory.getLogger(TicketingRoutes::class.java)

    /** Максимальный размер request тела создания hold-а. */
    private const val MAX_CREATE_HOLD_REQUEST_BYTES = 4 * 1024

    /** Регистрирует защищенные ticketing foundation routes. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        eventRepository: EventRepository,
        ticketingRepository: TicketingRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
        nowProvider: () -> OffsetDateTime = { OffsetDateTime.now() },
        holdTtl: Duration = Duration.ofMinutes(10),
    ) {
        val ticketingService = EventTicketingService(
            workspaceRepository = workspaceRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            nowProvider = nowProvider,
            holdTtl = holdTtl,
        )

        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                get("/events/{eventId}/inventory") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "ticketing_inventory:${principal.user.id}", limit = 180, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.inventory.list.rate_limited",
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
                            stage = "ticketing.inventory.list.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@get
                    }

                    try {
                        val inventory = ticketingService.listInventory(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                        )
                        logger.info(
                            "ticketing.inventory.list.success requestId={} userId={} eventId={} count={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            inventory.size,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.inventory.list.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf(
                                "count" to inventory.size.toString(),
                                "heldCount" to inventory.count { it.status == "held" }.toString(),
                            ),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            InventoryListResponse(
                                inventory = inventory.map { unit ->
                                    InventoryUnitResponse.fromStored(
                                        storedUnit = unit,
                                        currentUserId = principal.user.id,
                                    )
                                },
                            ),
                        )
                    } catch (error: Throwable) {
                        call.respondTicketingScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/events/{eventId}/holds") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "ticketing_hold_create:${principal.user.id}", limit = 60, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.hold.create.rate_limited",
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
                            stage = "ticketing.hold.create.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }

                    val request = call.receiveJsonBodyLimited<CreateSeatHoldRequest>(
                        json = ticketingJson,
                        maxBytes = MAX_CREATE_HOLD_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondTicketingRequestError(
                            error = error,
                            payloadTooLargeStage = "ticketing.hold.create.payload_too_large",
                            invalidPayloadStage = "ticketing.hold.create.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid seat hold request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }

                    val inventoryRef = request.toInventoryRef()
                    if (inventoryRef.isBlank()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.hold.create.invalid_inventory_ref",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid inventory ref"))
                        return@post
                    }

                    try {
                        val hold = ticketingService.createSeatHold(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            inventoryRef = inventoryRef,
                        )
                        logger.info(
                            "ticketing.hold.create.success requestId={} userId={} eventId={} holdId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            hold.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.hold.create.success",
                            status = HttpStatusCode.Created.value,
                            metadata = mapOf("holdStatus" to hold.status),
                        )
                        call.respond(HttpStatusCode.Created, SeatHoldResponse.fromStored(hold))
                    } catch (error: Throwable) {
                        call.respondTicketingScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                delete("/holds/{holdId}") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "ticketing_hold_release:${principal.user.id}", limit = 60, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.hold.release.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@delete
                    }

                    val holdId = call.parameters["holdId"]
                    if (!holdId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.hold.release.invalid_hold_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid hold id"))
                        return@delete
                    }

                    try {
                        val hold = ticketingService.releaseSeatHold(
                            actorUserId = principal.user.id,
                            holdId = holdId.orEmpty(),
                        )
                        logger.info(
                            "ticketing.hold.release.success requestId={} userId={} holdId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            hold.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.hold.release.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("holdStatus" to hold.status),
                        )
                        call.respond(HttpStatusCode.OK, SeatHoldResponse.fromStored(hold))
                    } catch (error: Throwable) {
                        call.respondTicketingScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }
            }
        }
    }

    /** Унифицирует обработку payload/body ошибок ticketing surface. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondTicketingRequestError(
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

    /** Унифицирует not-found/forbidden/conflict ошибки ticketing bounded context-а. */
    private suspend fun io.ktor.server.application.ApplicationCall.respondTicketingScopeError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        when (error) {
            is TicketingEventNotFoundException,
            is TicketingEventUnavailableException,
            is TicketingInventoryUnitNotFoundException,
            is TicketingSeatHoldNotFoundException,
            -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "ticketing.not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Ticketing resource was not found"))
            }

            is TicketingSeatHoldForbiddenException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "ticketing.forbidden",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = "forbidden",
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Ticketing action is forbidden"))
            }

            is TicketingValidationException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "ticketing.bad_request",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "bad_request",
                )
                respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", error.message ?: "Invalid ticketing request"))
            }

            is TicketingConflictException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "ticketing.conflict",
                    status = HttpStatusCode.Conflict.value,
                    safeErrorCode = "conflict",
                )
                respond(HttpStatusCode.Conflict, ErrorResponse("conflict", error.message ?: "Ticketing conflict"))
            }

            else -> throw error
        }
    }

    /** Проверяет UUID-подобность route parameter-а. */
    private fun String?.isUuid(): Boolean {
        return runCatching {
            UUID.fromString(this.orEmpty())
        }.isSuccess
    }
}

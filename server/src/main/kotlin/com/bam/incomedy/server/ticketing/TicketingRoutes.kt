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
import com.bam.incomedy.server.payments.yookassa.YooKassaWebhookSecurity
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.directPeerFingerprint
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
 * Поверхность уже покрывает public inventory read, order/status polling, защищенные hold
 * create/release, provider-agnostic checkout order foundation, PSP handoff и базовую webhook-
 * синхронизацию оплаты, но пока не включает ticket issuance, QR issuance или check-in.
 */
object TicketingRoutes {
    /** Структурированный logger ticketing foundation surface. */
    private val logger = LoggerFactory.getLogger(TicketingRoutes::class.java)

    /** Максимальный размер request тела создания hold-а. */
    private const val MAX_CREATE_HOLD_REQUEST_BYTES = 4 * 1024

    /** Максимальный размер request тела создания checkout order-а. */
    private const val MAX_CREATE_ORDER_REQUEST_BYTES = 8 * 1024

    /** Максимальный размер входящего webhook body от PSP. */
    private const val MAX_PAYMENT_WEBHOOK_REQUEST_BYTES = 64 * 1024

    /** Peer-based лимит публичного чтения inventory, чтобы анонимный catalog surface не стал log sink-ом. */
    private const val PUBLIC_INVENTORY_PEER_LIMIT = 240

    /** Peer-based лимит webhook-ов PSP на один source IP. */
    private const val PAYMENT_WEBHOOK_PEER_LIMIT = 300

    /** Поддерживаемые event-коды YooKassa webhook surface-а. */
    private val SUPPORTED_YOOKASSA_WEBHOOK_EVENTS = setOf(
        "payment.succeeded",
        "payment.canceled",
        "payment.waiting_for_capture",
    )

    /** Регистрирует public и protected ticketing foundation routes. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        eventRepository: EventRepository,
        ticketingRepository: TicketingRepository,
        checkoutGateway: TicketCheckoutGateway? = null,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
        nowProvider: () -> OffsetDateTime = { OffsetDateTime.now() },
        holdTtl: Duration = Duration.ofMinutes(10),
        checkoutTtl: Duration = Duration.ofMinutes(10),
    ) {
        val ticketingService = EventTicketingService(
            workspaceRepository = workspaceRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            checkoutGateway = checkoutGateway,
            nowProvider = nowProvider,
            holdTtl = holdTtl,
            checkoutTtl = checkoutTtl,
        )

        route.route("/api/v1") {
            /**
             * Публичная audience-поверхность чтения derived inventory.
             *
             * Route не выдает персонализированные hold-данные и оставляет create/release hold в
             * защищенной session-only части ticketing surface.
             */
            get("/public/events/{eventId}/inventory") {
                val directPeer = call.directPeerFingerprint()
                if (!rateLimiter.allow(
                        key = "ticketing_public_inventory_peer:$directPeer",
                        limit = PUBLIC_INVENTORY_PEER_LIMIT,
                        windowMs = 60_000L,
                    )
                ) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.public_inventory.list.rate_limited",
                        status = HttpStatusCode.TooManyRequests.value,
                        safeErrorCode = "rate_limited",
                        metadata = mapOf("scope" to "peer"),
                    )
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                    return@get
                }

                val eventId = call.parameters["eventId"]
                if (!eventId.isUuid()) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.public_inventory.list.invalid_event_id",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                    return@get
                }

                try {
                    val inventory = ticketingService.listPublicInventory(
                        eventId = eventId.orEmpty(),
                    )
                    logger.info(
                        "ticketing.public_inventory.list.success requestId={} eventId={} count={}",
                        call.callId ?: "n/a",
                        eventId,
                        inventory.size,
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.public_inventory.list.success",
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
                                    currentUserId = null,
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

            post("/webhooks/payments") {
                val sourceIp = YooKassaWebhookSecurity.extractSourceIp(call)
                if (!rateLimiter.allow(
                        key = "ticketing_payment_webhook_peer:$sourceIp",
                        limit = PAYMENT_WEBHOOK_PEER_LIMIT,
                        windowMs = 60_000L,
                    )
                ) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.checkout.webhook.rate_limited",
                        status = HttpStatusCode.TooManyRequests.value,
                        safeErrorCode = "rate_limited",
                        metadata = mapOf("scope" to "peer"),
                    )
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                    return@post
                }

                if (!YooKassaWebhookSecurity.isAllowedSourceIp(sourceIp)) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.checkout.webhook.forbidden_source_ip",
                        status = HttpStatusCode.Forbidden.value,
                        safeErrorCode = "forbidden",
                        metadata = mapOf("provider" to "yookassa"),
                    )
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Webhook source is forbidden"))
                    return@post
                }

                val request = call.receiveJsonBodyLimited<YooKassaWebhookRequest>(
                    json = yookassaWebhookJson,
                    maxBytes = MAX_PAYMENT_WEBHOOK_REQUEST_BYTES,
                ).getOrElse { error ->
                    call.respondTicketingRequestError(
                        error = error,
                        payloadTooLargeStage = "ticketing.checkout.webhook.payload_too_large",
                        invalidPayloadStage = "ticketing.checkout.webhook.invalid_payload",
                        payloadTooLargeMessage = "Webhook body is too large",
                        invalidPayloadMessage = "Invalid payment webhook payload",
                        diagnosticsStore = diagnosticsStore,
                    )
                    return@post
                }

                if (request.type != "notification") {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.checkout.webhook.invalid_type",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                        metadata = mapOf("provider" to "yookassa"),
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid webhook type"))
                    return@post
                }
                if (request.event !in SUPPORTED_YOOKASSA_WEBHOOK_EVENTS) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.checkout.webhook.invalid_event",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                        metadata = mapOf("provider" to "yookassa"),
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unsupported webhook event"))
                    return@post
                }
                val providerPaymentId = request.providerPaymentId()
                if (providerPaymentId.isBlank()) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "ticketing.checkout.webhook.invalid_payment_id",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                        metadata = mapOf("provider" to "yookassa"),
                    )
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid provider payment id"))
                    return@post
                }

                try {
                    when (val outcome = ticketingService.handleTicketCheckoutWebhook(providerPaymentId)) {
                        is TicketCheckoutWebhookOutcome.Applied -> {
                            logger.info(
                                "ticketing.checkout.webhook.applied requestId={} sourceIp={} event={} orderId={} sessionId={} result={}",
                                call.callId ?: "n/a",
                                sourceIp,
                                request.event,
                                outcome.order.id,
                                outcome.session.id,
                                outcome.resultCode,
                            )
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "ticketing.checkout.webhook.applied",
                                status = HttpStatusCode.OK.value,
                                metadata = mapOf(
                                    "provider" to outcome.session.provider,
                                    "event" to request.event,
                                    "result" to outcome.resultCode,
                                ),
                            )
                        }

                        is TicketCheckoutWebhookOutcome.Ignored -> {
                            logger.info(
                                "ticketing.checkout.webhook.ignored requestId={} sourceIp={} event={} reason={}",
                                call.callId ?: "n/a",
                                sourceIp,
                                request.event,
                                outcome.reasonCode,
                            )
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "ticketing.checkout.webhook.ignored",
                                status = HttpStatusCode.OK.value,
                                metadata = mapOf(
                                    "provider" to "yookassa",
                                    "event" to request.event,
                                    "reason" to outcome.reasonCode,
                                ),
                            )
                        }

                        is TicketCheckoutWebhookOutcome.RecoveryRequired -> {
                            logger.warn(
                                "ticketing.checkout.webhook.recovery_required requestId={} sourceIp={} event={} orderId={} reason={}",
                                call.callId ?: "n/a",
                                sourceIp,
                                request.event,
                                outcome.orderId ?: "n/a",
                                outcome.reasonCode,
                            )
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "ticketing.checkout.webhook.recovery_required",
                                status = HttpStatusCode.OK.value,
                                metadata = mapOf(
                                    "provider" to "yookassa",
                                    "event" to request.event,
                                    "reason" to outcome.reasonCode,
                                ),
                            )
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                } catch (error: Throwable) {
                    call.respondTicketingScopeError(
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

                get("/orders/{orderId}") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "ticketing_order_get:${principal.user.id}", limit = 180, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.order.get.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }

                    val orderId = call.parameters["orderId"]
                    if (!orderId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.order.get.invalid_order_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid order id"))
                        return@get
                    }

                    try {
                        val order = ticketingService.getTicketOrder(
                            actorUserId = principal.user.id,
                            orderId = orderId.orEmpty(),
                        )
                        logger.info(
                            "ticketing.order.get.success requestId={} userId={} orderId={} status={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            order.id,
                            order.status,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.order.get.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("status" to order.status),
                        )
                        call.respond(HttpStatusCode.OK, TicketOrderResponse.fromStored(order))
                    } catch (error: Throwable) {
                        call.respondTicketingScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/events/{eventId}/orders") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "ticketing_order_create:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.order.create.rate_limited",
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
                            stage = "ticketing.order.create.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }

                    val request = call.receiveJsonBodyLimited<CreateTicketOrderRequest>(
                        json = ticketingJson,
                        maxBytes = MAX_CREATE_ORDER_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondTicketingRequestError(
                            error = error,
                            payloadTooLargeStage = "ticketing.order.create.payload_too_large",
                            invalidPayloadStage = "ticketing.order.create.invalid_payload",
                            payloadTooLargeMessage = "Request body is too large",
                            invalidPayloadMessage = "Invalid checkout order request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }

                    val holdIds = request.toHoldIds()
                    if (holdIds.isEmpty()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.order.create.empty_holds",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "At least one hold id is required"))
                        return@post
                    }
                    if (holdIds.any { holdId -> !holdId.isUuid() }) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.order.create.invalid_hold_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid hold id"))
                        return@post
                    }

                    try {
                        val order = ticketingService.createTicketOrder(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            holdIds = holdIds,
                        )
                        logger.info(
                            "ticketing.order.create.success requestId={} userId={} eventId={} orderId={} lineCount={} totalMinor={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            order.id,
                            order.lines.size,
                            order.totalMinor,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.order.create.success",
                            status = HttpStatusCode.Created.value,
                            metadata = mapOf(
                                "lineCount" to order.lines.size.toString(),
                                "totalMinor" to order.totalMinor.toString(),
                                "status" to order.status,
                            ),
                        )
                        call.respond(HttpStatusCode.Created, TicketOrderResponse.fromStored(order))
                    } catch (error: Throwable) {
                        call.respondTicketingScopeError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/events/{eventId}/orders/{orderId}/checkout") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "ticketing_checkout_start:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.checkout.start.rate_limited",
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
                            stage = "ticketing.checkout.start.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }
                    val orderId = call.parameters["orderId"]
                    if (!orderId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.checkout.start.invalid_order_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid order id"))
                        return@post
                    }

                    try {
                        val session = ticketingService.startTicketCheckout(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            orderId = orderId.orEmpty(),
                            requestId = call.callId ?: "n/a",
                        )
                        logger.info(
                            "ticketing.checkout.start.success requestId={} userId={} eventId={} orderId={} provider={} sessionId={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            orderId,
                            session.provider,
                            session.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "ticketing.checkout.start.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf(
                                "provider" to session.provider,
                                "status" to session.status,
                            ),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            TicketCheckoutSessionResponse.fromStored(
                                storedSession = session,
                                eventId = eventId.orEmpty(),
                            ),
                        )
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
        error.toTicketingNotFoundDiagnostics()?.let { diagnostics ->
            diagnosticsStore?.recordCall(
                call = this,
                stage = diagnostics.stage,
                status = HttpStatusCode.NotFound.value,
                safeErrorCode = "not_found",
                metadata = diagnostics.metadata,
            )
            respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Ticketing resource was not found"))
            return
        }
        when (error) {
            is TicketingSeatHoldForbiddenException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "ticketing.forbidden",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = "forbidden",
                    metadata = mapOf(
                        "resource" to "hold",
                        "reason" to error.reasonCode,
                    ),
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Ticketing action is forbidden"))
            }

            is TicketingCheckoutUnavailableException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "ticketing.checkout.unavailable",
                    status = HttpStatusCode.ServiceUnavailable.value,
                    safeErrorCode = error.reasonCode,
                )
                respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(error.reasonCode, "Ticket checkout is unavailable"))
            }

            is TicketCheckoutGatewayException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "ticketing.checkout.provider_failed",
                    status = HttpStatusCode.BadGateway.value,
                    safeErrorCode = error.safeCode,
                )
                respond(HttpStatusCode.BadGateway, ErrorResponse(error.safeCode, "Checkout provider request failed"))
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

    /** Возвращает resource-aware diagnostics descriptor для not-found/unavailable ticketing ошибок. */
    private fun Throwable.toTicketingNotFoundDiagnostics(): TicketingNotFoundDiagnostics? {
        return when (this) {
            is TicketingEventNotFoundException -> TicketingNotFoundDiagnostics(
                stage = "ticketing.event.not_found",
                metadata = mapOf(
                    "resource" to "event",
                    "reason" to "missing",
                ),
            )

            is TicketingEventUnavailableException -> TicketingNotFoundDiagnostics(
                stage = "ticketing.event.unavailable",
                metadata = mapOf(
                    "resource" to "event",
                    "reason" to reasonCode,
                ),
            )

            is TicketingInventoryUnitNotFoundException -> TicketingNotFoundDiagnostics(
                stage = "ticketing.inventory.not_found",
                metadata = mapOf(
                    "resource" to "inventory",
                    "reason" to "missing",
                ),
            )

            is TicketingSeatHoldNotFoundException -> TicketingNotFoundDiagnostics(
                stage = "ticketing.hold.not_found",
                metadata = mapOf(
                    "resource" to "hold",
                    "reason" to "missing",
                ),
            )

            is TicketingTicketOrderNotFoundException -> TicketingNotFoundDiagnostics(
                stage = "ticketing.order.not_found",
                metadata = mapOf(
                    "resource" to "order",
                    "reason" to "missing",
                ),
            )

            else -> null
        }
    }

    /** Проверяет UUID-подобность route parameter-а. */
    private fun String?.isUuid(): Boolean {
        return runCatching {
            UUID.fromString(this.orEmpty())
        }.isSuccess
    }
}

/**
 * Описывает безопасную diagnostics-классификацию ticketing ошибки без раскрытия чувствительных деталей.
 *
 * @property stage Стадия обработки для фильтрации diagnostics events.
 * @property metadata Низкокардинальные атрибуты типа ресурса и причины.
 */
private data class TicketingNotFoundDiagnostics(
    val stage: String,
    val metadata: Map<String, String>,
)

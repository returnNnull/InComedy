package com.bam.incomedy.server.donations

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.DonationRepository
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.LineupRepository
import com.bam.incomedy.server.db.SessionUserRepository
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
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

/**
 * HTTP surface первого backend slice-а donations/payouts.
 */
object DonationRoutes {
    private val logger = LoggerFactory.getLogger(DonationRoutes::class.java)

    private const val MAX_UPSERT_PAYOUT_PROFILE_REQUEST_BYTES = 8 * 1024
    private const val MAX_CREATE_DONATION_REQUEST_BYTES = 8 * 1024

    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        eventRepository: EventRepository,
        lineupRepository: LineupRepository,
        donationRepository: DonationRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        val service = DonationService(
            sessionUserRepository = sessionUserRepository,
            eventRepository = eventRepository,
            lineupRepository = lineupRepository,
            donationRepository = donationRepository,
        )

        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                get("/comedian/me/payout-profile") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "comedian_payout_profile_get:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.payout_profile.get.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }

                    try {
                        val profile = service.getMyPayoutProfile(principal.user.id)
                        logger.info(
                            "comedian.payout_profile.get.success requestId={} userId={} exists={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            profile != null,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.payout_profile.get.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("exists" to (profile != null).toString()),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            PayoutProfileEnvelope(profile = profile?.let(PayoutProfileResponse::fromStored)),
                        )
                    } catch (error: Throwable) {
                        call.respondDonationError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                put("/comedian/me/payout-profile") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "comedian_payout_profile_upsert:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.payout_profile.upsert.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@put
                    }

                    val request = call.receiveJsonBodyLimited<UpsertPayoutProfileRequest>(
                        json = donationsJson,
                        maxBytes = MAX_UPSERT_PAYOUT_PROFILE_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondDonationRequestDecodeError(
                            error = error,
                            payloadTooLargeStage = "comedian.payout_profile.upsert.payload_too_large",
                            invalidPayloadStage = "comedian.payout_profile.upsert.invalid_payload",
                            invalidPayloadMessage = "Invalid payout profile request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@put
                    }
                    val legalType = request.toLegalTypeOrNull()
                    if (legalType == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.payout_profile.upsert.invalid_legal_type",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unknown payout legal type"))
                        return@put
                    }
                    if (request.beneficiaryRef.trim().length > 255) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.payout_profile.upsert.invalid_beneficiary_ref_length",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Beneficiary reference is too long"))
                        return@put
                    }

                    try {
                        val profile = service.upsertMyPayoutProfile(
                            actorUserId = principal.user.id,
                            legalType = legalType,
                            beneficiaryRef = request.beneficiaryRef,
                        )
                        logger.info(
                            "comedian.payout_profile.upsert.success requestId={} userId={} status={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            profile.verificationStatus.wireName,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.payout_profile.upsert.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("verificationStatus" to profile.verificationStatus.wireName),
                        )
                        call.respond(HttpStatusCode.OK, PayoutProfileResponse.fromStored(profile))
                    } catch (error: Throwable) {
                        call.respondDonationError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                get("/me/donations") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "donation_intent_list_donor:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "audience.donation.list.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }

                    try {
                        val donations = service.listMyDonations(principal.user.id)
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "audience.donation.list.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("count" to donations.size.toString()),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            DonationIntentListResponse(
                                donations = donations.map(DonationIntentResponse::fromStored),
                            ),
                        )
                    } catch (error: Throwable) {
                        call.respondDonationError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                get("/comedian/me/donations") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "comedian_donation_list:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.donation.list.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }

                    try {
                        val donations = service.listMyReceivedDonations(principal.user.id)
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "comedian.donation.list.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("count" to donations.size.toString()),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            DonationIntentListResponse(
                                donations = donations.map(DonationIntentResponse::fromStored),
                            ),
                        )
                    } catch (error: Throwable) {
                        call.respondDonationError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }

                post("/events/{eventId}/donations") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "donation_intent_create:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "audience.donation.create.rate_limited",
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
                            stage = "audience.donation.create.invalid_event_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid event id"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<CreateDonationIntentRequest>(
                        json = donationsJson,
                        maxBytes = MAX_CREATE_DONATION_REQUEST_BYTES,
                    ).getOrElse { error ->
                        call.respondDonationRequestDecodeError(
                            error = error,
                            payloadTooLargeStage = "audience.donation.create.payload_too_large",
                            invalidPayloadStage = "audience.donation.create.invalid_payload",
                            invalidPayloadMessage = "Invalid donation intent request",
                            diagnosticsStore = diagnosticsStore,
                        )
                        return@post
                    }
                    if (!request.comedianUserId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "audience.donation.create.invalid_comedian_user_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid comedian user id"))
                        return@post
                    }

                    try {
                        val result = service.createDonationIntent(
                            actorUserId = principal.user.id,
                            eventId = eventId.orEmpty(),
                            comedianUserId = request.comedianUserId,
                            amountMinor = request.amountMinor,
                            currency = request.currency,
                            message = request.message,
                            idempotencyKey = request.idempotencyKey,
                        )
                        val status = if (result.reusedExisting) HttpStatusCode.OK else HttpStatusCode.Created
                        logger.info(
                            "audience.donation.create.success requestId={} donorUserId={} eventId={} donationId={} reusedExisting={}",
                            call.callId ?: "n/a",
                            principal.user.id,
                            eventId,
                            result.donationIntent.id,
                            result.reusedExisting,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "audience.donation.create.success",
                            status = status.value,
                            metadata = mapOf(
                                "donationStatus" to result.donationIntent.status.wireName,
                                "reusedExisting" to result.reusedExisting.toString(),
                            ),
                        )
                        call.respond(status, DonationIntentResponse.fromStored(result.donationIntent))
                    } catch (error: Throwable) {
                        call.respondDonationError(
                            error = error,
                            diagnosticsStore = diagnosticsStore,
                        )
                    }
                }
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondDonationRequestDecodeError(
        error: Throwable,
        payloadTooLargeStage: String,
        invalidPayloadStage: String,
        invalidPayloadMessage: String,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        val (stage, message) = if (error is PayloadTooLargeException) {
            payloadTooLargeStage to "Request body is too large"
        } else {
            invalidPayloadStage to invalidPayloadMessage
        }
        diagnosticsStore?.recordCall(
            call = this,
            stage = stage,
            status = HttpStatusCode.BadRequest.value,
            safeErrorCode = "bad_request",
        )
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", message))
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondDonationError(
        error: Throwable,
        diagnosticsStore: DiagnosticsStore?,
    ) {
        when (error) {
            is DonationEventNotFoundException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "donation.scope.event_not_found",
                    status = HttpStatusCode.NotFound.value,
                    safeErrorCode = "not_found",
                )
                respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Event was not found"))
            }
            is DonationPermissionDeniedException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "donation.permission_denied",
                    status = HttpStatusCode.Forbidden.value,
                    safeErrorCode = "forbidden",
                    metadata = mapOf("reasonCode" to error.reasonCode),
                )
                respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", error.reasonCode))
            }
            is DonationConflictException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "donation.conflict",
                    status = HttpStatusCode.Conflict.value,
                    safeErrorCode = "conflict",
                    metadata = mapOf("reasonCode" to error.reasonCode),
                )
                respond(HttpStatusCode.Conflict, ErrorResponse("conflict", error.reasonCode))
            }
            is DonationValidationException -> {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "donation.validation_error",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "bad_request",
                    metadata = mapOf("reasonCode" to error.reasonCode),
                )
                respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", error.reasonCode))
            }
            else -> throw error
        }
    }

    private fun String?.isUuid(): Boolean {
        return runCatching { java.util.UUID.fromString(this) }.isSuccess
    }
}

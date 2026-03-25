package com.bam.incomedy.server.db

import com.bam.incomedy.domain.donations.DonationIntentStatus
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutVerificationStatus
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация persistence для первого donations/payouts backend slice-а.
 */
class PostgresDonationRepository(
    private val dataSource: DataSource,
) : DonationRepository {
    override fun findPayoutProfile(userId: String): StoredPayoutProfile? {
        dataSource.connection.use { connection ->
            return loadPayoutProfile(
                connection = connection,
                userId = userId,
            )
        }
    }

    override fun upsertPayoutProfile(
        userId: String,
        payoutProvider: String,
        legalType: PayoutLegalType,
        beneficiaryRef: String,
        verificationStatus: PayoutVerificationStatus,
    ): StoredPayoutProfile {
        val profileId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO comedian_payout_profiles (
                    id,
                    user_id,
                    payout_provider,
                    legal_type,
                    beneficiary_ref,
                    verification_status,
                    created_at,
                    updated_at,
                    status_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW())
                ON CONFLICT (user_id) DO UPDATE
                SET payout_provider = EXCLUDED.payout_provider,
                    legal_type = EXCLUDED.legal_type,
                    beneficiary_ref = EXCLUDED.beneficiary_ref,
                    verification_status = EXCLUDED.verification_status,
                    updated_at = NOW(),
                    status_updated_at = CASE
                        WHEN comedian_payout_profiles.verification_status = EXCLUDED.verification_status
                            THEN comedian_payout_profiles.status_updated_at
                        ELSE NOW()
                    END
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, profileId)
                statement.setObject(2, UUID.fromString(userId))
                statement.setString(3, payoutProvider)
                statement.setString(4, legalType.wireName)
                statement.setString(5, beneficiaryRef)
                statement.setString(6, verificationStatus.wireName)
                statement.executeUpdate()
            }
            return requireNotNull(loadPayoutProfile(connection, userId))
        }
    }

    override fun findDonationIntentByIdempotency(
        donorUserId: String,
        idempotencyKey: String,
    ): StoredDonationIntent? {
        dataSource.connection.use { connection ->
            return connection.prepareStatement(
                donationIntentSelect +
                    """
                    WHERE di.donor_user_id = ?
                      AND di.idempotency_key = ?
                    """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(donorUserId))
                statement.setString(2, idempotencyKey)
                statement.executeQuery().use { result ->
                    if (result.next()) result.toStoredDonationIntent() else null
                }
            }
        }
    }

    override fun createDonationIntent(
        eventId: String,
        comedianUserId: String,
        donorUserId: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        status: DonationIntentStatus,
        idempotencyKey: String,
    ): StoredDonationIntent {
        val donationId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO donation_intents (
                    id,
                    event_id,
                    comedian_user_id,
                    donor_user_id,
                    amount_minor,
                    currency,
                    message,
                    status,
                    payment_id,
                    idempotency_key,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NOW(), NOW())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(donationId))
                statement.setObject(2, UUID.fromString(eventId))
                statement.setObject(3, UUID.fromString(comedianUserId))
                statement.setObject(4, UUID.fromString(donorUserId))
                statement.setInt(5, amountMinor)
                statement.setString(6, currency)
                statement.setString(7, message)
                statement.setString(8, status.wireName)
                statement.setString(9, idempotencyKey)
                statement.executeUpdate()
            }
            return requireNotNull(loadDonationIntent(connection, donationId))
        }
    }

    override fun listDonationIntentsForDonor(
        donorUserId: String,
    ): List<StoredDonationIntent> {
        dataSource.connection.use { connection ->
            return connection.prepareStatement(
                donationIntentSelect +
                    """
                    WHERE di.donor_user_id = ?
                    ORDER BY di.created_at DESC, di.id DESC
                    """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(donorUserId))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.toStoredDonationIntent())
                        }
                    }
                }
            }
        }
    }

    override fun listDonationIntentsForComedian(
        comedianUserId: String,
    ): List<StoredDonationIntent> {
        dataSource.connection.use { connection ->
            return connection.prepareStatement(
                donationIntentSelect +
                    """
                    WHERE di.comedian_user_id = ?
                    ORDER BY di.created_at DESC, di.id DESC
                    """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(comedianUserId))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.toStoredDonationIntent())
                        }
                    }
                }
            }
        }
    }

    private fun loadPayoutProfile(
        connection: Connection,
        userId: String,
    ): StoredPayoutProfile? {
        return connection.prepareStatement(
            """
            SELECT
                id,
                user_id,
                payout_provider,
                legal_type,
                beneficiary_ref,
                verification_status,
                created_at,
                updated_at,
                status_updated_at
            FROM comedian_payout_profiles
            WHERE user_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                if (result.next()) result.toStoredPayoutProfile() else null
            }
        }
    }

    private fun loadDonationIntent(
        connection: Connection,
        donationId: String,
    ): StoredDonationIntent? {
        return connection.prepareStatement(
            donationIntentSelect +
                """
                WHERE di.id = ?
                """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(donationId))
            statement.executeQuery().use { result ->
                if (result.next()) result.toStoredDonationIntent() else null
            }
        }
    }

    private companion object {
        private const val donationIntentSelect =
            """
            SELECT
                di.id,
                di.event_id,
                oe.title AS event_title,
                di.comedian_user_id,
                comedian.display_name AS comedian_display_name,
                di.donor_user_id,
                donor.display_name AS donor_display_name,
                di.amount_minor,
                di.currency,
                di.message,
                di.status,
                di.payment_id,
                di.idempotency_key,
                di.created_at,
                di.updated_at
            FROM donation_intents di
            JOIN organizer_events oe
              ON oe.id = di.event_id
            JOIN users comedian
              ON comedian.id = di.comedian_user_id
            JOIN users donor
              ON donor.id = di.donor_user_id
            """
    }
}

private fun ResultSet.toStoredPayoutProfile(): StoredPayoutProfile {
    return StoredPayoutProfile(
        id = getObject("id", UUID::class.java).toString(),
        userId = getObject("user_id", UUID::class.java).toString(),
        payoutProvider = getString("payout_provider"),
        legalType = PayoutLegalType.fromWireName(getString("legal_type"))
            ?: error("Unknown payout legal type"),
        beneficiaryRef = getString("beneficiary_ref"),
        verificationStatus = PayoutVerificationStatus.fromWireName(getString("verification_status"))
            ?: error("Unknown payout verification status"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        statusUpdatedAt = getObject("status_updated_at", OffsetDateTime::class.java),
    )
}

private fun ResultSet.toStoredDonationIntent(): StoredDonationIntent {
    return StoredDonationIntent(
        id = getObject("id", UUID::class.java).toString(),
        eventId = getObject("event_id", UUID::class.java).toString(),
        eventTitle = getString("event_title"),
        comedianUserId = getObject("comedian_user_id", UUID::class.java).toString(),
        comedianDisplayName = getString("comedian_display_name"),
        donorUserId = getObject("donor_user_id", UUID::class.java).toString(),
        donorDisplayName = getString("donor_display_name"),
        amountMinor = getInt("amount_minor"),
        currency = getString("currency"),
        message = getString("message"),
        status = DonationIntentStatus.fromWireName(getString("status"))
            ?: error("Unknown donation intent status"),
        paymentId = getString("payment_id"),
        idempotencyKey = getString("idempotency_key"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    )
}

package com.bam.incomedy.server.observability

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты bounded in-memory diagnostics store.
 */
class InMemoryDiagnosticsStoreTest {

    /** Проверяет retention, newest-first выборку и базовую фильтрацию. */
    @Test
    fun `query returns newest matching events only`() {
        var currentTime = Instant.parse("2026-03-13T00:00:00Z")
        val store = InMemoryDiagnosticsStore(retentionLimit = 2) { currentTime }

        store.record(
            DiagnosticsEventInput(
                requestId = "req-1",
                method = "POST",
                route = "/api/v1/auth/telegram/verify",
                stage = "auth.telegram.verify.failed",
                status = 401,
                safeErrorCode = "telegram_auth_hash_invalid",
            ),
        )
        currentTime = currentTime.plusSeconds(1)
        store.record(
            DiagnosticsEventInput(
                requestId = "req-2",
                method = "POST",
                route = "/api/v1/auth/refresh",
                stage = "auth.refresh.invalid_token",
                status = 401,
                safeErrorCode = "invalid_refresh_token",
            ),
        )
        currentTime = currentTime.plusSeconds(1)
        store.record(
            DiagnosticsEventInput(
                requestId = "req-3",
                method = "GET",
                route = "/api/v1/workspaces",
                stage = "organizer.workspaces.list.success",
                status = 200,
            ),
        )

        val allEvents = store.query(DiagnosticsQuery(limit = 10))
        assertEquals(2, allEvents.size)
        assertEquals("req-3", allEvents[0].requestId)
        assertEquals("req-2", allEvents[1].requestId)

        val filteredEvents = store.query(
            DiagnosticsQuery(
                requestId = "req-2",
                stage = "auth.refresh.invalid_token",
                status = 401,
                limit = 10,
            ),
        )
        assertEquals(1, filteredEvents.size)
        assertEquals("invalid_refresh_token", filteredEvents.single().safeErrorCode)
    }

    /** Проверяет sanitization low-cardinality metadata и безопасного кода ошибки. */
    @Test
    fun `record sanitizes oversized and multiline fields`() {
        val store = InMemoryDiagnosticsStore(retentionLimit = 10) { Instant.parse("2026-03-13T00:00:00Z") }

        store.record(
            DiagnosticsEventInput(
                requestId = "",
                method = "POST\n",
                route = "/api/v1/auth/telegram/verify\r\nleak",
                stage = "auth.telegram.verify.failed\n",
                status = 401,
                safeErrorCode = "telegram_auth_hash_invalid\nwith-extra-data",
                metadata = mapOf(
                    "reason\n" to "bad\nhash",
                    "" to "ignored",
                ),
            ),
        )

        val event = store.query(DiagnosticsQuery(limit = 1)).single()
        assertEquals("n/a", event.requestId)
        assertEquals("POST", event.method)
        assertTrue(event.route.startsWith("/api/v1/auth/telegram/verify"))
        assertEquals("auth.telegram.verify.failed", event.stage)
        assertEquals("telegram_auth_hash_invalid with-extra-data", event.safeErrorCode)
        assertEquals("bad hash", event.metadata["reason"])
        assertNull(event.metadata[""])
    }
}

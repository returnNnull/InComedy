package com.bam.incomedy.server.observability

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestIdTest {

    @Test
    fun `accepts UUID request ids only`() {
        assertTrue(isValidRequestId("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(isValidRequestId("trace-123"))
        assertFalse(isValidRequestId("bad\r\nvalue"))
    }
}

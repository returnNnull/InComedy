package com.bam.incomedy.server.security

import io.ktor.server.application.ApplicationCall
import java.util.concurrent.ConcurrentHashMap

class AuthRateLimiter(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private data class Bucket(
        var windowStartMs: Long,
        var count: Int,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun allow(key: String, limit: Int, windowMs: Long): Boolean {
        val now = nowMillis()
        val bucket = buckets.computeIfAbsent(key) { Bucket(windowStartMs = now, count = 0) }
        synchronized(bucket) {
            if (now - bucket.windowStartMs >= windowMs) {
                bucket.windowStartMs = now
                bucket.count = 0
            }
            if (bucket.count >= limit) return false
            bucket.count += 1
            return true
        }
    }
}

fun ApplicationCall.clientFingerprint(): String {
    val forwarded = request.headers["X-Forwarded-For"]
        ?.substringBefore(',')
        ?.trim()
    if (!forwarded.isNullOrBlank()) return forwarded
    return request.local.remoteHost.ifBlank { "unknown" }
}

package com.bam.incomedy.server.security

import io.ktor.server.application.ApplicationCall
import redis.clients.jedis.JedisPooled
import java.util.concurrent.ConcurrentHashMap

interface AuthRateLimiter {
    fun allow(key: String, limit: Int, windowMs: Long): Boolean
}

class InMemoryAuthRateLimiter(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : AuthRateLimiter {
    private data class Bucket(
        var windowStartMs: Long,
        var count: Int,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun allow(key: String, limit: Int, windowMs: Long): Boolean {
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

class RedisAuthRateLimiter(
    private val jedis: JedisPooled,
    private val keyPrefix: String = "auth:rate:",
) : AuthRateLimiter {
    override fun allow(key: String, limit: Int, windowMs: Long): Boolean {
        val script = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
              return 0
            end
            return 1
        """.trimIndent()
        val result = jedis.eval(
            script,
            listOf("$keyPrefix$key"),
            listOf(windowMs.toString(), limit.toString()),
        )
        return result == 1L
    }
}

fun ApplicationCall.directPeerFingerprint(): String {
    return request.local.remoteHost
        .trim()
        .takeIf { it.isNotBlank() }
        ?.take(128)
        ?: "unknown"
}

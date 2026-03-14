package com.bam.incomedy.server.auth.credentials

interface PasswordHasher {
    fun hash(password: CharArray): String

    fun verify(password: CharArray, passwordHash: String): Boolean
}

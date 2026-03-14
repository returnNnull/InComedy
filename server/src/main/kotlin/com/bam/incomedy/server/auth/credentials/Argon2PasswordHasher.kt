package com.bam.incomedy.server.auth.credentials

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Factory.Argon2Types

class Argon2PasswordHasher(
    private val iterations: Int = 3,
    private val memoryKb: Int = 65_536,
    private val parallelism: Int = 1,
) : PasswordHasher {
    private val argon2 = Argon2Factory.create(Argon2Types.ARGON2id)

    override fun hash(password: CharArray): String {
        return argon2.hash(iterations, memoryKb, parallelism, password)
    }

    override fun verify(password: CharArray, passwordHash: String): Boolean {
        return argon2.verify(passwordHash, password)
    }
}

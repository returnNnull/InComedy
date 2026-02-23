package com.bam.incomedy.feature.auth.domain

import kotlin.random.Random

interface AuthStateGenerator {
    fun next(): String
}

class RandomAuthStateGenerator : AuthStateGenerator {
    override fun next(): String = Random.nextLong().toString()
}

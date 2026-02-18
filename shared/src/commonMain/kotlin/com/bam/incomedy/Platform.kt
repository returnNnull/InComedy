package com.bam.incomedy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
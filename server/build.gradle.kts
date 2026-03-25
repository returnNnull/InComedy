plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
    application
}

group = "com.bam.incomedy"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":domain:donations"))
    implementation(project(":domain:event"))
    implementation(project(":domain:ticketing"))
    implementation(project(":domain:venue"))
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.call.logging.jvm)
    implementation(libs.ktor.server.call.id.jvm)
    implementation(libs.ktor.server.content.negotiation.jvm)
    implementation(libs.ktor.server.websockets.jvm)
    implementation(libs.ktor.serialization.kotlinx.json.jvm)
    implementation(libs.ktor.server.status.pages.jvm)
    implementation(libs.ktor.server.cors.jvm)
    implementation(libs.logback.classic)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.java.jwt)
    implementation(libs.jedis)
    implementation(libs.argon2.jvm)

    testImplementation(libs.ktor.server.tests.jvm)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.h2)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.bam.incomedy.server.MainKt")
}

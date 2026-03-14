package com.bam.incomedy.server.ios

import com.bam.incomedy.server.config.IosAssociatedDomainsConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests the Apple App Site Association endpoints served by the backend. */
class AppleAppSiteAssociationRoutesTest {

    /** Both supported AASA endpoints should return the same JSON payload. */
    @Test
    fun `aasa endpoints return configured app ids and vk callback path`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            routing {
                AppleAppSiteAssociationRoutes.register(
                    route = this,
                    config = IosAssociatedDomainsConfig(
                        appIds = listOf(
                            "TEAM123.com.bam.incomedy.InComedy",
                            "TEAM123.com.bam.incomedy.InComedy.beta",
                        ),
                        paths = listOf("/auth/vk/callback", "/auth/vk/callback/*"),
                    ),
                )
            }
        }

        val rootResponse = client.get("/apple-app-site-association")
        val wellKnownResponse = client.get("/.well-known/apple-app-site-association")

        assertEquals(HttpStatusCode.OK, rootResponse.status)
        assertEquals(HttpStatusCode.OK, wellKnownResponse.status)
        assertEquals("application/json", rootResponse.headers["Content-Type"])
        assertEquals(rootResponse.bodyAsText(), wellKnownResponse.bodyAsText())

        val json = Json.parseToJsonElement(rootResponse.bodyAsText()).jsonObject
        val details = json.getValue("applinks").jsonObject.getValue("details").jsonArray
        assertEquals("TEAM123.com.bam.incomedy.InComedy", details[0].jsonObject.getValue("appID").jsonPrimitive.content)
        assertEquals("/auth/vk/callback", details[0].jsonObject.getValue("paths").jsonArray[0].jsonPrimitive.content)
        assertEquals("/auth/vk/callback/*", details[0].jsonObject.getValue("paths").jsonArray[1].jsonPrimitive.content)
    }
}

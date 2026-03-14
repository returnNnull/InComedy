package com.bam.incomedy.server.ios

import com.bam.incomedy.server.config.IosAssociatedDomainsConfig
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Serves Apple App Site Association metadata for iOS associated domains verification. */
object AppleAppSiteAssociationRoutes {
    private val json = Json { prettyPrint = false }

    /** Registers both supported AASA endpoints without redirects. */
    fun register(
        route: Route,
        config: IosAssociatedDomainsConfig,
    ) {
        val body = associationBody(config)
        route.get("/apple-app-site-association") {
            call.respondText(
                text = body,
                contentType = ContentType.Application.Json,
            )
        }
        route.get("/.well-known/apple-app-site-association") {
            call.respondText(
                text = body,
                contentType = ContentType.Application.Json,
            )
        }
    }

    /** Renders the compact JSON document used by Apple associated domains. */
    fun associationBody(config: IosAssociatedDomainsConfig): String {
        return json.encodeToString(
            AppleAppSiteAssociationDocument.serializer(),
            AppleAppSiteAssociationDocument(
                applinks = AppleAppLinksSection(
                    apps = emptyList(),
                    details = config.appIds.map { appId ->
                        AppleAppLinksDetail(
                            appID = appId,
                            paths = config.paths,
                        )
                    },
                ),
            ),
        )
    }
}

@Serializable
private data class AppleAppSiteAssociationDocument(
    val applinks: AppleAppLinksSection,
)

@Serializable
private data class AppleAppLinksSection(
    val apps: List<String>,
    val details: List<AppleAppLinksDetail>,
)

@Serializable
private data class AppleAppLinksDetail(
    val appID: String,
    val paths: List<String>,
)

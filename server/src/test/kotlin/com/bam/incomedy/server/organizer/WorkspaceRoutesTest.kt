package com.bam.incomedy.server.organizer

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceRoutesTest {

    @Test
    fun `workspace can be created and listed by owner`() = testApplication {
        val repository = InMemoryTelegramUserRepository().apply {
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000020",
                    displayName = "Workspace Owner",
                    username = "owner",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.TELEGRAM),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
        }
        val tokenService = tokenService()

        environment {
            config = MapApplicationConfig()
        }
        application {
            this.install(ContentNegotiation) {
                json()
            }
            routing {
                WorkspaceRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    userRepository = repository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val accessToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000020",
            provider = AuthProvider.TELEGRAM,
        ).accessToken

        val createResponse = client.post("/api/v1/workspaces") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Comedy Cellar Moscow"}""")
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdBody = createResponse.bodyAsText()
        assertTrue(createdBody.contains(""""permission_role":"owner""""))

        val listResponse = client.get("/api/v1/workspaces") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listBody = listResponse.bodyAsText()
        assertTrue(listBody.contains("Comedy Cellar Moscow"))
        assertTrue(listBody.contains(""""permission_role":"owner""""))
    }

    @Test
    fun `workspace invitation is created and visible in invitee inbox`() = testApplication {
        val repository = InMemoryTelegramUserRepository().apply {
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000020",
                    displayName = "Workspace Owner",
                    username = "owner",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.TELEGRAM),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000021",
                    displayName = "Checker User",
                    username = "checker_user",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.PASSWORD),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
        }
        val tokenService = tokenService()
        val ownerToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000020",
            provider = AuthProvider.TELEGRAM,
        ).accessToken
        val inviteeToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000021",
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val workspace = repository.createWorkspace(
            ownerUserId = "00000000-0000-0000-0000-000000000020",
            name = "Comedy Cellar Moscow",
            slug = "comedy-cellar-moscow",
        )

        configureWorkspaceRoutes(repository, tokenService)

        val createInvitationResponse = client.post("/api/v1/workspaces/${workspace.id}/invitations") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"invitee_identifier":"checker_user","permission_role":"checker"}""")
        }

        assertEquals(HttpStatusCode.Created, createInvitationResponse.status)
        val invitationBody = createInvitationResponse.bodyAsText()
        assertTrue(invitationBody.contains(""""status":"invited""""))
        assertTrue(invitationBody.contains(""""permission_role":"checker""""))

        val inboxResponse = client.get("/api/v1/workspace-invitations") {
            header(HttpHeaders.Authorization, "Bearer $inviteeToken")
        }

        assertEquals(HttpStatusCode.OK, inboxResponse.status)
        val inboxBody = inboxResponse.bodyAsText()
        assertTrue(inboxBody.contains("Comedy Cellar Moscow"))
        assertTrue(inboxBody.contains(""""permission_role":"checker""""))
    }

    @Test
    fun `invitee can accept workspace invitation and see workspace`() = testApplication {
        val repository = InMemoryTelegramUserRepository().apply {
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000020",
                    displayName = "Workspace Owner",
                    username = "owner",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.TELEGRAM),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000021",
                    displayName = "Checker User",
                    username = "checker_user",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.PASSWORD),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
        }
        val tokenService = tokenService()
        val ownerToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000020",
            provider = AuthProvider.TELEGRAM,
        ).accessToken
        val inviteeToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000021",
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val workspace = repository.createWorkspace(
            ownerUserId = "00000000-0000-0000-0000-000000000020",
            name = "Comedy Cellar Moscow",
            slug = "comedy-cellar-moscow",
        )

        configureWorkspaceRoutes(repository, tokenService)

        val createInvitationResponse = client.post("/api/v1/workspaces/${workspace.id}/invitations") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"invitee_identifier":"checker_user","permission_role":"checker"}""")
        }
        val membershipId = Regex(""""membership_id":"([^"]+)"""")
            .find(createInvitationResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("membership id not found in response")

        val acceptResponse = client.post("/api/v1/workspace-invitations/$membershipId/respond") {
            header(HttpHeaders.Authorization, "Bearer $inviteeToken")
            contentType(ContentType.Application.Json)
            setBody("""{"decision":"accept"}""")
        }

        assertEquals(HttpStatusCode.OK, acceptResponse.status)
        val listResponse = client.get("/api/v1/workspaces") {
            header(HttpHeaders.Authorization, "Bearer $inviteeToken")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listBody = listResponse.bodyAsText()
        assertTrue(listBody.contains("Comedy Cellar Moscow"))
        assertTrue(listBody.contains(""""permission_role":"checker""""))
        assertTrue(repository.findById("00000000-0000-0000-0000-000000000021")?.roles?.contains(UserRole.ORGANIZER) == true)
    }

    @Test
    fun `owner can update workspace membership role`() = testApplication {
        val repository = InMemoryTelegramUserRepository().apply {
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000020",
                    displayName = "Workspace Owner",
                    username = "owner",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.TELEGRAM),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000021",
                    displayName = "Checker User",
                    username = "checker_user",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.PASSWORD),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
        }
        val tokenService = tokenService()
        val ownerToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000020",
            provider = AuthProvider.TELEGRAM,
        ).accessToken
        val workspace = repository.createWorkspace(
            ownerUserId = "00000000-0000-0000-0000-000000000020",
            name = "Comedy Cellar Moscow",
            slug = "comedy-cellar-moscow",
        )

        configureWorkspaceRoutes(repository, tokenService)

        val createInvitationResponse = client.post("/api/v1/workspaces/${workspace.id}/invitations") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"invitee_identifier":"checker_user","permission_role":"checker"}""")
        }
        val membershipId = Regex(""""membership_id":"([^"]+)"""")
            .find(createInvitationResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("membership id not found in response")

        val updateResponse = client.patch("/api/v1/workspaces/${workspace.id}/memberships/$membershipId") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"permission_role":"host"}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updateBody = updateResponse.bodyAsText()
        assertTrue(updateBody.contains(""""permission_role":"host""""))
    }

    @Test
    fun `manager cannot promote membership to manager`() = testApplication {
        val repository = InMemoryTelegramUserRepository().apply {
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000020",
                    displayName = "Workspace Owner",
                    username = "owner",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.TELEGRAM),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000021",
                    displayName = "Workspace Manager",
                    username = "manager_user",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.PASSWORD),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000022",
                    displayName = "Checker User",
                    username = "checker_user",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.PASSWORD),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
        }
        val tokenService = tokenService()
        val ownerToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000020",
            provider = AuthProvider.TELEGRAM,
        ).accessToken
        val managerToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000021",
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val workspace = repository.createWorkspace(
            ownerUserId = "00000000-0000-0000-0000-000000000020",
            name = "Comedy Cellar Moscow",
            slug = "comedy-cellar-moscow",
        )

        configureWorkspaceRoutes(repository, tokenService)

        val managerInvitationResponse = client.post("/api/v1/workspaces/${workspace.id}/invitations") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"invitee_identifier":"manager_user","permission_role":"manager"}""")
        }
        val managerMembershipId = Regex(""""membership_id":"([^"]+)"""")
            .find(managerInvitationResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("manager membership id not found")
        client.post("/api/v1/workspace-invitations/$managerMembershipId/respond") {
            header(HttpHeaders.Authorization, "Bearer $managerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"decision":"accept"}""")
        }

        val checkerInvitationResponse = client.post("/api/v1/workspaces/${workspace.id}/invitations") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"invitee_identifier":"checker_user","permission_role":"checker"}""")
        }
        val checkerMembershipId = Regex(""""membership_id":"([^"]+)"""")
            .find(checkerInvitationResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("checker membership id not found")

        val forbiddenResponse = client.patch("/api/v1/workspaces/${workspace.id}/memberships/$checkerMembershipId") {
            header(HttpHeaders.Authorization, "Bearer $managerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"permission_role":"manager"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, forbiddenResponse.status)
        assertTrue(forbiddenResponse.bodyAsText().contains("membership_role_not_assignable"))
    }

    private fun ApplicationTestBuilder.configureWorkspaceRoutes(
        repository: InMemoryTelegramUserRepository,
        tokenService: JwtSessionTokenService,
    ) {
        environment {
            config = MapApplicationConfig()
        }
        application {
            this.install(ContentNegotiation) {
                json()
            }
            routing {
                WorkspaceRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    userRepository = repository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }
    }

    private fun tokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                issuer = "test",
                secret = "0123456789abcdef0123456789abcdef",
                accessTtlSeconds = 3600L,
                refreshTtlSeconds = 86400L,
            ),
        )
    }
}

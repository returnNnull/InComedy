package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthLaunchRequest
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.AuthStateGenerator
import com.bam.incomedy.feature.auth.domain.SessionTerminationService
import com.bam.incomedy.feature.auth.domain.SessionValidationService
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider
import com.bam.incomedy.feature.auth.domain.SocialAuthService
import com.bam.incomedy.feature.auth.domain.ValidatedSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @Test
    fun `when provider clicked then emits launch effect`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = FakeProvider(AuthProviderType.VK)
        val viewModel = createViewModel(
            provider = provider,
            dispatcher = dispatcher,
        )
        val effectDeferred = async { viewModel.effects.first() }

        viewModel.onIntent(AuthIntent.OnProviderClick(AuthProviderType.VK))
        advanceUntilIdle()

        val effect = effectDeferred.await() as AuthEffect.OpenExternalAuth
        assertEquals(AuthProviderType.VK, effect.provider)
        assertTrue(effect.url.contains("vk.local/auth"))
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(AuthProviderType.VK, viewModel.state.value.selectedProvider)
    }

    @Test
    fun `when callback has valid state then session is set`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = FakeProvider(AuthProviderType.GOOGLE)
        val viewModel = createViewModel(
            provider = provider,
            dispatcher = dispatcher,
        )

        viewModel.onIntent(AuthIntent.OnProviderClick(AuthProviderType.GOOGLE))
        advanceUntilIdle()
        viewModel.onIntent(
            AuthIntent.OnAuthCallback(
                provider = AuthProviderType.GOOGLE,
                code = "ok",
                state = "fixed_state",
            ),
        )
        advanceUntilIdle()

        val session = viewModel.state.value.session
        assertNotNull(session)
        assertEquals(AuthProviderType.GOOGLE, session.provider)
        assertTrue(viewModel.state.value.isAuthorized)
    }

    @Test
    fun `when callback has invalid state then returns error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = FakeProvider(AuthProviderType.TELEGRAM)
        val viewModel = createViewModel(
            provider = provider,
            dispatcher = dispatcher,
        )

        viewModel.onIntent(AuthIntent.OnProviderClick(AuthProviderType.TELEGRAM))
        advanceUntilIdle()
        viewModel.onIntent(
            AuthIntent.OnAuthCallback(
                provider = AuthProviderType.TELEGRAM,
                code = "ok",
                state = "wrong_state",
            ),
        )
        advanceUntilIdle()

        assertEquals("Invalid auth state for TELEGRAM", viewModel.state.value.errorMessage)
        assertNull(viewModel.state.value.session)
    }

    @Test
    fun `when launch fails then error is exposed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = FakeProvider(AuthProviderType.VK, failLaunch = true)
        val viewModel = createViewModel(
            provider = provider,
            dispatcher = dispatcher,
        )

        viewModel.onIntent(AuthIntent.OnProviderClick(AuthProviderType.VK))
        advanceUntilIdle()

        assertEquals("launch_failed", viewModel.state.value.errorMessage)
    }

    private fun createViewModel(
        provider: SocialAuthProvider,
        dispatcher: TestDispatcher,
    ): AuthViewModel {
        return AuthViewModel(
            socialAuthService = SocialAuthService(listOf(provider)),
            sessionValidationService = FakeSessionValidationService(),
            sessionTerminationService = FakeSessionTerminationService(),
            stateGenerator = FixedStateGenerator,
            dispatcher = dispatcher,
        )
    }
}

private object FixedStateGenerator : AuthStateGenerator {
    override fun next(): String = "fixed_state"
}

private class FakeSessionValidationService : SessionValidationService {
    override suspend fun validate(accessToken: String): Result<ValidatedSession> {
        return Result.failure(IllegalStateException("not_used_in_this_test"))
    }
}

private class FakeSessionTerminationService : SessionTerminationService {
    override suspend fun terminate(accessToken: String): Result<Unit> {
        return Result.success(Unit)
    }
}

private class FakeProvider(
    override val type: AuthProviderType,
    private val failLaunch: Boolean = false,
    private val failComplete: Boolean = false,
) : SocialAuthProvider {
    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        if (failLaunch) return Result.failure(IllegalStateException("launch_failed"))
        return Result.success(
            AuthLaunchRequest(
                provider = type,
                state = state,
                url = "https://${type.name.lowercase()}.local/auth?state=$state",
            ),
        )
    }

    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        if (failComplete) return Result.failure(IllegalStateException("complete_failed"))
        return Result.success(
            AuthSession(
                provider = type,
                userId = "${type.name.lowercase()}_user",
                accessToken = "token_$code",
            ),
        )
    }
}

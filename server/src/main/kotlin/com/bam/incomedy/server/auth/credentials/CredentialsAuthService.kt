package com.bam.incomedy.server.auth.credentials

import com.bam.incomedy.server.auth.IssuedAuthSession
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.DuplicateCredentialLoginException
import com.bam.incomedy.server.db.UserRepository
import java.time.Instant
import java.util.Locale

class CredentialsAuthService(
    private val userRepository: UserRepository,
    private val tokenService: JwtSessionTokenService,
    private val passwordHasher: PasswordHasher,
) {
    private val missingUserHash = passwordHasher.hash(MISSING_USER_PASSWORD.toCharArray())

    fun register(login: String, password: String): IssuedAuthSession {
        val normalizedLogin = normalizeLogin(login)
        validatePassword(password)
        val passwordHash = passwordHasher.hash(password.toCharArray())
        val user = userRepository.createPasswordIdentity(
            login = normalizedLogin,
            normalizedLogin = normalizedLogin,
            passwordHash = passwordHash,
        )
        return issueSession(user.id)
    }

    fun login(login: String, password: String): IssuedAuthSession {
        val normalizedLogin = normalizeLogin(login)
        validatePassword(password)
        val credentialAccount = userRepository.findPasswordIdentity(normalizedLogin)
        val passwordChars = password.toCharArray()
        val isValid = if (credentialAccount == null) {
            passwordHasher.verify(passwordChars, missingUserHash)
            false
        } else {
            passwordHasher.verify(passwordChars, credentialAccount.passwordHash)
        }
        if (!isValid || credentialAccount == null) {
            throw InvalidCredentialsException
        }
        return issueSession(credentialAccount.user.id)
    }

    private fun issueSession(userId: String): IssuedAuthSession {
        val now = Instant.now()
        val tokens = tokenService.issue(
            userId = userId,
            provider = AuthProvider.PASSWORD,
        )
        userRepository.storeRefreshToken(
            userId = userId,
            tokenHash = tokenService.refreshTokenHash(tokens.refreshToken),
            expiresAt = tokenService.refreshExpiryInstant(now),
        )
        val user = userRepository.findById(userId) ?: error("Stored user not found after credential auth")
        return IssuedAuthSession(
            provider = AuthProvider.PASSWORD.wireName,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresInSeconds = tokens.expiresInSeconds,
            user = user,
        )
    }

    private fun normalizeLogin(raw: String): String {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        if (!LOGIN_REGEX.matches(normalized)) {
            throw InvalidCredentialLoginException
        }
        return normalized
    }

    private fun validatePassword(password: String) {
        val trimmed = password.trim()
        if (trimmed.length !in PASSWORD_LENGTH_RANGE) {
            throw WeakPasswordException
        }
    }

    private companion object {
        val LOGIN_REGEX = Regex("^[a-z0-9](?:[a-z0-9._-]{1,30}[a-z0-9])?$")
        val PASSWORD_LENGTH_RANGE = 8..128
        const val MISSING_USER_PASSWORD = "missing-user-password"
    }
}

object InvalidCredentialLoginException : IllegalArgumentException("Credential login is invalid")

object WeakPasswordException : IllegalArgumentException("Password does not meet security requirements")

object InvalidCredentialsException : IllegalArgumentException("Credential login or password is invalid")

package com.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.config.JwtSettings
import com.database.UserRecord
import com.database.UsersTable
import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID

class AuthService(
    private val jwtSettings: JwtSettings
) {
    private val algorithm = Algorithm.HMAC256(jwtSettings.secret)
    private val verifier = JWT
        .require(algorithm)
        .withAudience(jwtSettings.audience)
        .withIssuer(jwtSettings.domain)
        .build()

    fun register(request: RegisterRequest?): AuthSessionResponse {
        val email = normalizeEmail(request?.email)
        val password = validatePassword(request?.password)
        val name = normalizeName(request?.name) ?: email.substringBefore("@")

        if (UsersTable.findByEmail(email) != null) {
            throw AuthException(HttpStatusCode.Conflict, "Email is already registered")
        }

        val user = UsersTable.create(
            email = email,
            passwordHash = PasswordHasher.hash(password),
            name = name
        )

        return AuthSessionResponse(
            user = user.toProfileResponse(),
            tokens = issueTokens(user.id, user.email)
        )
    }

    fun login(request: LoginRequest?): AuthSessionResponse {
        val email = normalizeEmail(request?.email)
        val password = request?.password?.takeIf { it.isNotBlank() }
            ?: throw AuthException(HttpStatusCode.BadRequest, "Password is required")
        val user = UsersTable.findByEmailWithPassword(email)

        if (user == null || !PasswordHasher.verify(password, user.passwordHash)) {
            throw AuthException(HttpStatusCode.Unauthorized, "Invalid email or password")
        }

        return AuthSessionResponse(
            user = user.toProfileResponse(),
            tokens = issueTokens(user.id, user.email)
        )
    }

    fun refresh(request: RefreshRequest?): TokenPair {
        val refreshToken = request?.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw AuthException(HttpStatusCode.BadRequest, "Refresh token is required")
        val decodedToken = try {
            verifier.verify(refreshToken)
        } catch (error: JWTVerificationException) {
            throw AuthException(HttpStatusCode.Unauthorized, "Invalid refresh token")
        }

        if (decodedToken.getClaim("type").asString() != JwtTokenType.REFRESH) {
            throw AuthException(HttpStatusCode.Unauthorized, "Invalid refresh token")
        }

        val userId = decodedToken.subject.toUuidOrNull()
            ?: throw AuthException(HttpStatusCode.Unauthorized, "Invalid refresh token")
        val user = UsersTable.findById(userId)
            ?: throw AuthException(HttpStatusCode.Unauthorized, "User not found")

        return issueTokens(user.id, user.email)
    }

    fun currentUser(userId: UUID): UserProfileResponse {
        val user = UsersTable.findById(userId)
            ?: throw AuthException(HttpStatusCode.NotFound, "User not found")

        return user.toProfileResponse()
    }

    fun changePassword(userId: UUID, request: ChangePasswordRequest?): AuthMessageResponse {
        val currentPassword = request?.currentPassword?.takeIf { it.isNotBlank() }
            ?: throw AuthException(HttpStatusCode.BadRequest, "Current password is required")
        val newPassword = validatePassword(request.newPassword)

        val user = UsersTable.findByIdWithPassword(userId)
            ?: throw AuthException(HttpStatusCode.NotFound, "User not found")

        if (!PasswordHasher.verify(currentPassword, user.passwordHash)) {
            throw AuthException(HttpStatusCode.Unauthorized, "Current password is incorrect")
        }

        UsersTable.updatePassword(userId, PasswordHasher.hash(newPassword))
        return AuthMessageResponse("Password changed successfully")
    }

    private fun issueTokens(userId: String, email: String): TokenPair {
        return TokenPair(
            accessToken = createToken(
                userId = userId,
                email = email,
                type = JwtTokenType.ACCESS,
                expiresInSeconds = jwtSettings.accessTokenTtlSeconds
            ),
            refreshToken = createToken(
                userId = userId,
                email = email,
                type = JwtTokenType.REFRESH,
                expiresInSeconds = jwtSettings.refreshTokenTtlSeconds
            ),
            expiresInSeconds = jwtSettings.accessTokenTtlSeconds
        )
    }

    private fun createToken(userId: String, email: String, type: String, expiresInSeconds: Long): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(expiresInSeconds)

        return JWT.create()
            .withAudience(jwtSettings.audience)
            .withIssuer(jwtSettings.domain)
            .withSubject(userId)
            .withClaim("type", type)
            .withClaim("email", email)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
    }

    private fun normalizeEmail(email: String?): String {
        val normalized = email
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: throw AuthException(HttpStatusCode.BadRequest, "Email is required")

        if (normalized.length > 255 || !emailRegex.matches(normalized)) {
            throw AuthException(HttpStatusCode.BadRequest, "Email has invalid format")
        }

        return normalized
    }

    private fun validatePassword(password: String?): String {
        val value = password?.takeIf { it.isNotBlank() }
            ?: throw AuthException(HttpStatusCode.BadRequest, "Password is required")

        if (value.length < minPasswordLength) {
            throw AuthException(HttpStatusCode.BadRequest, "Password must be at least $minPasswordLength characters")
        }

        return value
    }

    private fun normalizeName(name: String?): String? {
        val normalized = name
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (normalized != null && normalized.length > maxNameLength) {
            throw AuthException(HttpStatusCode.BadRequest, "Name must be at most $maxNameLength characters")
        }

        return normalized
    }

    private fun UserRecord.toProfileResponse(): UserProfileResponse {
        return UserProfileResponse(
            id = id,
            name = name,
            email = email,
            currency = currency,
            timezone = timezone
        )
    }

    private fun com.database.UserCredentialsRecord.toProfileResponse(): UserProfileResponse {
        return UserProfileResponse(
            id = id,
            name = name,
            email = email,
            currency = currency,
            timezone = timezone
        )
    }

    private fun String?.toUuidOrNull(): UUID? {
        return this?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    private companion object {
        const val minPasswordLength = 8
        const val maxNameLength = 100
        val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

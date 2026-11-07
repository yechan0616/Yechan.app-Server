package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.config.AuthProperties
import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.SessionResponse
import app.yechan.server.domain.auth.entity.Administrator
import app.yechan.server.domain.auth.entity.StudioSession
import app.yechan.server.domain.auth.repository.StudioSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID

@Service
class StudioSessions(
    private val repository: StudioSessionRepository,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(
        administrator: Administrator,
        userAgent: String?,
    ): IssuedSession {
        val now = clock.instant()
        val token =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteArray(TOKEN_BYTES).also(random::nextBytes),
            )
        val session =
            repository.save(
                StudioSession(
                    administrator = administrator,
                    tokenHash = hash(token),
                    expiresAt = now.plus(properties.session.absoluteTimeout),
                    userAgent = userAgent?.take(USER_AGENT_LENGTH),
                    issuedAt = now,
                    idleTimeout = properties.session.idleTimeout,
                ),
            )
        return IssuedSession(token, session.toResponse())
    }

    @Transactional
    fun authenticate(token: String): AdministratorPrincipal? {
        if (!TOKEN_FORMAT.matches(token)) return null
        val session = repository.findByTokenHash(hash(token)) ?: return null
        val now = clock.instant()
        if (!session.isActive(now)) return null
        session.touch(now, properties.session.idleTimeout)
        val administrator = session.administrator
        return AdministratorPrincipal(administrator.id, administrator.username, administrator.displayName, session.id)
    }

    @Transactional(readOnly = true)
    fun describe(sessionId: UUID): SessionResponse? = repository.findById(sessionId).orElse(null)?.toResponse()

    @Transactional
    fun revoke(sessionId: UUID) = repository.deleteById(sessionId)

    @Transactional
    fun revokeOthers(principal: AdministratorPrincipal): Int =
        repository.deleteOthers(principal.id, principal.sessionId)

    @Transactional
    fun purgeExpired(): Int = repository.deleteExpired(clock.instant())

    private fun StudioSession.toResponse() =
        SessionResponse(
            administrator = administrator.toResponse(),
            expiresAt = minOf(expiresAt, idleExpiresAt),
        )

    private fun hash(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

    private companion object {
        const val TOKEN_BYTES = 32
        const val USER_AGENT_LENGTH = 255
        val TOKEN_FORMAT = Regex("^[A-Za-z0-9_-]{43}$")
    }
}

class IssuedSession(
    val token: String,
    val session: SessionResponse,
)

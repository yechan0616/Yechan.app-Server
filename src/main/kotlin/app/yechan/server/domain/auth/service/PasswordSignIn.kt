package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.dto.PasswordSignInRequest
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.AdministratorRepository
import app.yechan.server.global.error.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class PasswordSignIn(
    private val administrators: AdministratorRepository,
    private val encoder: PasswordEncoder,
    private val policy: PasswordPolicy,
    private val sessions: StudioSessions,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val decoy: String by lazy { encoder.encode(UUID.randomUUID().toString()).orEmpty() }

    // Failed attempts must be persisted even though the request ends with an error.
    @Transactional(noRollbackFor = [BusinessException::class])
    fun signIn(
        request: PasswordSignInRequest,
        userAgent: String?,
    ): IssuedSession {
        val now = clock.instant()
        val administrator = administrators.findByEmail(policy.normalizeEmail(request.email))
        val hash = administrator?.passwordHash
        if (administrator == null || hash == null || administrator.isLocked(now)) {
            // Spend the same time as a real check so responses do not reveal which emails exist.
            encoder.matches(request.password, decoy)
            throw failed()
        }
        if (!encoder.matches(request.password, hash)) {
            administrator.recordFailedSignIn(now, MAX_FAILURES, LOCK_DURATION)
            if (administrator.isLocked(now)) log.warn("Password sign-in locked for administrator {}", administrator.id)
            throw failed()
        }
        administrator.recordSuccessfulSignIn()
        if (encoder.upgradeEncoding(hash)) administrator.rehashPassword(checkNotNull(encoder.encode(request.password)))
        return sessions.issue(administrator, userAgent)
    }

    private fun failed() = BusinessException(AuthErrorCode.AUTH_SIGN_IN_FAILED)

    private companion object {
        const val MAX_FAILURES = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}

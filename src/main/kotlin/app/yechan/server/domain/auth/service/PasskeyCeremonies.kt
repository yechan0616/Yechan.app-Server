package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.config.AuthProperties
import app.yechan.server.domain.auth.entity.CeremonyPurpose
import app.yechan.server.domain.auth.entity.PasskeyCeremony
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.PasskeyCeremonyRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class PasskeyCeremonies(
    private val repository: PasskeyCeremonyRepository,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun open(
        purpose: CeremonyPurpose,
        challenge: ByteArray,
        administratorId: UUID? = null,
        userHandle: ByteArray? = null,
        username: String? = null,
        displayName: String? = null,
        label: String? = null,
    ): PasskeyCeremony {
        val now = clock.instant()
        // Unauthenticated endpoint: cap pending ceremonies.
        if (repository.countByExpiresAtAfter(now) >=
            MAX_PENDING
        ) {
            throw BusinessException(AuthErrorCode.AUTH_RATE_LIMITED)
        }
        return repository.save(
            PasskeyCeremony(
                purpose = purpose,
                challenge = challenge,
                expiresAt = now.plus(properties.ceremonyTtl),
                administratorId = administratorId,
                userHandle = userHandle,
                username = username,
                displayName = displayName,
                label = label,
            ),
        )
    }

    // Own transaction: a challenge stays consumed even if the caller rolls back.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = [BusinessException::class])
    fun consume(
        id: UUID,
        purpose: CeremonyPurpose,
    ): PasskeyCeremony {
        val ceremony = repository.findById(id).orElse(null) ?: throw invalid()
        if (repository.consume(id) != 1) throw invalid()
        if (ceremony.purpose != purpose || ceremony.isExpired(clock.instant())) throw invalid()
        return ceremony
    }

    @Transactional
    fun purgeExpired(): Int = repository.deleteExpired(clock.instant())

    private fun invalid() = BusinessException(AuthErrorCode.AUTH_CEREMONY_INVALID)

    private companion object {
        const val MAX_PENDING = 500L
    }
}

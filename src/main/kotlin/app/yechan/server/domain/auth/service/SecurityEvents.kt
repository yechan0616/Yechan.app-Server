package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.ClientContext
import app.yechan.server.domain.auth.dto.SecurityEventResponse
import app.yechan.server.domain.auth.entity.SecurityEvent
import app.yechan.server.domain.auth.entity.SecurityEventType
import app.yechan.server.domain.auth.entity.SignInMethod
import app.yechan.server.domain.auth.repository.SecurityEventRepository
import app.yechan.server.global.common.response.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class SecurityEvents(
    private val repository: SecurityEventRepository,
    private val clock: Clock,
) {
    // Own transaction: failures are recorded even when the surrounding request rolls back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        type: SecurityEventType,
        administratorId: UUID?,
        client: ClientContext,
        method: SignInMethod? = null,
        detail: String? = null,
    ) {
        repository.save(
            SecurityEvent(
                administratorId,
                type,
                method,
                client.ipAddress,
                client.userAgent,
                detail?.take(DETAIL_LENGTH),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun list(
        principal: AdministratorPrincipal,
        page: Int,
        size: Int,
    ): PageResponse<SecurityEventResponse> =
        PageResponse.of(
            repository.findAllByAdministratorIdOrderByCreatedAtDesc(principal.id, PageRequest.of(page, size)),
        ) {
            SecurityEventResponse(
                it.id,
                it.type,
                it.method,
                it.ipAddress,
                it.userAgent,
                it.detail,
                checkNotNull(it.createdAt),
            )
        }

    @Transactional
    fun purgeExpired(): Int = repository.deleteOlderThan(clock.instant().minus(RETENTION))

    private companion object {
        const val DETAIL_LENGTH = 200
        val RETENTION: Duration = Duration.ofDays(180)
    }
}

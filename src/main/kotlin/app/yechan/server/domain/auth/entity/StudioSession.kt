package app.yechan.server.domain.auth.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

@Entity
@Table(name = "studio_session")
class StudioSession(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "administrator_id", nullable = false, updatable = false)
    val administrator: Administrator,
    @Column(nullable = false, unique = true, updatable = false)
    val tokenHash: ByteArray,
    @Column(nullable = false, updatable = false)
    val expiresAt: Instant,
    @Column(length = 255, updatable = false)
    val userAgent: String?,
    @Column(length = 45, updatable = false)
    val ipAddress: String?,
    issuedAt: Instant,
    idleTimeout: Duration,
) : BaseEntity() {
    @Column(nullable = false)
    var lastSeenAt: Instant = issuedAt
        protected set

    @Column(nullable = false)
    var idleExpiresAt: Instant = minOf(issuedAt.plus(idleTimeout), expiresAt)
        protected set

    fun isActive(now: Instant): Boolean = now.isBefore(expiresAt) && now.isBefore(idleExpiresAt)

    fun touch(
        now: Instant,
        idleTimeout: Duration,
    ): Boolean {
        if (Duration.between(lastSeenAt, now) < TOUCH_INTERVAL) return false
        lastSeenAt = now
        idleExpiresAt = minOf(now.plus(idleTimeout), expiresAt)
        return true
    }

    private companion object {
        val TOUCH_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}

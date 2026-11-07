package app.yechan.server.domain.auth.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "passkey_ceremony")
class PasskeyCeremony(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val purpose: CeremonyPurpose,
    @Column(nullable = false, updatable = false)
    val challenge: ByteArray,
    @Column(nullable = false, updatable = false)
    val expiresAt: Instant,
    @Column(length = 45, updatable = false)
    val ipAddress: String? = null,
    @Column(updatable = false)
    val administratorId: UUID? = null,
    @Column(updatable = false)
    val userHandle: ByteArray? = null,
    @Column(length = 32, updatable = false)
    val username: String? = null,
    @Column(length = 100, updatable = false)
    val displayName: String? = null,
    @Column(length = 100, updatable = false)
    val label: String? = null,
) : BaseEntity() {
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
}

enum class CeremonyPurpose {
    ONBOARDING,

    ENROLLMENT,

    SIGN_IN,
}

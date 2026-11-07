package app.yechan.server.domain.auth.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "security_event")
class SecurityEvent(
    @Column(updatable = false)
    val administratorId: UUID?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    val type: SecurityEventType,
    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    val method: SignInMethod?,
    @Column(length = 45, updatable = false)
    val ipAddress: String?,
    @Column(length = 255, updatable = false)
    val userAgent: String?,
    @Column(length = 200, updatable = false)
    val detail: String?,
) : BaseEntity()

enum class SecurityEventType {
    ONBOARDED,
    SIGNED_IN,
    SIGN_IN_FAILED,
    ACCOUNT_LOCKED,
    SIGNED_OUT,
    SESSION_REVOKED,
    EMAIL_CHANGED,
    PASSWORD_CHANGED,
    PASSWORD_DISABLED,
    PASSKEY_ADDED,
    PASSKEY_REMOVED,
}

enum class SignInMethod { PASSKEY, PASSWORD }

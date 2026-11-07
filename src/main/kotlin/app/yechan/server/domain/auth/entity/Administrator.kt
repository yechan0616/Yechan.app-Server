package app.yechan.server.domain.auth.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

@Entity
@Table(name = "administrator")
class Administrator(
    @Column(nullable = false, unique = true, length = 32, updatable = false)
    val username: String,
    displayName: String,
    // WebAuthn user.id: random bytes, never personal data.
    @Column(nullable = false, unique = true, updatable = false)
    val userHandle: ByteArray,
) : BaseEntity() {
    @Column(nullable = false, length = 100)
    var displayName: String = displayName
        protected set

    @Column(unique = true, length = 254)
    var email: String? = null
        protected set

    @Column(length = 255)
    var passwordHash: String? = null
        protected set

    var passwordChangedAt: Instant? = null
        protected set

    @Column(nullable = false)
    var failedSignIns: Int = 0
        protected set

    var lockedUntil: Instant? = null
        protected set

    val passwordEnabled: Boolean get() = passwordHash != null

    fun rename(displayName: String) {
        this.displayName = displayName
    }

    fun changeEmail(email: String) {
        this.email = email
    }

    fun changePassword(
        hash: String,
        at: Instant,
    ) {
        passwordHash = hash
        passwordChangedAt = at
        failedSignIns = 0
        lockedUntil = null
    }

    fun rehashPassword(hash: String) {
        passwordHash = hash
    }

    fun isLocked(now: Instant): Boolean = lockedUntil?.isAfter(now) == true

    fun recordFailedSignIn(
        now: Instant,
        maxFailures: Int,
        lockDuration: Duration,
    ) {
        failedSignIns++
        if (failedSignIns >= maxFailures) {
            lockedUntil = now.plus(lockDuration)
            failedSignIns = 0
        }
    }

    fun recordSuccessfulSignIn() {
        failedSignIns = 0
        lockedUntil = null
    }
}

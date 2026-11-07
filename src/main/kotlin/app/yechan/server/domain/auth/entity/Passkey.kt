package app.yechan.server.domain.auth.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "passkey")
class Passkey(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "administrator_id", nullable = false, updatable = false)
    val administrator: Administrator,
    @Column(nullable = false, unique = true, updatable = false)
    val credentialId: ByteArray,
    @Column(nullable = false, updatable = false)
    val attestedCredentialData: ByteArray,
    @Column(nullable = false, length = 32, updatable = false)
    val attestationFormat: String,
    @Column(nullable = false, updatable = false)
    val backupEligible: Boolean,
    transports: Set<String>,
    signCount: Long,
    userVerified: Boolean,
    backedUp: Boolean,
    label: String,
) : BaseEntity() {
    @Column(name = "transports", nullable = false, length = 200, updatable = false)
    private val encodedTransports: String = transports.sorted().joinToString(",")

    @Column(nullable = false)
    var signCount: Long = signCount
        protected set

    @Column(nullable = false)
    var userVerified: Boolean = userVerified
        protected set

    @Column(nullable = false)
    var backedUp: Boolean = backedUp
        protected set

    @Column(nullable = false, length = 100)
    var label: String = label
        protected set

    var lastUsedAt: Instant? = null
        protected set

    fun transports(): Set<String> = encodedTransports.split(',').filter { it.isNotEmpty() }.toSet()

    fun recordSignIn(
        signCount: Long,
        userVerified: Boolean,
        backedUp: Boolean,
        at: Instant,
    ) {
        this.signCount = signCount
        this.userVerified = this.userVerified || userVerified
        this.backedUp = backedUp
        this.lastUsedAt = at
    }

    fun rename(label: String) {
        this.label = label
    }
}

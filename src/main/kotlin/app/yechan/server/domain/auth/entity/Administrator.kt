package app.yechan.server.domain.auth.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

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
}

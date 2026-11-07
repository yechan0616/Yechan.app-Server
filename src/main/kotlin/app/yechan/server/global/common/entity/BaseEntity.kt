package app.yechan.server.global.common.entity

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.hibernate.id.uuid.UuidVersion7Strategy
import org.springframework.data.domain.Persistable
import java.util.UUID

// Ids are assigned up front (UUIDv7, or a caller-supplied one such as a bundle ref), so "new" is tracked explicitly.
@MappedSuperclass
abstract class BaseEntity(
    id: UUID? = null,
) : BaseTimeEntity(),
    Persistable<UUID> {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private val identifier: UUID = id ?: UuidVersion7Strategy.INSTANCE.generateUuid(null)

    @Transient
    private var persisted: Boolean = false

    override fun getId(): UUID = identifier

    override fun isNew(): Boolean = !persisted

    @PostPersist
    @PostLoad
    protected fun markPersisted() {
        persisted = true
    }
}

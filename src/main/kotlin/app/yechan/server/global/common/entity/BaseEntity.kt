package app.yechan.server.global.common.entity

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity : BaseTimeEntity() {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private var identifier: UUID? = null

    val id: UUID
        get() = checkNotNull(identifier) { "${javaClass.simpleName} is not persisted yet" }
}

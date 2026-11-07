package app.yechan.server.domain.media.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "asset")
class Asset(
    @Column(nullable = false, unique = true, length = 200, updatable = false)
    val storageKey: String,
    @Column(nullable = false, length = 50, updatable = false)
    val contentType: String,
    @Column(nullable = false, updatable = false)
    val byteSize: Long,
    @Column(nullable = false, updatable = false)
    val width: Int,
    @Column(nullable = false, updatable = false)
    val height: Int,
    @Column(nullable = false, unique = true, updatable = false)
    val sha256: ByteArray,
    @Column(length = 255, updatable = false)
    val filename: String?,
) : BaseEntity()

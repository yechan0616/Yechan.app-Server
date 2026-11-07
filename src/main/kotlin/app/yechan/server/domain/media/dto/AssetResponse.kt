package app.yechan.server.domain.media.dto

import java.time.Instant
import java.util.UUID

data class AssetResponse(
    val id: UUID,
    val url: String,
    val contentType: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val filename: String?,
    val createdAt: Instant,
)

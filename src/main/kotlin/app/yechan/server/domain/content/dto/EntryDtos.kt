package app.yechan.server.domain.content.dto

import app.yechan.server.domain.content.entity.EntryStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import tools.jackson.databind.node.ObjectNode
import java.time.Instant
import java.util.UUID

data class CreateEntryRequest(
    @field:Size(max = 96)
    val slug: String? = null,
    @field:Min(0) @field:Max(1_000_000)
    val position: Int = 0,
    @field:NotNull
    val content: ObjectNode,
)

data class UpdateEntryRequest(
    @field:Size(max = 96)
    val slug: String? = null,
    @field:Min(0) @field:Max(1_000_000)
    val position: Int = 0,
    @field:NotNull
    val content: ObjectNode,
    @field:NotNull
    val revision: Long,
)

data class PublishEntryRequest(
    @field:NotNull
    val revision: Long,
)

data class EntryResponse(
    val id: UUID,
    val blueprint: String,
    val slug: String?,
    val position: Int,
    val status: EntryStatus,
    val content: ObjectNode,
    val publishedSlug: String?,
    val publishedAt: Instant?,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PublishedEntryResponse(
    val id: UUID,
    val slug: String?,
    val publishedAt: Instant,
    val content: ObjectNode,
)

data class ReorderEntriesRequest(
    @field:NotNull @field:Size(min = 1, max = 1_000)
    val entries: List<UUID>,
)

data class PreviewLinkResponse(
    val token: String,
    val expiresAt: Instant,
)

data class PreviewEntryResponse(
    val id: UUID,
    val blueprint: String,
    val slug: String?,
    val revision: Long,
    val content: ObjectNode,
)

data class RevisionSummaryResponse(
    val number: Int,
    val slug: String?,
    val createdAt: Instant,
)

data class RevisionResponse(
    val number: Int,
    val slug: String?,
    val content: ObjectNode,
    val createdAt: Instant,
)

data class RestoreRevisionRequest(
    @field:NotNull
    val revision: Long,
)

data class TrashedEntryResponse(
    val id: UUID,
    val blueprint: String,
    val slug: String?,
    val deletedAt: Instant,
    val purgeAt: Instant,
)

data class LocalesResponse(
    val supported: List<String>,
    val default: String,
)

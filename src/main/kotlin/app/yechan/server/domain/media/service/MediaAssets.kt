package app.yechan.server.domain.media.service

import app.yechan.server.domain.media.config.MediaProperties
import app.yechan.server.domain.media.dto.AssetResponse
import app.yechan.server.domain.media.entity.Asset
import app.yechan.server.domain.media.exception.MediaErrorCode
import app.yechan.server.domain.media.repository.AssetRepository
import app.yechan.server.global.common.response.PageResponse
import app.yechan.server.global.error.BusinessException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class MediaAssets(
    private val assets: AssetRepository,
    private val storage: AssetStorage,
    private val probe: ImageProbe,
    private val properties: MediaProperties,
    private val usages: ObjectProvider<AssetUsage>,
    private val clock: Clock,
) {
    @Transactional
    fun upload(
        bytes: ByteArray,
        filename: String?,
    ): UploadedAsset {
        if (bytes.isEmpty()) throw BusinessException(MediaErrorCode.ASSET_EMPTY)
        if (bytes.size > properties.maxFileSize.toBytes()) throw BusinessException(MediaErrorCode.ASSET_TOO_LARGE)
        val image = probe.inspect(bytes) ?: throw BusinessException(MediaErrorCode.ASSET_UNSUPPORTED_TYPE)
        if (image.width !in 1..MAX_SIDE || image.height !in 1..MAX_SIDE ||
            image.width.toLong() * image.height > MAX_PIXELS
        ) {
            throw BusinessException(MediaErrorCode.ASSET_DIMENSIONS_INVALID)
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        assets.findBySha256(digest)?.let { return UploadedAsset(it.toResponse(), created = false) }

        val key = "assets/${KEY_PREFIX.format(
            clock.instant().atOffset(ZoneOffset.UTC),
        )}/${UUID.randomUUID()}.${image.format.extension}"
        storage.put(key, bytes, image.format.contentType)
        try {
            val asset =
                assets.saveAndFlush(
                    Asset(
                        storageKey = key,
                        contentType = image.format.contentType,
                        byteSize = bytes.size.toLong(),
                        width = image.width,
                        height = image.height,
                        sha256 = digest,
                        filename = sanitize(filename),
                    ),
                )
            return UploadedAsset(asset.toResponse(), created = true)
        } catch (ex: RuntimeException) {
            runCatching { storage.delete(key) }
            throw ex
        }
    }

    @Transactional(readOnly = true)
    fun list(
        page: Int,
        size: Int,
    ): PageResponse<AssetResponse> =
        PageResponse.of(assets.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))) {
            it.toResponse()
        }

    @Transactional(readOnly = true)
    fun get(id: UUID): AssetResponse = require(id).toResponse()

    @Transactional(readOnly = true)
    fun findAll(ids: Collection<UUID>): Map<UUID, AssetResponse> =
        if (ids.isEmpty()) emptyMap() else assets.findAllById(ids).associate { it.id to it.toResponse() }

    @Transactional
    fun delete(id: UUID) {
        val asset = require(id)
        if (usages.orderedStream().anyMatch { it.isInUse(id) }) throw BusinessException(MediaErrorCode.ASSET_IN_USE)
        assets.delete(asset)
        assets.flush()
        storage.delete(asset.storageKey)
    }

    private fun require(id: UUID): Asset =
        assets.findById(id).orElse(null) ?: throw BusinessException(MediaErrorCode.ASSET_NOT_FOUND)

    private fun sanitize(filename: String?): String? =
        filename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.filterNot { it.isISOControl() }
            ?.trim()
            ?.take(MAX_FILENAME)
            ?.ifEmpty { null }

    private fun Asset.toResponse() =
        AssetResponse(
            id = id,
            url = storage.url(storageKey),
            contentType = contentType,
            byteSize = byteSize,
            width = width,
            height = height,
            filename = filename,
            createdAt = checkNotNull(createdAt),
        )

    private companion object {
        const val MAX_SIDE = 16_384
        const val MAX_PIXELS = 100_000_000L
        const val MAX_FILENAME = 255
        val KEY_PREFIX: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM")
    }
}

class UploadedAsset(
    val asset: AssetResponse,
    val created: Boolean,
)

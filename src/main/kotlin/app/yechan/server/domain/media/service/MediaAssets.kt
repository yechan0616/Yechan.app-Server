package app.yechan.server.domain.media.service

import app.yechan.server.domain.media.config.MediaProperties
import app.yechan.server.domain.media.dto.AssetResponse
import app.yechan.server.domain.media.entity.Asset
import app.yechan.server.domain.media.exception.MediaErrorCode
import app.yechan.server.domain.media.repository.AssetRepository
import app.yechan.server.global.common.response.PageResponse
import app.yechan.server.global.error.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class MediaAssets(
    private val assets: AssetRepository,
    private val storage: AssetStorage,
    private val probe: ImageProbe,
    private val stripper: MetadataStripper,
    private val properties: MediaProperties,
    private val usages: ObjectProvider<AssetUsage>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun checkSize(size: Long) {
        if (size > properties.maxFileSize.toBytes()) throw BusinessException(MediaErrorCode.ASSET_TOO_LARGE)
    }

    @Transactional
    fun upload(
        original: ByteArray,
        filename: String?,
    ): UploadedAsset {
        if (original.isEmpty()) throw BusinessException(MediaErrorCode.ASSET_EMPTY)
        checkSize(original.size.toLong())
        val image = probe.inspect(original) ?: throw BusinessException(MediaErrorCode.ASSET_UNSUPPORTED_TYPE)
        if (image.width !in 1..MAX_SIDE || image.height !in 1..MAX_SIDE ||
            image.width.toLong() * image.height > MAX_PIXELS
        ) {
            throw BusinessException(MediaErrorCode.ASSET_DIMENSIONS_INVALID)
        }
        val stripped =
            runCatching { stripper.strip(original, image.format) }.getOrNull()
                ?: throw BusinessException(MediaErrorCode.ASSET_UNSUPPORTED_TYPE)
        val bytes = stripped.bytes

        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        assets.findBySha256(digest)?.let { return UploadedAsset(it.toResponse(), created = false) }

        val folder = KEY_PREFIX.format(clock.instant().atOffset(ZoneOffset.UTC))
        val key = "assets/$folder/${UUID.randomUUID()}.${image.format.extension}"
        storage.put(key, bytes, image.format.contentType)
        try {
            val asset =
                assets.saveAndFlush(
                    Asset(
                        storageKey = key,
                        contentType = image.format.contentType,
                        byteSize = bytes.size.toLong(),
                        width = if (stripped.rotated) image.height else image.width,
                        height = if (stripped.rotated) image.width else image.height,
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
    fun unused(
        page: Int,
        size: Int,
    ): PageResponse<AssetResponse> {
        val all = assets.findAll().sortedByDescending { it.createdAt }
        val used = usedAmong(all.map { it.id })
        val unused = all.filterNot { it.id in used }
        val slice = unused.drop(page * size).take(size).map { it.toResponse() }
        return PageResponse(slice, page, size, unused.size.toLong(), (unused.size + size - 1) / size)
    }

    // Recent uploads are skipped: they may belong to an entry that is still being written.
    @Transactional
    fun deleteUnused(): Int {
        val cutoff = clock.instant().minus(CLEANUP_GRACE)
        val candidates = assets.findAll().filter { checkNotNull(it.createdAt).isBefore(cutoff) }
        val used = usedAmong(candidates.map { it.id })
        val targets = candidates.filterNot { it.id in used }
        assets.deleteAll(targets)
        deleteFromStorageAfterCommit(targets.map { it.storageKey })
        return targets.size
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

    @Transactional(readOnly = true)
    fun all(): List<AssetResponse> = assets.findAll().sortedBy { it.createdAt }.map { it.toResponse() }

    @Transactional(readOnly = true)
    fun download(id: UUID): ByteArray = storage.get(require(id).storageKey)

    @Transactional
    fun delete(id: UUID) {
        val asset = require(id)
        if (usedAmong(listOf(id)).isNotEmpty()) throw BusinessException(MediaErrorCode.ASSET_IN_USE)
        assets.delete(asset)
        deleteFromStorageAfterCommit(listOf(asset.storageKey))
    }

    // Objects go only once the row deletion is committed; a rollback must not leave a row without its file.
    private fun deleteFromStorageAfterCommit(keys: List<String>) {
        if (keys.isEmpty()) return
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    keys.forEach { key ->
                        runCatching {
                            storage.delete(
                                key,
                            )
                        }.onFailure { log.warn("Failed to delete object {}", key, it) }
                    }
                }
            },
        )
    }

    private fun usedAmong(ids: Collection<UUID>): Set<UUID> =
        usages
            .orderedStream()
            .toList()
            .flatMap {
                it.usedAmong(ids)
            }.toSet()

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
        val CLEANUP_GRACE: Duration = Duration.ofDays(1)
        const val MAX_PIXELS = 100_000_000L
        const val MAX_FILENAME = 255
        val KEY_PREFIX: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM")
    }
}

class UploadedAsset(
    val asset: AssetResponse,
    val created: Boolean,
)

package app.yechan.server.domain.media.service

import app.yechan.server.domain.media.config.MediaProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client

@Component
class AssetStorage(
    private val s3: S3Client,
    private val properties: MediaProperties,
) {
    fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        s3.putObject(
            {
                it
                    .bucket(properties.storage.bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(bytes.size.toLong())
                    .cacheControl(IMMUTABLE)
            },
            RequestBody.fromBytes(bytes),
        )
    }

    fun get(key: String): ByteArray =
        s3.getObjectAsBytes { it.bucket(properties.storage.bucket).key(key) }.asByteArray()

    fun delete(key: String) {
        s3.deleteObject { it.bucket(properties.storage.bucket).key(key) }
    }

    fun url(key: String): String = "${properties.publicBaseUrl}/$key"

    private companion object {
        const val IMMUTABLE = "public, max-age=31536000, immutable"
    }
}

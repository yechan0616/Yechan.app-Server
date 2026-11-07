package app.yechan.server.domain.media.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.net.URI

@ConfigurationProperties(prefix = "app.media")
data class MediaProperties(
    val publicBaseUrl: String,
    val maxFileSize: DataSize,
    val storage: Storage,
) {
    init {
        require(publicBaseUrl.startsWith("https://") || publicBaseUrl.startsWith("http://")) {
            "app.media.public-base-url must be an absolute URL"
        }
        require(!publicBaseUrl.endsWith("/")) { "app.media.public-base-url must not end with /" }
        require(maxFileSize.toBytes() in 1..MAX_FILE_SIZE.toBytes()) {
            "app.media.max-file-size must be at most $MAX_FILE_SIZE"
        }
    }

    data class Storage(
        val endpoint: URI,
        val region: String,
        val bucket: String,
        val accessKeyId: String,
        val secretAccessKey: String,
        val pathStyle: Boolean = true,
        val createBucket: Boolean = false,
    ) {
        init {
            require(bucket.isNotBlank() && region.isNotBlank()) { "app.media.storage bucket and region are required" }
            require(
                accessKeyId.isNotBlank() && secretAccessKey.isNotBlank(),
            ) { "app.media.storage credentials are required" }
        }

        override fun toString() = "Storage(endpoint=$endpoint, region=$region, bucket=$bucket, pathStyle=$pathStyle)"
    }

    private companion object {
        val MAX_FILE_SIZE: DataSize = DataSize.ofMegabytes(50)
    }
}

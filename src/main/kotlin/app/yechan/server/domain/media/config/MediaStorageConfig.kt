package app.yechan.server.domain.media.config

import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException

@Configuration(proxyBeanMethods = false)
class MediaStorageConfig {
    @Bean
    fun s3Client(properties: MediaProperties): S3Client {
        val storage = properties.storage
        return S3Client
            .builder()
            .endpointOverride(storage.endpoint)
            .region(Region.of(storage.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.accessKeyId, storage.secretAccessKey),
                ),
            ).forcePathStyle(storage.pathStyle)
            // R2 and local S3 mocks reject some of the SDK's default checksum headers.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build()
    }

    @Bean
    fun mediaBucketInitializer(
        s3: S3Client,
        properties: MediaProperties,
    ) = ApplicationRunner {
        val bucket = properties.storage.bucket
        if (!properties.storage.createBucket) return@ApplicationRunner
        try {
            s3.headBucket { it.bucket(bucket) }
        } catch (_: NoSuchBucketException) {
            s3.createBucket { it.bucket(bucket) }
        }
    }
}

package app.yechan.server

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    // Keep in sync with deploy/compose*.yaml.
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))

    @Bean
    fun s3Container(): S3MockContainer = S3MockContainer()

    @Bean
    fun s3Properties(s3: S3MockContainer) =
        DynamicPropertyRegistrar { registry ->
            registry.add("app.media.storage.endpoint", s3::endpoint)
            registry.add("app.media.public-base-url") { "${s3.endpoint()}/yechan-media" }
            registry.add("app.media.storage.create-bucket") { true }
        }
}

class S3MockContainer : GenericContainer<S3MockContainer>(DockerImageName.parse("adobe/s3mock:5.2.3")) {
    init {
        withExposedPorts(PORT)
        waitingFor(Wait.forListeningPort())
    }

    fun endpoint(): String = "http://$host:${getMappedPort(PORT)}"

    private companion object {
        const val PORT = 9090
    }
}

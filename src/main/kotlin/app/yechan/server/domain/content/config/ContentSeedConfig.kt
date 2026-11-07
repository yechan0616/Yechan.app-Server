package app.yechan.server.domain.content.config

import app.yechan.server.domain.content.service.ContentImporter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'\${app.seed.bundle:}'.isBlank()")
class ContentSeedConfig {
    @Bean
    fun contentSeedRunner(
        importer: ContentImporter,
        @Value("\${app.seed.bundle}") bundle: String,
    ) = ApplicationRunner {
        val report = importer.import(Path.of(bundle))
        LoggerFactory.getLogger(ContentSeedConfig::class.java).info("Imported content bundle {}: {}", bundle, report)
    }
}

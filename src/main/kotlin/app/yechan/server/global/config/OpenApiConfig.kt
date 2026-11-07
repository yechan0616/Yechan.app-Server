package app.yechan.server.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(Info().title("yechan.app API").version("v1"))
            .components(
                Components().addSecuritySchemes(
                    SESSION,
                    SecurityScheme()
                        .type(
                            SecurityScheme.Type.APIKEY,
                        ).`in`(SecurityScheme.In.COOKIE)
                        .name("studio_session"),
                ),
            )

    @Bean
    fun publicApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("public")
            .pathsToMatch("/api/v1/public/**")
            .build()

    @Bean
    fun studioApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("studio")
            .pathsToMatch("/api/v1/studio/**")
            .addOpenApiCustomizer { it.addSecurityItem(SecurityRequirement().addList(SESSION)) }
            .build()

    private companion object {
        const val SESSION = "session"
    }
}

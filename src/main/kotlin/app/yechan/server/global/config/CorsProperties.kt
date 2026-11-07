package app.yechan.server.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
    val publicOrigins: List<String>,
    val studioOrigins: List<String>,
) {
    init {
        check("public-origins", publicOrigins)
        check("studio-origins", studioOrigins)
    }

    private fun check(
        name: String,
        origins: List<String>,
    ) {
        require(origins.isNotEmpty()) { "app.cors.$name must not be empty" }
        require(origins.none { it == "*" }) { "app.cors.$name must not contain a wildcard" }
    }
}

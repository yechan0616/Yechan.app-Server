package app.yechan.server.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
    val allowedOrigins: List<String>,
) {
    init {
        require(allowedOrigins.isNotEmpty()) { "app.cors.allowed-origins must not be empty" }
        require(allowedOrigins.none { it == "*" }) { "app.cors.allowed-origins must not contain a wildcard" }
    }
}

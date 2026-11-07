package app.yechan.server.domain.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val relyingParty: RelyingParty,
    val session: Session,
    val ceremonyTtl: Duration,
    val setupToken: String? = null,
) {
    init {
        require(!ceremonyTtl.isNegative && ceremonyTtl <= Duration.ofMinutes(10)) {
            "app.auth.ceremony-ttl must be between 0 and 10 minutes"
        }
        require(setupToken.isNullOrEmpty() || setupToken.length >= MIN_SETUP_TOKEN_LENGTH) {
            "app.auth.setup-token must be at least $MIN_SETUP_TOKEN_LENGTH characters"
        }
    }

    val onboardingEnabled: Boolean get() = !setupToken.isNullOrEmpty()

    data class RelyingParty(
        val id: String,
        val name: String,
        val origins: List<String>,
    ) {
        init {
            require(id.isNotBlank() && !id.contains("://")) { "app.auth.relying-party.id must be a bare domain" }
            require(origins.isNotEmpty()) { "app.auth.relying-party.origins must not be empty" }
            origins.forEach { origin ->
                val host = URI(origin).host
                require(host == id || host.endsWith(".$id")) { "Origin $origin is outside relying party $id" }
            }
        }
    }

    data class Session(
        val cookieName: String,
        val secureCookie: Boolean,
        val idleTimeout: Duration,
        val absoluteTimeout: Duration,
    ) {
        init {
            require(!cookieName.startsWith("__Host-") || secureCookie) { "__Host- cookies require secure-cookie=true" }
            require(idleTimeout.isPositive && absoluteTimeout >= idleTimeout) {
                "Session timeouts must be positive and absolute-timeout >= idle-timeout"
            }
        }
    }

    private companion object {
        const val MIN_SETUP_TOKEN_LENGTH = 32
    }
}

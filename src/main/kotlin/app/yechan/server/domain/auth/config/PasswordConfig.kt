package app.yechan.server.domain.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration(proxyBeanMethods = false)
class PasswordConfig {
    // Prefixed hashes ({argon2}...) let the algorithm change later without breaking stored passwords.
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        DelegatingPasswordEncoder(ARGON2, mapOf(ARGON2 to Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()))

    private companion object {
        const val ARGON2 = "argon2"
    }
}

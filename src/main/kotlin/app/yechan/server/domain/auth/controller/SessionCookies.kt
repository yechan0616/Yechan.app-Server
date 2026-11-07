package app.yechan.server.domain.auth.controller

import app.yechan.server.domain.auth.config.AuthProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SessionCookies(
    private val properties: AuthProperties,
) {
    fun issue(token: String): String = build(token, properties.session.absoluteTimeout)

    fun clear(): String = build("", Duration.ZERO)

    private fun build(
        value: String,
        maxAge: Duration,
    ): String =
        ResponseCookie
            .from(properties.session.cookieName, value)
            .httpOnly(true)
            .secure(properties.session.secureCookie)
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString()
}

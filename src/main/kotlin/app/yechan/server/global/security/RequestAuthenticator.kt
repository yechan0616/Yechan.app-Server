package app.yechan.server.global.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.Authentication

fun interface RequestAuthenticator {
    fun authenticate(request: HttpServletRequest): Authentication?
}

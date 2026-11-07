package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.config.AuthProperties
import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.global.security.RequestAuthenticator
import app.yechan.server.global.security.SecurityConfig
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

class StudioAuthentication(
    private val administrator: AdministratorPrincipal,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_${SecurityConfig.ADMINISTRATOR_ROLE}"))) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): AdministratorPrincipal = administrator
}

@Component
class StudioSessionAuthenticator(
    private val sessions: StudioSessions,
    private val properties: AuthProperties,
) : RequestAuthenticator {
    override fun authenticate(request: HttpServletRequest): Authentication? {
        val token = request.cookies?.firstOrNull { it.name == properties.session.cookieName }?.value ?: return null
        return sessions.authenticate(token)?.let(::StudioAuthentication)
    }
}

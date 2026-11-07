package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.AdministratorResponse
import app.yechan.server.domain.auth.dto.ChangePasswordRequest
import app.yechan.server.domain.auth.dto.UpdateAdministratorRequest
import app.yechan.server.domain.auth.entity.Administrator
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.AdministratorRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AdministratorAccount(
    private val administrators: AdministratorRepository,
    private val encoder: PasswordEncoder,
    private val policy: PasswordPolicy,
    private val sessions: StudioSessions,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(principal: AdministratorPrincipal): AdministratorResponse = require(principal).toResponse()

    @Transactional
    fun update(
        principal: AdministratorPrincipal,
        request: UpdateAdministratorRequest,
    ): AdministratorResponse {
        val administrator = require(principal)
        request.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(administrator::rename)
        request.email?.let(policy::normalizeEmail)?.let { email ->
            if (administrators.existsByEmailAndIdNot(
                    email,
                    administrator.id,
                )
            ) {
                throw BusinessException(AuthErrorCode.AUTH_EMAIL_TAKEN)
            }
            administrator.changeEmail(email)
        }
        return administrators.saveAndFlush(administrator).toResponse()
    }

    @Transactional
    fun changePassword(
        principal: AdministratorPrincipal,
        request: ChangePasswordRequest,
    ): AdministratorResponse {
        val administrator = require(principal)
        if (administrator.email == null) throw BusinessException(AuthErrorCode.AUTH_EMAIL_REQUIRED)
        administrator.passwordHash?.let { current ->
            if (request.currentPassword == null || !encoder.matches(request.currentPassword, current)) {
                throw BusinessException(AuthErrorCode.AUTH_CURRENT_PASSWORD_INCORRECT)
            }
        }
        val violations =
            policy.check(
                request.newPassword,
                administrator.email,
                administrator.username,
                field = "newPassword",
            )
        if (violations.isNotEmpty()) throw BusinessException(AuthErrorCode.AUTH_PASSWORD_WEAK, violations = violations)
        administrator.changePassword(checkNotNull(encoder.encode(request.newPassword)), clock.instant())
        sessions.revokeOthers(principal)
        return administrator.toResponse()
    }

    private fun require(principal: AdministratorPrincipal): Administrator =
        administrators.findById(principal.id).orElseThrow()
}

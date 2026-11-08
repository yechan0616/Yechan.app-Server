package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.AdministratorResponse
import app.yechan.server.domain.auth.dto.ChangePasswordRequest
import app.yechan.server.domain.auth.dto.ClientContext
import app.yechan.server.domain.auth.dto.DisablePasswordRequest
import app.yechan.server.domain.auth.dto.UpdateAdministratorRequest
import app.yechan.server.domain.auth.entity.Administrator
import app.yechan.server.domain.auth.entity.SecurityEventType
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.AdministratorRepository
import app.yechan.server.domain.auth.repository.PasskeyRepository
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
    private val passkeys: PasskeyRepository,
    private val sessions: StudioSessions,
    private val events: SecurityEvents,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(principal: AdministratorPrincipal): AdministratorResponse = require(principal).toResponse()

    @Transactional
    fun update(
        principal: AdministratorPrincipal,
        request: UpdateAdministratorRequest,
        client: ClientContext,
    ): AdministratorResponse {
        val administrator = require(principal)
        request.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(administrator::rename)
        request.email?.let(policy::requireEmail)?.let { email ->
            if (administrators.existsByEmailAndIdNot(
                    email,
                    administrator.id,
                )
            ) {
                throw BusinessException(AuthErrorCode.AUTH_EMAIL_TAKEN)
            }
            if (email != administrator.email) {
                administrator.changeEmail(email)
                events.record(SecurityEventType.EMAIL_CHANGED, administrator.id, client)
            }
        }
        return administrators.saveAndFlush(administrator).toResponse()
    }

    @Transactional
    fun changePassword(
        principal: AdministratorPrincipal,
        request: ChangePasswordRequest,
        client: ClientContext,
    ): AdministratorResponse {
        val administrator = require(principal)
        if (administrator.email == null) throw BusinessException(AuthErrorCode.AUTH_EMAIL_REQUIRED)
        administrator.passwordHash?.let { verifyCurrent(it, request.currentPassword) }
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
        events.record(SecurityEventType.PASSWORD_CHANGED, administrator.id, client)
        return administrator.toResponse()
    }

    @Transactional
    fun disablePassword(
        principal: AdministratorPrincipal,
        request: DisablePasswordRequest,
        client: ClientContext,
    ): AdministratorResponse {
        val administrator = require(principal)
        val current = administrator.passwordHash ?: return administrator.toResponse()
        verifyCurrent(current, request.currentPassword)
        if (passkeys.countByAdministratorId(administrator.id) ==
            0L
        ) {
            throw BusinessException(AuthErrorCode.AUTH_LAST_CREDENTIAL)
        }
        administrator.removePassword()
        events.record(SecurityEventType.PASSWORD_DISABLED, administrator.id, client)
        return administrator.toResponse()
    }

    private fun verifyCurrent(
        hash: String,
        candidate: String?,
    ) {
        if (candidate == null || !encoder.matches(candidate, hash)) {
            throw BusinessException(AuthErrorCode.AUTH_CURRENT_PASSWORD_INCORRECT)
        }
    }

    private fun require(principal: AdministratorPrincipal): Administrator =
        administrators.findById(principal.id).orElseThrow()
}

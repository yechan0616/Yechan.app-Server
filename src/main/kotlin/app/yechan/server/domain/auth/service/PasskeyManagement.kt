package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.dto.CeremonyCompletionRequest
import app.yechan.server.domain.auth.dto.EnrollmentOptionsRequest
import app.yechan.server.domain.auth.dto.PasskeyCreationOptionsResponse
import app.yechan.server.domain.auth.dto.PasskeyResponse
import app.yechan.server.domain.auth.entity.CeremonyPurpose
import app.yechan.server.domain.auth.entity.Passkey
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.AdministratorRepository
import app.yechan.server.domain.auth.repository.PasskeyRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PasskeyManagement(
    private val administrators: AdministratorRepository,
    private val passkeys: PasskeyRepository,
    private val ceremonies: PasskeyCeremonies,
    private val relyingParty: PasskeyRelyingParty,
    private val registrar: PasskeyRegistrar,
) {
    @Transactional(readOnly = true)
    fun list(principal: AdministratorPrincipal): List<PasskeyResponse> =
        passkeys.findAllByAdministratorIdOrderByCreatedAtAsc(principal.id).map { it.toResponse() }

    @Transactional
    fun beginEnrollment(
        principal: AdministratorPrincipal,
        request: EnrollmentOptionsRequest,
    ): PasskeyCreationOptionsResponse {
        val administrator = administrators.findById(principal.id).orElseThrow()
        val challenge = relyingParty.newChallenge()
        val ceremony =
            ceremonies.open(
                purpose = CeremonyPurpose.ENROLLMENT,
                challenge = challenge,
                administratorId = administrator.id,
                userHandle = administrator.userHandle,
                label = request.label?.trim()?.ifEmpty { null },
            )
        val existing = passkeys.findAllByAdministratorIdOrderByCreatedAtAsc(administrator.id)
        return PasskeyCreationOptionsResponse(
            ceremonyId = ceremony.id,
            publicKey =
                relyingParty.creationOptions(
                    challenge,
                    administrator.userHandle,
                    administrator.username,
                    administrator.displayName,
                    existing,
                ),
        )
    }

    @Transactional
    fun completeEnrollment(
        principal: AdministratorPrincipal,
        request: CeremonyCompletionRequest,
        userAgent: String?,
    ): PasskeyResponse {
        val ceremony = ceremonies.consume(request.ceremonyId, CeremonyPurpose.ENROLLMENT)
        if (ceremony.administratorId != principal.id) throw BusinessException(AuthErrorCode.AUTH_CEREMONY_INVALID)
        val administrator = administrators.findById(principal.id).orElseThrow()
        return registrar.register(administrator, ceremony, request.credential, userAgent).toResponse()
    }

    @Transactional
    fun rename(
        principal: AdministratorPrincipal,
        passkeyId: UUID,
        label: String,
    ): PasskeyResponse = find(principal, passkeyId).apply { rename(label.trim()) }.toResponse()

    @Transactional
    fun remove(
        principal: AdministratorPrincipal,
        passkeyId: UUID,
    ) {
        val passkey = find(principal, passkeyId)
        if (passkeys.countByAdministratorId(principal.id) <= 1) throw BusinessException(AuthErrorCode.AUTH_LAST_PASSKEY)
        passkeys.delete(passkey)
    }

    private fun find(
        principal: AdministratorPrincipal,
        passkeyId: UUID,
    ): Passkey =
        passkeys.findByIdAndAdministratorId(passkeyId, principal.id)
            ?: throw BusinessException(AuthErrorCode.AUTH_PASSKEY_NOT_FOUND)

    private fun Passkey.toResponse() =
        PasskeyResponse(
            id = id,
            label = label,
            synced = backupEligible,
            transports = transports(),
            createdAt = checkNotNull(createdAt),
            lastUsedAt = lastUsedAt,
        )
}

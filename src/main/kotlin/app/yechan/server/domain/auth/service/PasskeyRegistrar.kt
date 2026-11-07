package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.entity.Administrator
import app.yechan.server.domain.auth.entity.Passkey
import app.yechan.server.domain.auth.entity.PasskeyCeremony
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.PasskeyRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode

@Component
@Transactional(propagation = Propagation.MANDATORY)
class PasskeyRegistrar(
    private val passkeys: PasskeyRepository,
    private val relyingParty: PasskeyRelyingParty,
) {
    fun register(
        administrator: Administrator,
        ceremony: PasskeyCeremony,
        credential: JsonNode,
        userAgent: String?,
    ): Passkey {
        val registration = relyingParty.verifyRegistration(credentialJson(credential), ceremony.challenge)
        if (passkeys.existsByCredentialId(registration.credentialId)) {
            throw BusinessException(AuthErrorCode.AUTH_PASSKEY_ALREADY_REGISTERED)
        }
        return passkeys.save(
            Passkey(
                administrator = administrator,
                credentialId = registration.credentialId,
                attestedCredentialData = registration.attestedCredentialData,
                attestationFormat = registration.attestationFormat,
                backupEligible = registration.backupEligible,
                transports = registration.transports,
                signCount = registration.signCount,
                userVerified = registration.userVerified,
                backedUp = registration.backedUp,
                label = ceremony.label ?: defaultLabel(userAgent),
            ),
        )
    }

    companion object {
        private const val MAX_CREDENTIAL_JSON = 16 * 1024

        fun credentialJson(credential: JsonNode): String {
            if (!credential.isObject) throw BusinessException(AuthErrorCode.AUTH_PASSKEY_REJECTED)
            return credential.toString().also {
                if (it.length > MAX_CREDENTIAL_JSON) throw BusinessException(AuthErrorCode.AUTH_PASSKEY_REJECTED)
            }
        }

        private fun defaultLabel(userAgent: String?): String =
            when {
                userAgent == null -> "Passkey"
                userAgent.contains("iPhone") -> "iPhone"
                userAgent.contains("iPad") -> "iPad"
                userAgent.contains("Mac OS X") -> "Mac"
                userAgent.contains("Android") -> "Android"
                userAgent.contains("Windows") -> "Windows"
                else -> "Passkey"
            }
    }
}

package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.dto.CeremonyCompletionRequest
import app.yechan.server.domain.auth.dto.ClientContext
import app.yechan.server.domain.auth.dto.PasskeyRequestOptionsResponse
import app.yechan.server.domain.auth.entity.CeremonyPurpose
import app.yechan.server.domain.auth.entity.SecurityEventType
import app.yechan.server.domain.auth.entity.SignInMethod
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.PasskeyRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class SignIn(
    private val passkeys: PasskeyRepository,
    private val ceremonies: PasskeyCeremonies,
    private val relyingParty: PasskeyRelyingParty,
    private val sessions: StudioSessions,
    private val events: SecurityEvents,
    private val clock: Clock,
) {
    @Transactional
    fun begin(): PasskeyRequestOptionsResponse {
        val challenge = relyingParty.newChallenge()
        val ceremony = ceremonies.open(CeremonyPurpose.SIGN_IN, challenge)
        return PasskeyRequestOptionsResponse(ceremony.id, relyingParty.requestOptions(challenge))
    }

    @Transactional
    fun complete(
        request: CeremonyCompletionRequest,
        client: ClientContext,
    ): IssuedSession {
        val ceremony = ceremonies.consume(request.ceremonyId, CeremonyPurpose.SIGN_IN)
        var administratorId: UUID? = null
        try {
            val assertion = relyingParty.parseAssertion(PasskeyRegistrar.credentialJson(request.credential))
            val credentialId = assertion.credentialId ?: throw failed()
            val passkey = passkeys.findByCredentialId(credentialId) ?: throw failed()
            administratorId = passkey.administrator.id
            val verified = relyingParty.verifyAssertion(assertion, ceremony.challenge, passkey)
            passkey.recordSignIn(verified.signCount, verified.userVerified, verified.backedUp, clock.instant())
            val issued = sessions.issue(passkey.administrator, client)
            events.record(SecurityEventType.SIGNED_IN, administratorId, client, SignInMethod.PASSKEY)
            return issued
        } catch (ex: BusinessException) {
            events.record(SecurityEventType.SIGN_IN_FAILED, administratorId, client, SignInMethod.PASSKEY)
            // Do not reveal which step failed.
            if (ex.errorCode == AuthErrorCode.AUTH_SIGN_IN_FAILED) throw ex
            throw BusinessException(AuthErrorCode.AUTH_SIGN_IN_FAILED, cause = ex)
        }
    }

    private fun failed() = BusinessException(AuthErrorCode.AUTH_SIGN_IN_FAILED)
}

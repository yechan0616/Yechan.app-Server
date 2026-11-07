package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.config.AuthProperties
import app.yechan.server.domain.auth.dto.CeremonyCompletionRequest
import app.yechan.server.domain.auth.dto.OnboardingOptionsRequest
import app.yechan.server.domain.auth.dto.OnboardingStatusResponse
import app.yechan.server.domain.auth.dto.PasskeyCreationOptionsResponse
import app.yechan.server.domain.auth.entity.Administrator
import app.yechan.server.domain.auth.entity.CeremonyPurpose
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.AdministratorRepository
import app.yechan.server.domain.auth.repository.PasskeyRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
class Onboarding(
    private val properties: AuthProperties,
    private val administrators: AdministratorRepository,
    private val passkeys: PasskeyRepository,
    private val ceremonies: PasskeyCeremonies,
    private val relyingParty: PasskeyRelyingParty,
    private val registrar: PasskeyRegistrar,
    private val sessions: StudioSessions,
) {
    @Transactional(readOnly = true)
    fun status() = OnboardingStatusResponse(available = isOpen())

    @Transactional
    fun begin(
        setupToken: String?,
        request: OnboardingOptionsRequest,
    ): PasskeyCreationOptionsResponse {
        authorize(setupToken)
        val existing = administrators.findByUsername(request.username)
        val userHandle = existing?.userHandle ?: relyingParty.newUserHandle()
        val displayName = existing?.displayName ?: request.displayName.trim()
        val challenge = relyingParty.newChallenge()
        val ceremony =
            ceremonies.open(
                purpose = CeremonyPurpose.ONBOARDING,
                challenge = challenge,
                userHandle = userHandle,
                username = request.username,
                displayName = displayName,
                label = request.label?.trim()?.ifEmpty { null },
            )
        return PasskeyCreationOptionsResponse(
            ceremonyId = ceremony.id,
            publicKey = relyingParty.creationOptions(challenge, userHandle, request.username, displayName, emptyList()),
        )
    }

    @Transactional
    fun complete(
        setupToken: String?,
        request: CeremonyCompletionRequest,
        userAgent: String?,
    ): IssuedSession {
        authorize(setupToken)
        val ceremony = ceremonies.consume(request.ceremonyId, CeremonyPurpose.ONBOARDING)
        val username = checkNotNull(ceremony.username)
        val administrator =
            administrators.findByUsername(username)
                ?: administrators.save(
                    Administrator(username, checkNotNull(ceremony.displayName), checkNotNull(ceremony.userHandle)),
                )
        registrar.register(administrator, ceremony, request.credential, userAgent)
        return sessions.issue(administrator, userAgent)
    }

    private fun authorize(setupToken: String?) {
        val expected = properties.setupToken
        if (expected.isNullOrEmpty()) throw BusinessException(AuthErrorCode.AUTH_ONBOARDING_UNAVAILABLE)
        if (setupToken == null || !MessageDigest.isEqual(digest(setupToken), digest(expected))) {
            throw BusinessException(AuthErrorCode.AUTH_SETUP_TOKEN_INVALID)
        }
        if (!isOpen()) throw BusinessException(AuthErrorCode.AUTH_ONBOARDING_UNAVAILABLE)
    }

    private fun isOpen() = properties.onboardingEnabled && passkeys.count() == 0L

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
}

package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.config.AuthProperties
import app.yechan.server.domain.auth.dto.CeremonyCompletionRequest
import app.yechan.server.domain.auth.dto.ClientContext
import app.yechan.server.domain.auth.dto.OnboardingOptionsRequest
import app.yechan.server.domain.auth.dto.OnboardingStatusResponse
import app.yechan.server.domain.auth.dto.PasskeyCreationOptionsResponse
import app.yechan.server.domain.auth.dto.PasswordOnboardingRequest
import app.yechan.server.domain.auth.entity.Administrator
import app.yechan.server.domain.auth.entity.CeremonyPurpose
import app.yechan.server.domain.auth.entity.SecurityEventType
import app.yechan.server.domain.auth.entity.SignInMethod
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.domain.auth.repository.AdministratorRepository
import app.yechan.server.domain.auth.repository.PasskeyRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock

@Service
class Onboarding(
    private val properties: AuthProperties,
    private val administrators: AdministratorRepository,
    private val passkeys: PasskeyRepository,
    private val ceremonies: PasskeyCeremonies,
    private val relyingParty: PasskeyRelyingParty,
    private val registrar: PasskeyRegistrar,
    private val sessions: StudioSessions,
    private val events: SecurityEvents,
    private val encoder: PasswordEncoder,
    private val policy: PasswordPolicy,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun status() = OnboardingStatusResponse(available = isOpen())

    @Transactional
    fun begin(
        setupToken: String?,
        request: OnboardingOptionsRequest,
        client: ClientContext,
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
                client = client,
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
        client: ClientContext,
    ): IssuedSession {
        authorize(setupToken)
        val ceremony = ceremonies.consume(request.ceremonyId, CeremonyPurpose.ONBOARDING)
        val username = checkNotNull(ceremony.username)
        val administrator =
            administrators.findByUsername(username)
                ?: administrators.save(
                    Administrator(username, checkNotNull(ceremony.displayName), checkNotNull(ceremony.userHandle)),
                )
        registrar.register(administrator, ceremony, request.credential, client)
        events.record(SecurityEventType.ONBOARDED, administrator.id, client, SignInMethod.PASSKEY)
        return sessions.issue(administrator, client)
    }

    @Transactional
    fun completeWithPassword(
        setupToken: String?,
        request: PasswordOnboardingRequest,
        client: ClientContext,
    ): IssuedSession {
        authorize(setupToken)
        val email = policy.requireEmail(request.email)
        val violations = policy.check(request.password, email, request.username)
        if (violations.isNotEmpty()) throw BusinessException(AuthErrorCode.AUTH_PASSWORD_WEAK, violations = violations)
        val administrator =
            administrators.findByUsername(request.username)
                ?: Administrator(request.username, request.displayName.trim(), relyingParty.newUserHandle())
        if (administrators.findByEmail(email)?.let { it.username != request.username } == true) {
            throw BusinessException(AuthErrorCode.AUTH_EMAIL_TAKEN)
        }
        administrator.changeEmail(email)
        administrator.changePassword(checkNotNull(encoder.encode(request.password)), clock.instant())
        val saved = administrators.save(administrator)
        events.record(SecurityEventType.ONBOARDED, saved.id, client, SignInMethod.PASSWORD)
        return sessions.issue(saved, client)
    }

    private fun authorize(setupToken: String?) {
        val expected = properties.setupToken
        if (expected.isNullOrEmpty()) throw BusinessException(AuthErrorCode.AUTH_ONBOARDING_UNAVAILABLE)
        if (setupToken == null || !MessageDigest.isEqual(digest(setupToken), digest(expected))) {
            throw BusinessException(AuthErrorCode.AUTH_SETUP_TOKEN_INVALID)
        }
        if (!isOpen()) throw BusinessException(AuthErrorCode.AUTH_ONBOARDING_UNAVAILABLE)
    }

    private fun isOpen() =
        properties.onboardingEnabled && passkeys.count() == 0L && administrators.countByPasswordHashIsNotNull() == 0L

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
}

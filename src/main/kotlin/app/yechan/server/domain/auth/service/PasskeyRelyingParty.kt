package app.yechan.server.domain.auth.service

import app.yechan.server.domain.auth.config.AuthProperties
import app.yechan.server.domain.auth.dto.AuthenticatorSelection
import app.yechan.server.domain.auth.dto.CreationOptions
import app.yechan.server.domain.auth.dto.CredentialDescriptor
import app.yechan.server.domain.auth.dto.CredentialParameter
import app.yechan.server.domain.auth.dto.RelyingPartyEntity
import app.yechan.server.domain.auth.dto.RequestOptions
import app.yechan.server.domain.auth.dto.UserEntity
import app.yechan.server.domain.auth.entity.Passkey
import app.yechan.server.domain.auth.exception.AuthErrorCode
import app.yechan.server.global.error.BusinessException
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.verifier.exception.VerificationException
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class PasskeyRelyingParty(
    private val properties: AuthProperties,
) {
    private val objectConverter = ObjectConverter()
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
    private val credentialDataConverter = AttestedCredentialDataConverter(objectConverter)
    private val random = SecureRandom()
    private val origins =
        properties.relyingParty.origins
            .map(Origin::create)
            .toSet()

    fun newChallenge(): ByteArray = randomBytes(CHALLENGE_BYTES)

    fun newUserHandle(): ByteArray = randomBytes(USER_HANDLE_BYTES)

    fun creationOptions(
        challenge: ByteArray,
        userHandle: ByteArray,
        username: String,
        displayName: String,
        existing: List<Passkey>,
    ) = CreationOptions(
        challenge = encode(challenge),
        rp = RelyingPartyEntity(properties.relyingParty.id, properties.relyingParty.name),
        user = UserEntity(encode(userHandle), username, displayName),
        pubKeyCredParams = SUPPORTED_ALGORITHMS.map { CredentialParameter(it.value) },
        timeout = properties.ceremonyTtl.toMillis(),
        attestation = "none",
        authenticatorSelection =
            AuthenticatorSelection(residentKey = "required", requireResidentKey = true, userVerification = "required"),
        excludeCredentials = existing.map { CredentialDescriptor(encode(it.credentialId), it.transports().sorted()) },
        hints = listOf("client-device"),
    )

    fun requestOptions(challenge: ByteArray) =
        RequestOptions(
            challenge = encode(challenge),
            rpId = properties.relyingParty.id,
            timeout = properties.ceremonyTtl.toMillis(),
            userVerification = "required",
            allowCredentials = emptyList(),
            hints = listOf("client-device"),
        )

    fun verifyRegistration(
        credentialJson: String,
        challenge: ByteArray,
    ): VerifiedRegistration =
        rejectOnFailure {
            val parameters =
                RegistrationParameters(
                    serverProperty(challenge),
                    SUPPORTED_ALGORITHMS.map { PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, it) },
                    true,
                    true,
                )
            val registration = webAuthnManager.verifyRegistrationResponseJSON(credentialJson, parameters)
            val attestation = checkNotNull(registration.attestationObject)
            val authenticatorData = attestation.authenticatorData
            val credentialData = checkNotNull(authenticatorData.attestedCredentialData)
            VerifiedRegistration(
                credentialId = credentialData.credentialId,
                attestedCredentialData = credentialDataConverter.convert(credentialData),
                attestationFormat = attestation.format,
                signCount = authenticatorData.signCount,
                userVerified = authenticatorData.isFlagUV,
                backupEligible = authenticatorData.isFlagBE,
                backedUp = authenticatorData.isFlagBS,
                transports =
                    registration.transports
                        .orEmpty()
                        .map { it.value }
                        .toSet(),
            )
        }

    fun parseAssertion(credentialJson: String): AuthenticationData =
        rejectOnFailure { webAuthnManager.parseAuthenticationResponseJSON(credentialJson) }

    fun verifyAssertion(
        assertion: AuthenticationData,
        challenge: ByteArray,
        passkey: Passkey,
    ): VerifiedAssertion =
        rejectOnFailure {
            val userHandle = checkNotNull(assertion.userHandle) { "User handle is missing" }
            check(userHandle.contentEquals(passkey.administrator.userHandle)) { "User handle does not match" }
            val record =
                CredentialRecordImpl(
                    NoneAttestationStatement(),
                    passkey.userVerified,
                    passkey.backupEligible,
                    passkey.backedUp,
                    passkey.signCount,
                    credentialDataConverter.convert(passkey.attestedCredentialData),
                    null,
                    null,
                    null,
                    null,
                )
            val verified =
                webAuthnManager.verify(
                    assertion,
                    AuthenticationParameters(serverProperty(challenge), record, null, true, true),
                )
            val authenticatorData = checkNotNull(verified.authenticatorData)
            VerifiedAssertion(
                signCount = authenticatorData.signCount,
                userVerified = authenticatorData.isFlagUV,
                backedUp = authenticatorData.isFlagBS,
            )
        }

    private fun serverProperty(challenge: ByteArray): ServerProperty =
        ServerProperty
            .builder()
            .origins(origins)
            .rpId(properties.relyingParty.id)
            .challenge(DefaultChallenge(challenge))
            .build()

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private inline fun <T> rejectOnFailure(block: () -> T): T =
        try {
            block()
        } catch (ex: DataConversionException) {
            throw BusinessException(AuthErrorCode.AUTH_PASSKEY_REJECTED, cause = ex)
        } catch (ex: VerificationException) {
            throw BusinessException(AuthErrorCode.AUTH_PASSKEY_REJECTED, cause = ex)
        } catch (ex: IllegalArgumentException) {
            throw BusinessException(AuthErrorCode.AUTH_PASSKEY_REJECTED, cause = ex)
        } catch (ex: IllegalStateException) {
            throw BusinessException(AuthErrorCode.AUTH_PASSKEY_REJECTED, cause = ex)
        }

    companion object {
        private const val CHALLENGE_BYTES = 32
        private const val USER_HANDLE_BYTES = 32
        private val SUPPORTED_ALGORITHMS =
            listOf(COSEAlgorithmIdentifier.ES256, COSEAlgorithmIdentifier.EdDSA, COSEAlgorithmIdentifier.RS256)

        fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

class VerifiedRegistration(
    val credentialId: ByteArray,
    val attestedCredentialData: ByteArray,
    val attestationFormat: String,
    val signCount: Long,
    val userVerified: Boolean,
    val backupEligible: Boolean,
    val backedUp: Boolean,
    val transports: Set<String>,
)

class VerifiedAssertion(
    val signCount: Long,
    val userVerified: Boolean,
    val backedUp: Boolean,
)

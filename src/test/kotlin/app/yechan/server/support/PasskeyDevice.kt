package app.yechan.server.support

import com.webauthn4j.data.AttestationConveyancePreference
import com.webauthn4j.data.AuthenticatorSelectionCriteria
import com.webauthn4j.data.PublicKeyCredentialCreationOptions
import com.webauthn4j.data.PublicKeyCredentialDescriptor
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialRequestOptions
import com.webauthn4j.data.PublicKeyCredentialRpEntity
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.PublicKeyCredentialUserEntity
import com.webauthn4j.data.ResidentKeyRequirement
import com.webauthn4j.data.UserVerificationRequirement
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.test.authenticator.webauthn.PackedAuthenticator
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor
import com.webauthn4j.test.client.ClientPlatform
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.Base64

class PasskeyDevice(
    private val json: JsonMapper,
    origin: String = "http://localhost:3001",
) {
    private val platform = ClientPlatform(Origin.create(origin), WebAuthnAuthenticatorAdaptor(PackedAuthenticator()))
    private val credentialIds = mutableListOf<ByteArray>()

    fun create(publicKey: JsonNode): JsonNode {
        val options =
            PublicKeyCredentialCreationOptions(
                PublicKeyCredentialRpEntity(publicKey["rp"]["id"].stringValue(), publicKey["rp"]["name"].stringValue()),
                PublicKeyCredentialUserEntity(
                    decode(publicKey["user"]["id"].stringValue()),
                    publicKey["user"]["name"].stringValue(),
                    publicKey["user"]["displayName"].stringValue(),
                ),
                DefaultChallenge(decode(publicKey["challenge"].stringValue())),
                listOf(
                    PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
                ),
                publicKey["timeout"].longValue(),
                emptyList(),
                AuthenticatorSelectionCriteria(
                    null,
                    true,
                    ResidentKeyRequirement.REQUIRED,
                    UserVerificationRequirement.REQUIRED,
                ),
                AttestationConveyancePreference.NONE,
                null,
            )
        val credential = platform.create(options)
        credentialIds += credential.rawId
        val response = checkNotNull(credential.response)
        return json.valueToTree(
            mapOf(
                "id" to encode(credential.rawId),
                "rawId" to encode(credential.rawId),
                "type" to "public-key",
                "authenticatorAttachment" to "platform",
                "clientExtensionResults" to emptyMap<String, Any>(),
                "response" to
                    mapOf(
                        "clientDataJSON" to encode(response.clientDataJSON),
                        "attestationObject" to encode(response.attestationObject),
                        "transports" to listOf("internal"),
                    ),
            ),
        )
    }

    fun get(publicKey: JsonNode): JsonNode {
        // The emulator cannot discover credentials, so pass this device's ids.
        val options =
            PublicKeyCredentialRequestOptions(
                DefaultChallenge(decode(publicKey["challenge"].stringValue())),
                publicKey["timeout"].longValue(),
                publicKey["rpId"].stringValue(),
                credentialIds.map { PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY, it, null) },
                UserVerificationRequirement.REQUIRED,
                null,
            )
        val credential = platform.get(options)
        val response = checkNotNull(credential.response)
        return json.valueToTree(
            mapOf(
                "id" to encode(credential.rawId),
                "rawId" to encode(credential.rawId),
                "type" to "public-key",
                "authenticatorAttachment" to "platform",
                "clientExtensionResults" to emptyMap<String, Any>(),
                "response" to
                    mapOf(
                        "clientDataJSON" to encode(response.clientDataJSON),
                        "authenticatorData" to encode(response.authenticatorData),
                        "signature" to encode(response.signature),
                        "userHandle" to response.userHandle?.let(::encode),
                    ),
            ),
        )
    }

    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)
}

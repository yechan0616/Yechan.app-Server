package app.yechan.server.domain.auth.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

data class PasskeyCreationOptionsResponse(
    val ceremonyId: UUID,
    val publicKey: CreationOptions,
)

data class PasskeyRequestOptionsResponse(
    val ceremonyId: UUID,
    val publicKey: RequestOptions,
)

data class CreationOptions(
    val challenge: String,
    val rp: RelyingPartyEntity,
    val user: UserEntity,
    val pubKeyCredParams: List<CredentialParameter>,
    val timeout: Long,
    val attestation: String,
    val authenticatorSelection: AuthenticatorSelection,
    val excludeCredentials: List<CredentialDescriptor>,
    val hints: List<String>,
)

data class RequestOptions(
    val challenge: String,
    val rpId: String,
    val timeout: Long,
    val userVerification: String,
    val allowCredentials: List<CredentialDescriptor>,
    val hints: List<String>,
)

data class RelyingPartyEntity(
    val id: String,
    val name: String,
)

data class UserEntity(
    val id: String,
    val name: String,
    val displayName: String,
)

data class CredentialParameter(
    val alg: Long,
    val type: String = "public-key",
)

data class AuthenticatorSelection(
    val residentKey: String,
    val requireResidentKey: Boolean,
    val userVerification: String,
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class CredentialDescriptor(
    val id: String,
    val transports: List<String>,
    val type: String = "public-key",
)

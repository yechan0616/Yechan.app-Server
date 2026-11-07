package app.yechan.server.domain.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import tools.jackson.databind.JsonNode
import java.util.UUID

data class OnboardingOptionsRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^[a-z0-9][a-z0-9._-]{2,31}$",
        message = "must be 3-32 lowercase letters, digits, '.', '_' or '-'",
    )
    val username: String,
    @field:NotBlank @field:Size(max = 100)
    val displayName: String,
    @field:Size(max = 100)
    val label: String? = null,
)

data class EnrollmentOptionsRequest(
    @field:Size(max = 100)
    val label: String? = null,
)

data class CeremonyCompletionRequest(
    @field:NotNull
    val ceremonyId: UUID,
    @field:NotNull
    val credential: JsonNode,
)

data class RenamePasskeyRequest(
    @field:NotBlank @field:Size(max = 100)
    val label: String,
)

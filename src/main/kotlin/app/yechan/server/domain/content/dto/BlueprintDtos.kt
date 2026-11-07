package app.yechan.server.domain.content.dto

import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.entity.FieldDefinition
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateBlueprintRequest(
    @field:NotBlank @field:Size(max = 64)
    val key: String,
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    @field:NotNull
    val kind: BlueprintKind,
    @field:NotNull @field:Size(max = 200)
    val fields: List<FieldDefinition>,
)

data class UpdateBlueprintRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    @field:NotNull
    val kind: BlueprintKind,
    @field:NotNull @field:Size(max = 200)
    val fields: List<FieldDefinition>,
    @field:NotNull
    val revision: Long,
)

data class BlueprintResponse(
    val key: String,
    val name: String,
    val description: String?,
    val kind: BlueprintKind,
    val fields: List<FieldDefinition>,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

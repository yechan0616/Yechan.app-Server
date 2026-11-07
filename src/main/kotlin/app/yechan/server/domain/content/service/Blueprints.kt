package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.BlueprintResponse
import app.yechan.server.domain.content.dto.CreateBlueprintRequest
import app.yechan.server.domain.content.dto.UpdateBlueprintRequest
import app.yechan.server.domain.content.entity.Blueprint
import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.repository.BlueprintRepository
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class Blueprints(
    private val blueprints: BlueprintRepository,
    private val entries: EntryRepository,
    private val schemaValidator: BlueprintSchemaValidator,
) {
    @Transactional(readOnly = true)
    fun list(): List<BlueprintResponse> = blueprints.findAllByOrderByNameAsc().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun get(key: String): BlueprintResponse = require(key).toResponse()

    @Transactional
    fun create(request: CreateBlueprintRequest): BlueprintResponse {
        if (blueprints.existsByKey(request.key)) throw BusinessException(ContentErrorCode.BLUEPRINT_ALREADY_EXISTS)
        validate(request.key, request.name, request.description, request.fields)
        return blueprints
            .save(
                Blueprint(request.key, request.kind, request.name.trim(), request.description?.trim(), request.fields),
            ).toResponse()
    }

    @Transactional
    fun update(
        key: String,
        request: UpdateBlueprintRequest,
    ): BlueprintResponse {
        val blueprint = require(key)
        if (blueprint.revision !=
            request.revision
        ) {
            throw BusinessException(ContentErrorCode.BLUEPRINT_REVISION_CONFLICT)
        }
        if (blueprint.kind != request.kind && entries.countByBlueprintId(blueprint.id) > 0) {
            throw BusinessException(ContentErrorCode.BLUEPRINT_KIND_LOCKED)
        }
        validate(key, request.name, request.description, request.fields)
        blueprint.revise(request.name.trim(), request.description?.trim(), request.kind, request.fields)
        return blueprints.saveAndFlush(blueprint).toResponse()
    }

    @Transactional
    fun delete(key: String) {
        val blueprint = require(key)
        val referencedBy =
            blueprints.findAll().any { other ->
                other.key != key && other.fields.any { key in it.referencedBlueprints() }
            }
        if (entries.countByBlueprintId(blueprint.id) > 0 ||
            referencedBy
        ) {
            throw BusinessException(ContentErrorCode.BLUEPRINT_IN_USE)
        }
        entries.purgeTrashOf(blueprint.id)
        blueprints.delete(blueprint)
    }

    fun require(key: String): Blueprint =
        blueprints.findByKey(key) ?: throw BusinessException(ContentErrorCode.BLUEPRINT_NOT_FOUND)

    @Transactional(propagation = Propagation.MANDATORY)
    fun requireSingletonVacant(blueprint: Blueprint) {
        if (blueprint.kind != BlueprintKind.SINGLETON) return
        blueprints.lockById(blueprint.id)
        if (entries.countByBlueprintId(blueprint.id) >
            0
        ) {
            throw BusinessException(ContentErrorCode.ENTRY_SINGLETON_EXISTS)
        }
    }

    private fun validate(
        key: String,
        name: String,
        description: String?,
        fields: List<FieldDefinition>,
    ) {
        val violations = schemaValidator.validate(key, name, description, fields, blueprints.findAllKeys())
        if (violations.isNotEmpty()) {
            throw BusinessException(
                ContentErrorCode.BLUEPRINT_INVALID,
                violations = violations,
            )
        }
    }

    private fun Blueprint.toResponse() =
        BlueprintResponse(
            key = key,
            name = name,
            description = description,
            kind = kind,
            fields = fields,
            revision = revision,
            createdAt = checkNotNull(createdAt),
            updatedAt = checkNotNull(updatedAt),
        )
}

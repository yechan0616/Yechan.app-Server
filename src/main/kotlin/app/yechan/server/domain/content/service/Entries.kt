package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.CreateEntryRequest
import app.yechan.server.domain.content.dto.EntryResponse
import app.yechan.server.domain.content.dto.PreviewLinkResponse
import app.yechan.server.domain.content.dto.PublishEntryRequest
import app.yechan.server.domain.content.dto.ReorderEntriesRequest
import app.yechan.server.domain.content.dto.UpdateEntryRequest
import app.yechan.server.domain.content.entity.Blueprint
import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.entity.Entry
import app.yechan.server.domain.content.entity.EntryStatus
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.domain.content.repository.StudioQuery
import app.yechan.server.domain.media.service.MediaAssets
import app.yechan.server.global.common.response.PageResponse
import app.yechan.server.global.error.BusinessException
import app.yechan.server.global.error.FieldViolation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.node.ObjectNode
import java.time.Clock
import java.util.UUID

@Service
class Entries(
    private val entries: EntryRepository,
    private val blueprints: Blueprints,
    private val validator: ContentValidator,
    private val media: MediaAssets,
    private val previews: PreviewTokens,
    private val revisions: Revisions,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(
        blueprintKey: String,
        page: Int,
        size: Int,
        search: String? = null,
        status: EntryStatus? = null,
    ): PageResponse<EntryResponse> {
        val blueprint = blueprints.require(blueprintKey)
        return PageResponse.of(
            entries.findForStudio(StudioQuery(blueprint.id, search, status, page, size)),
        ) { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): EntryResponse = require(id).toResponse()

    @Transactional
    fun create(
        blueprintKey: String,
        request: CreateEntryRequest,
    ): EntryResponse {
        val blueprint = blueprints.require(blueprintKey)
        if (blueprint.kind == BlueprintKind.SINGLETON && entries.countByBlueprintId(blueprint.id) > 0) {
            throw BusinessException(ContentErrorCode.ENTRY_SINGLETON_EXISTS)
        }
        val slug = normalizeSlug(request.slug)
        checkDraft(blueprint, slug, request.content)
        if (slug != null &&
            entries.existsByBlueprintIdAndSlug(blueprint.id, slug)
        ) {
            throw BusinessException(ContentErrorCode.ENTRY_SLUG_TAKEN)
        }
        return entries.save(Entry(blueprint, slug, request.position, request.content)).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        request: UpdateEntryRequest,
    ): EntryResponse {
        val entry = require(id)
        checkRevision(entry, request.revision)
        val slug = normalizeSlug(request.slug)
        checkDraft(entry.blueprint, slug, request.content)
        if (slug != null && entries.existsByBlueprintIdAndSlugAndIdNot(entry.blueprint.id, slug, id)) {
            throw BusinessException(ContentErrorCode.ENTRY_SLUG_TAKEN)
        }
        entry.edit(slug, request.position, request.content)
        return entries.saveAndFlush(entry).toResponse()
    }

    @Transactional
    fun publish(
        id: UUID,
        request: PublishEntryRequest,
    ): EntryResponse {
        val entry = require(id)
        checkRevision(entry, request.revision)
        val blueprint = entry.blueprint
        val slugViolations = slugViolations(blueprint, entry.slug)
        val referenceIds = validator.collectReferences(blueprint.fields, entry.draft)
        val references = entries.findReferences(referenceIds).associate { it.id to it.blueprint }
        val assets = media.findAll(validator.collectAssets(blueprint.fields, entry.draft)).keys
        val violations =
            slugViolations +
                validator.validate(blueprint.fields, entry.draft, ContentValidator.Mode.PUBLISH, references, assets)
        if (violations.isNotEmpty()) throw BusinessException(ContentErrorCode.ENTRY_INVALID, violations = violations)
        entry.slug?.let { slug ->
            if (entries.existsByBlueprintIdAndPublishedSlugAndIdNot(blueprint.id, slug, id)) {
                throw BusinessException(ContentErrorCode.ENTRY_SLUG_TAKEN)
            }
        }
        entry.publish(clock.instant())
        revisions.record(entry)
        return entries.saveAndFlush(entry).toResponse()
    }

    @Transactional
    fun unpublish(id: UUID): EntryResponse {
        val entry = require(id)
        entry.unpublish()
        return entries.saveAndFlush(entry).toResponse()
    }

    @Transactional
    fun delete(
        id: UUID,
        force: Boolean = false,
    ) {
        val entry = require(id)
        val referencing = entries.referencing(id, id.toString())
        if (referencing.isNotEmpty() && !force) {
            val violations = referencing.map { FieldViolation("referencedBy", "${it.blueprint}/${it.slug ?: it.id}") }
            throw BusinessException(ContentErrorCode.ENTRY_REFERENCED, violations = violations)
        }
        entry.moveToTrash(clock.instant())
    }

    @Transactional
    fun reorder(
        blueprintKey: String,
        request: ReorderEntriesRequest,
    ) {
        val blueprint = blueprints.require(blueprintKey)
        val ids = request.entries
        if (ids.toSet().size != ids.size || entries.countByBlueprintIdAndIdIn(blueprint.id, ids) != ids.size.toLong()) {
            throw BusinessException(ContentErrorCode.ENTRY_ORDER_INVALID)
        }
        ids.forEachIndexed { position, id -> entries.updatePosition(id, position) }
    }

    @Transactional(readOnly = true)
    fun preview(id: UUID): PreviewLinkResponse =
        previews.issue(require(id).id).let { PreviewLinkResponse(it.token, it.expiresAt) }

    fun require(id: UUID): Entry =
        entries.findWithBlueprint(id) ?: throw BusinessException(ContentErrorCode.ENTRY_NOT_FOUND)

    fun checkRevision(
        entry: Entry,
        revision: Long,
    ) {
        if (entry.revision != revision) throw BusinessException(ContentErrorCode.ENTRY_REVISION_CONFLICT)
    }

    fun checkDraft(
        blueprint: Blueprint,
        slug: String?,
        content: ObjectNode,
    ) {
        val violations =
            slugViolations(blueprint, slug, allowMissing = true) +
                validator.validate(blueprint.fields, content, ContentValidator.Mode.DRAFT)
        if (violations.isNotEmpty()) throw BusinessException(ContentErrorCode.ENTRY_INVALID, violations = violations)
    }

    private fun slugViolations(
        blueprint: Blueprint,
        slug: String?,
        allowMissing: Boolean = false,
    ): List<FieldViolation> =
        when {
            blueprint.kind == BlueprintKind.SINGLETON && slug != null -> {
                listOf(
                    FieldViolation("slug", "is not allowed for a singleton"),
                )
            }

            blueprint.kind == BlueprintKind.SINGLETON -> {
                emptyList()
            }

            slug == null && allowMissing -> {
                emptyList()
            }

            slug == null -> {
                listOf(FieldViolation("slug", "is required"))
            }

            !SLUG.matches(
                slug,
            ) -> {
                listOf(FieldViolation("slug", "must be lowercase letters, digits and single hyphens (max 96)"))
            }

            else -> {
                emptyList()
            }
        }

    private fun normalizeSlug(slug: String?): String? = slug?.trim()?.ifEmpty { null }

    fun describe(entry: Entry): EntryResponse = entry.toResponse()

    private fun Entry.toResponse() =
        EntryResponse(
            id = id,
            blueprint = blueprint.key,
            slug = slug,
            position = position,
            status = status,
            content = draft,
            publishedSlug = publishedSlug,
            publishedAt = publishedAt,
            revision = revision,
            createdAt = checkNotNull(createdAt),
            updatedAt = checkNotNull(updatedAt),
        )

    companion object {
        private val SLUG = Regex("^(?=.{1,96}$)[a-z0-9]+(?:-[a-z0-9]+)*$")
    }
}

package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.LocalesResponse
import app.yechan.server.domain.content.dto.PreviewEntryResponse
import app.yechan.server.domain.content.dto.PublishedEntryResponse
import app.yechan.server.domain.content.entity.Blueprint
import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.entity.Entry
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.repository.BlueprintRepository
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.domain.media.service.MediaAssets
import app.yechan.server.global.common.response.PageResponse
import app.yechan.server.global.error.BusinessException
import app.yechan.server.global.i18n.ContentLocales
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.node.ObjectNode

@Service
@Transactional(readOnly = true)
class PublishedContent(
    private val blueprints: BlueprintRepository,
    private val entries: EntryRepository,
    private val queries: PublishedQueries,
    private val previews: PreviewTokens,
    private val validator: ContentValidator,
    private val renderer: ContentRenderer,
    private val media: MediaAssets,
    private val locales: ContentLocales,
) {
    fun locales() = LocalesResponse(locales.supported, locales.default)

    fun collection(
        key: String,
        locale: String,
        page: Int,
        size: Int,
        filters: Map<String, String> = emptyMap(),
        sort: String? = null,
    ): PageResponse<PublishedEntryResponse> {
        val blueprint = blueprintOf(key, BlueprintKind.COLLECTION)
        val result = entries.findPublished(queries.build(blueprint, filters, sort, page, size))
        val render = renderer(blueprint, result.content.map { checkNotNull(it.published) }, locale)
        return PageResponse.of(result) { it.toPublished(render) }
    }

    fun entry(
        key: String,
        slug: String,
        locale: String,
    ): PublishedEntryResponse {
        val blueprint = blueprintOf(key, BlueprintKind.COLLECTION)
        val entry = entries.findByBlueprintIdAndPublishedSlug(blueprint.id, slug) ?: throw notFound()
        return entry.toPublished(renderer(blueprint, listOf(checkNotNull(entry.published)), locale))
    }

    fun singleton(
        key: String,
        locale: String,
    ): PublishedEntryResponse {
        val blueprint = blueprintOf(key, BlueprintKind.SINGLETON)
        val entry = entries.findFirstByBlueprintId(blueprint.id)?.takeIf { it.published != null } ?: throw notFound()
        return entry.toPublished(renderer(blueprint, listOf(checkNotNull(entry.published)), locale))
    }

    fun preview(
        token: String,
        locale: String,
    ): PreviewEntryResponse {
        val invalid = BusinessException(ContentErrorCode.PREVIEW_TOKEN_INVALID)
        val entry = previews.verify(token)?.let(entries::findWithBlueprint) ?: throw invalid
        val blueprint = entry.blueprint
        return PreviewEntryResponse(
            id = entry.id,
            blueprint = blueprint.key,
            slug = entry.slug,
            revision = entry.revision,
            content = renderer(blueprint, listOf(entry.draft), locale)(entry.draft),
        )
    }

    private fun Entry.toPublished(render: (ObjectNode) -> ObjectNode) =
        PublishedEntryResponse(
            id = id,
            slug = publishedSlug,
            publishedAt = checkNotNull(publishedAt),
            content = render(checkNotNull(published)),
        )

    private fun renderer(
        blueprint: Blueprint,
        contents: List<ObjectNode>,
        locale: String,
    ): (ObjectNode) -> ObjectNode {
        val ids = contents.flatMap { validator.collectReferences(blueprint.fields, it) }.toSet()
        val links =
            if (ids.isEmpty()) {
                emptyMap()
            } else {
                entries
                    .findReferences(ids)
                    .filter { it.published }
                    .associate { it.id to ContentRenderer.LinkedEntry(it.id, it.blueprint, it.slug) }
            }
        val assets = media.findAll(contents.flatMap { validator.collectAssets(blueprint.fields, it) }.toSet())
        return { content -> renderer.render(blueprint.fields, content, locale, locales.default, links, assets) }
    }

    private fun blueprintOf(
        key: String,
        kind: BlueprintKind,
    ): Blueprint =
        blueprints.findByKey(key)?.takeIf { it.kind == kind }
            ?: throw BusinessException(ContentErrorCode.BLUEPRINT_NOT_FOUND)

    private fun notFound() = BusinessException(ContentErrorCode.ENTRY_NOT_FOUND)
}

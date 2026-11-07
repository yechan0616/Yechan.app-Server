package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.LocalesResponse
import app.yechan.server.domain.content.dto.PublishedEntryResponse
import app.yechan.server.domain.content.entity.Blueprint
import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.entity.Entry
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.repository.BlueprintRepository
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.global.common.response.PageResponse
import app.yechan.server.global.error.BusinessException
import app.yechan.server.global.i18n.ContentLocales
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PublishedContent(
    private val blueprints: BlueprintRepository,
    private val entries: EntryRepository,
    private val validator: ContentValidator,
    private val renderer: ContentRenderer,
    private val locales: ContentLocales,
) {
    fun locales() = LocalesResponse(locales.supported, locales.default)

    fun collection(
        key: String,
        locale: String,
        page: Int,
        size: Int,
    ): PageResponse<PublishedEntryResponse> {
        val blueprint = blueprintOf(key, BlueprintKind.COLLECTION)
        val result =
            entries.findAllByBlueprintIdAndPublishedAtIsNotNull(
                blueprint.id,
                PageRequest.of(page, size, Entries.ORDER),
            )
        val render = renderer(blueprint, result.content, locale)
        return PageResponse.of(result, render)
    }

    fun entry(
        key: String,
        slug: String,
        locale: String,
    ): PublishedEntryResponse {
        val blueprint = blueprintOf(key, BlueprintKind.COLLECTION)
        val entry = entries.findByBlueprintIdAndPublishedSlug(blueprint.id, slug) ?: throw notFound()
        return renderer(blueprint, listOf(entry), locale)(entry)
    }

    fun singleton(
        key: String,
        locale: String,
    ): PublishedEntryResponse {
        val blueprint = blueprintOf(key, BlueprintKind.SINGLETON)
        val entry = entries.findFirstByBlueprintId(blueprint.id)?.takeIf { it.published != null } ?: throw notFound()
        return renderer(blueprint, listOf(entry), locale)(entry)
    }

    private fun renderer(
        blueprint: Blueprint,
        page: List<Entry>,
        locale: String,
    ): (Entry) -> PublishedEntryResponse {
        val ids = page.flatMap { validator.collectReferences(blueprint.fields, it.published) }.toSet()
        val links =
            if (ids.isEmpty()) {
                emptyMap()
            } else {
                entries
                    .findReferences(ids)
                    .filter { it.published }
                    .associate { it.id to ContentRenderer.LinkedEntry(it.id, it.blueprint, it.slug) }
            }
        return { entry ->
            PublishedEntryResponse(
                id = entry.id,
                slug = entry.publishedSlug,
                publishedAt = checkNotNull(entry.publishedAt),
                content =
                    renderer.render(
                        blueprint.fields,
                        checkNotNull(entry.published),
                        locale,
                        locales.default,
                        links,
                    ),
            )
        }
    }

    private fun blueprintOf(
        key: String,
        kind: BlueprintKind,
    ): Blueprint =
        blueprints.findByKey(key)?.takeIf { it.kind == kind }
            ?: throw BusinessException(ContentErrorCode.BLUEPRINT_NOT_FOUND)

    private fun notFound() = BusinessException(ContentErrorCode.ENTRY_NOT_FOUND)
}

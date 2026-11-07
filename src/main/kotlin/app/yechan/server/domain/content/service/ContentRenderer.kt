package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.domain.media.dto.AssetResponse
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.util.UUID

@Component
class ContentRenderer {
    data class LinkedEntry(
        val id: UUID,
        val blueprint: String,
        val slug: String?,
    )

    fun render(
        fields: List<FieldDefinition>,
        content: JsonNode,
        locale: String,
        defaultLocale: String,
        links: Map<UUID, LinkedEntry>,
        assets: Map<UUID, AssetResponse> = emptyMap(),
    ): ObjectNode = Renderer(locale, defaultLocale, links, assets).renderObject(fields, content)

    private class Renderer(
        private val locale: String,
        private val defaultLocale: String,
        private val links: Map<UUID, LinkedEntry>,
        private val assets: Map<UUID, AssetResponse>,
    ) {
        private val nodes = JsonNodeFactory.instance

        fun renderObject(
            fields: List<FieldDefinition>,
            content: JsonNode?,
        ): ObjectNode =
            nodes.objectNode().also { result ->
                fields.forEach { field -> result.set(field.key, renderValue(field, content?.get(field.key))) }
            }

        private fun renderValue(
            field: FieldDefinition,
            value: JsonNode?,
        ): JsonNode {
            if (value == null || value.isNull) return nodes.nullNode()
            return when {
                field.localized -> {
                    pickLocale(value)
                }

                field.type == FieldType.REFERENCE -> {
                    renderLink(value)
                }

                field.type == FieldType.ASSET -> {
                    renderAsset(value)
                }

                field.type == FieldType.LIST -> {
                    nodes.arrayNode().also { array ->
                        value.values().forEach { array.add(renderValue(checkNotNull(field.item), it)) }
                    }
                }

                field.type == FieldType.OBJECT -> {
                    renderObject(checkNotNull(field.fields), value)
                }

                else -> {
                    value
                }
            }
        }

        private fun pickLocale(value: JsonNode): JsonNode =
            listOf(locale, defaultLocale)
                .firstNotNullOfOrNull { tag -> value.get(tag)?.takeIf { it.isString && it.stringValue().isNotBlank() } }
                ?: nodes.nullNode()

        private fun renderAsset(value: JsonNode): JsonNode {
            val id =
                runCatching { UUID.fromString(value.get("asset")?.stringValue()) }.getOrNull()
                    ?: return nodes.nullNode()
            val asset = assets[id] ?: return nodes.nullNode()
            val alt = value.get("alt")?.takeIf { it.isObject }?.let(::pickLocale) ?: nodes.nullNode()
            return nodes
                .objectNode()
                .put("url", asset.url)
                .put("width", asset.width)
                .put("height", asset.height)
                .put("contentType", asset.contentType)
                .set("alt", alt)
        }

        private fun renderLink(value: JsonNode): JsonNode {
            val id = runCatching { UUID.fromString(value.stringValue()) }.getOrNull() ?: return nodes.nullNode()
            val link = links[id] ?: return nodes.nullNode()
            return nodes
                .objectNode()
                .put("id", link.id.toString())
                .put("blueprint", link.blueprint)
                .put("slug", link.slug)
        }
    }
}

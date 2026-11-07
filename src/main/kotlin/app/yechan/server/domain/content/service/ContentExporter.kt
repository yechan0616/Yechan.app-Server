package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.BundleAsset
import app.yechan.server.domain.content.dto.BundleEntry
import app.yechan.server.domain.content.dto.ContentBundle
import app.yechan.server.domain.content.dto.CreateBlueprintRequest
import app.yechan.server.domain.content.entity.Blueprint
import app.yechan.server.domain.content.entity.EntryStatus
import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.domain.content.repository.BlueprintRepository
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.domain.media.service.MediaAssets
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.io.OutputStream
import java.util.UUID

@Service
class ContentExporter(
    private val json: JsonMapper,
    private val blueprints: BlueprintRepository,
    private val entries: EntryRepository,
    private val media: MediaAssets,
) {
    @Transactional(readOnly = true)
    fun export(output: OutputStream) {
        val blueprintList = ordered(blueprints.findAllByOrderByNameAsc())
        val fields = blueprintList.associate { it.key to it.fields }
        val entryList = entries.findAll().sortedWith(compareBy({ it.blueprint.key }, { it.position }, { it.createdAt }))
        val entryIds = entryList.map { it.id }.toSet()
        val assetList = media.all()
        val assetIds = assetList.map { it.id }.toSet()
        val tokenize = {
            blueprint: String,
            content: ObjectNode,
            ->
            tokenize(checkNotNull(fields[blueprint]), content, entryIds, assetIds)
        }

        val bundle =
            ContentBundle(
                blueprints =
                    blueprintList.map {
                        CreateBlueprintRequest(
                            it.key,
                            it.name,
                            it.description,
                            it.kind,
                            it.fields,
                        )
                    },
                assets = assetList.map { BundleAsset(it.id.toString(), fileOf(it.id, it.contentType), it.filename) },
                entries =
                    entryList.map { entry ->
                        BundleEntry(
                            ref = entry.id.toString(),
                            blueprint = entry.blueprint.key,
                            slug = entry.slug,
                            position = entry.position,
                            publish = entry.status != EntryStatus.DRAFT,
                            content = tokenize(entry.blueprint.key, entry.draft),
                            published =
                                entry.published?.takeIf { entry.status == EntryStatus.CHANGED }?.let {
                                    tokenize(
                                        entry.blueprint.key,
                                        it,
                                    )
                                },
                        )
                    },
            )

        val files =
            sequenceOf(ContentImporter.BUNDLE_FILE to json.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle)) +
                assetList.asSequence().map { fileOf(it.id, it.contentType) to media.download(it.id) }
        BundleArchive.write(output, files)
    }

    private fun ordered(list: List<Blueprint>): List<Blueprint> {
        val remaining = list.toMutableList()
        val result = mutableListOf<Blueprint>()
        while (remaining.isNotEmpty()) {
            val done = result.map { it.key }.toSet()
            val next =
                remaining
                    .filter { b ->
                        b.fields.flatMap { it.referencedBlueprints() }.all { it == b.key || it in done }
                    }.ifEmpty { listOf(remaining.first()) }
            result += next
            remaining -= next.toSet()
        }
        return result
    }

    private fun tokenize(
        fields: List<FieldDefinition>,
        content: ObjectNode,
        entryIds: Set<UUID>,
        assetIds: Set<UUID>,
    ): ObjectNode = Tokenizer(entryIds, assetIds).objectOf(fields, content)

    private class Tokenizer(
        private val entryIds: Set<UUID>,
        private val assetIds: Set<UUID>,
    ) {
        private val nodes = JsonNodeFactory.instance

        fun objectOf(
            fields: List<FieldDefinition>,
            value: JsonNode,
        ): ObjectNode {
            val byKey = fields.associateBy { it.key }
            return nodes.objectNode().also { copy ->
                value.properties().forEach { (key, child) ->
                    copy.set(
                        key,
                        byKey[key]?.let { valueOf(it, child) } ?: child,
                    )
                }
            }
        }

        private fun valueOf(
            field: FieldDefinition,
            value: JsonNode,
        ): JsonNode =
            when {
                value.isNull -> {
                    value
                }

                field.type == FieldType.REFERENCE -> {
                    token(ContentImporter.ENTRY_PREFIX, value, entryIds)
                }

                field.type == FieldType.ASSET && value.isObject -> {
                    (value.deepCopy() as ObjectNode).also { copy ->
                        value.get("asset")?.let { copy.set("asset", token(ContentImporter.ASSET_PREFIX, it, assetIds)) }
                    }
                }

                field.type == FieldType.LIST && value.isArray -> {
                    nodes.arrayNode().also { array ->
                        value.values().forEach { array.add(valueOf(checkNotNull(field.item), it)) }
                    }
                }

                field.type == FieldType.OBJECT && value.isObject -> {
                    objectOf(checkNotNull(field.fields), value)
                }

                else -> {
                    value
                }
            }

        private fun token(
            prefix: String,
            value: JsonNode,
            known: Set<UUID>,
        ): JsonNode {
            val id = value.takeIf { it.isString }?.let { runCatching { UUID.fromString(it.stringValue()) }.getOrNull() }
            return if (id != null && id in known) nodes.stringNode(prefix + id) else value
        }
    }

    private fun fileOf(
        id: UUID,
        contentType: String,
    ) = "assets/$id.${contentType.substringAfter('/').replace("jpeg", "jpg")}"
}

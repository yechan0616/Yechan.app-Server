package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.ContentBundle
import app.yechan.server.domain.content.dto.CreateEntryRequest
import app.yechan.server.domain.content.dto.ImportReport
import app.yechan.server.domain.content.dto.PublishEntryRequest
import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.repository.BlueprintRepository
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.domain.media.service.MediaAssets
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

// Uses the studio services so bundles get the same validation; existing keys and slugs are skipped, so re-runs are safe.
@Service
class ContentImporter(
    private val json: JsonMapper,
    private val blueprints: Blueprints,
    private val blueprintRepository: BlueprintRepository,
    private val entries: Entries,
    private val entryRepository: EntryRepository,
    private val media: MediaAssets,
) {
    fun import(directory: Path): ImportReport {
        val root = directory.toRealPath()
        val bundle = json.readValue<ContentBundle>(Files.readAllBytes(root.resolve(BUNDLE_FILE)))

        var blueprintsCreated = 0
        bundle.blueprints.filterNot { blueprintRepository.existsByKey(it.key) }.forEach {
            blueprints.create(it)
            blueprintsCreated++
        }

        var assetsUploaded = 0
        val assets =
            bundle.assets.associate { asset ->
                val file = root.resolve(asset.file).normalize()
                require(file.startsWith(root) && Files.isRegularFile(file)) {
                    "Asset file is outside the bundle or missing: ${asset.file}"
                }
                val uploaded = media.upload(Files.readAllBytes(file), file.fileName.toString())
                if (uploaded.created) assetsUploaded++
                asset.ref to uploaded.asset.id
            }

        val created = mutableMapOf<String, UUID>()
        var entriesCreated = 0
        var entriesSkipped = 0
        bundle.entries.forEach { entry ->
            val blueprint = blueprints.require(entry.blueprint)
            val existing =
                if (blueprint.kind == BlueprintKind.SINGLETON) {
                    entryRepository.findFirstByBlueprintId(blueprint.id)
                } else {
                    entry.slug?.let { entryRepository.findByBlueprintIdAndSlug(blueprint.id, it) }
                }
            if (existing != null) {
                created[entry.ref] = existing.id
                entriesSkipped++
                return@forEach
            }
            val content = resolve(entry.content, created, assets) as ObjectNode
            val saved = entries.create(entry.blueprint, CreateEntryRequest(entry.slug, entry.position, content))
            if (entry.publish) entries.publish(saved.id, PublishEntryRequest(saved.revision))
            created[entry.ref] = saved.id
            entriesCreated++
        }
        return ImportReport(blueprintsCreated, assetsUploaded, entriesCreated, entriesSkipped)
    }

    private fun resolve(
        node: JsonNode,
        entries: Map<String, UUID>,
        assets: Map<String, UUID>,
    ): JsonNode =
        when {
            node.isObject -> {
                val copy = JsonNodeFactory.instance.objectNode()
                node.properties().forEach { (key, value) -> copy.set(key, resolve(value, entries, assets)) }
                copy
            }

            node.isArray -> {
                val copy: ArrayNode = JsonNodeFactory.instance.arrayNode()
                node.values().forEach { copy.add(resolve(it, entries, assets)) }
                copy
            }

            node.isString && node.stringValue().startsWith(ENTRY_PREFIX) -> {
                val ref = node.stringValue().removePrefix(ENTRY_PREFIX)
                JsonNodeFactory.instance.stringNode(
                    requireNotNull(entries[ref]) {
                        "Unknown entry ref '$ref' (define it earlier)"
                    }.toString(),
                )
            }

            node.isString && node.stringValue().startsWith(ASSET_PREFIX) -> {
                val ref = node.stringValue().removePrefix(ASSET_PREFIX)
                JsonNodeFactory.instance.stringNode(
                    requireNotNull(assets[ref]) { "Unknown asset ref '$ref'" }.toString(),
                )
            }

            else -> {
                node
            }
        }

    private companion object {
        const val BUNDLE_FILE = "bundle.json"
        const val ENTRY_PREFIX = "@entry:"
        const val ASSET_PREFIX = "@asset:"
    }
}

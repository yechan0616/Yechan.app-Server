package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.BundleEntry
import app.yechan.server.domain.content.dto.ContentBundle
import app.yechan.server.domain.content.dto.CreateBlueprintRequest
import app.yechan.server.domain.content.dto.CreateEntryRequest
import app.yechan.server.domain.content.dto.EntryResponse
import app.yechan.server.domain.content.dto.ImportReport
import app.yechan.server.domain.content.dto.PublishEntryRequest
import app.yechan.server.domain.content.dto.UpdateEntryRequest
import app.yechan.server.domain.content.entity.BlueprintKind
import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.repository.BlueprintRepository
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.domain.media.service.MediaAssets
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.readValue
import java.io.InputStream
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
    fun import(source: Path): ImportReport =
        if (Files.isRegularFile(source)) Files.newInputStream(source).use(::importArchive) else importDirectory(source)

    fun importArchive(archive: InputStream): ImportReport {
        val directory = Files.createTempDirectory("content-bundle")
        try {
            BundleArchive.extract(archive, directory)
            return importDirectory(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun importDirectory(directory: Path): ImportReport {
        val root = directory.toRealPath()
        val bundle = json.readValue<ContentBundle>(Files.readAllBytes(root.resolve(BUNDLE_FILE)))
        val blueprintsCreated = createBlueprints(bundle.blueprints)

        var assetsUploaded = 0
        val assets =
            bundle.assets.associate { asset ->
                val file = root.resolve(asset.file).normalize()
                require(file.startsWith(root) && Files.isRegularFile(file)) {
                    "Asset file is outside the bundle or missing: ${asset.file}"
                }
                val uploaded = media.upload(Files.readAllBytes(file), asset.filename ?: file.fileName.toString())
                if (uploaded.created) assetsUploaded++
                asset.ref to uploaded.asset.id
            }

        val ids = mutableMapOf<String, UUID>()
        val created = mutableListOf<Pair<BundleEntry, EntryResponse>>()
        var skipped = 0
        bundle.entries.forEach { entry ->
            val existing = existingEntry(entry)
            if (existing != null) {
                ids[entry.ref] = existing
                skipped++
            } else {
                // Forward references are filled in once every entry exists.
                val content = resolve(entry.content, ids, assets, strict = false) as ObjectNode
                val request = CreateEntryRequest(entry.slug, entry.position, content)
                val saved = entries.create(entry.blueprint, request, preservedId(entry))
                ids[entry.ref] = saved.id
                created += entry to saved
            }
        }

        created.forEach { (entry, saved) ->
            var current = saved
            val saveDraft = { content: ObjectNode ->
                val resolved = resolve(content, ids, assets) as ObjectNode
                current =
                    entries.update(
                        current.id,
                        UpdateEntryRequest(entry.slug, entry.position, resolved, current.revision),
                    )
            }
            when {
                entry.published != null -> {
                    saveDraft(entry.published)
                    current = entries.publish(current.id, PublishEntryRequest(current.revision))
                    saveDraft(entry.content)
                }

                else -> {
                    if (hasForwardReference(current.content)) saveDraft(entry.content)
                    if (entry.publish) entries.publish(current.id, PublishEntryRequest(current.revision))
                }
            }
        }
        return ImportReport(blueprintsCreated, assetsUploaded, created.size, skipped)
    }

    private fun createBlueprints(requests: List<CreateBlueprintRequest>): Int {
        val pending = requests.filterNot { blueprintRepository.existsByKey(it.key) }.toMutableList()
        var createdCount = 0
        while (pending.isNotEmpty()) {
            val known = blueprintRepository.findAllKeys()
            val ready =
                pending.filter { request ->
                    request.fields.flatMap { it.referencedBlueprints() }.all {
                        it == request.key ||
                            it in known
                    }
                }
            // A reference cycle cannot be ordered; creating it anyway surfaces the validation error.
            ready.ifEmpty { listOf(pending.first()) }.forEach {
                blueprints.create(it)
                pending.remove(it)
                createdCount++
            }
        }
        return createdCount
    }

    private fun hasForwardReference(node: JsonNode): Boolean =
        when {
            node.isString -> node.stringValue() == PENDING.toString()
            node.isContainer -> node.values().any(::hasForwardReference)
            else -> false
        }

    // Exported bundles use entry ids as refs, so an entry keeps its id across servers and re-imports.
    private fun existingEntry(entry: BundleEntry): UUID? {
        val blueprint = blueprints.require(entry.blueprint)
        preservedId(entry)?.takeIf { entryRepository.existsById(it) }?.let { return it }
        return if (blueprint.kind == BlueprintKind.SINGLETON) {
            entryRepository.findFirstByBlueprintId(blueprint.id)?.id
        } else {
            entry.slug?.let { entryRepository.findByBlueprintIdAndSlug(blueprint.id, it)?.id }
        }
    }

    private fun preservedId(entry: BundleEntry): UUID? =
        runCatching { UUID.fromString(entry.ref) }.getOrNull()?.takeIf { entryRepository.findTrashed(it) == null }

    private fun resolve(
        node: JsonNode,
        entries: Map<String, UUID>,
        assets: Map<String, UUID>,
        strict: Boolean = true,
    ): JsonNode {
        val nodes = JsonNodeFactory.instance
        return when {
            node.isObject -> {
                nodes.objectNode().also { copy ->
                    node.properties().forEach { (k, v) -> copy.set(k, resolve(v, entries, assets, strict)) }
                }
            }

            node.isArray -> {
                nodes.arrayNode().also { copy ->
                    node.values().forEach {
                        copy.add(
                            resolve(it, entries, assets, strict),
                        )
                    }
                }
            }

            node.isString && node.stringValue().startsWith(ENTRY_PREFIX) -> {
                val ref = node.stringValue().removePrefix(ENTRY_PREFIX)
                val id =
                    entries[ref] ?: if (strict) throw IllegalArgumentException("Unknown entry ref '$ref'") else PENDING
                nodes.stringNode(id.toString())
            }

            node.isString && node.stringValue().startsWith(ASSET_PREFIX) -> {
                val ref = node.stringValue().removePrefix(ASSET_PREFIX)
                nodes.stringNode(requireNotNull(assets[ref]) { "Unknown asset ref '$ref'" }.toString())
            }

            else -> {
                node
            }
        }
    }

    companion object {
        const val BUNDLE_FILE = "bundle.json"
        const val ENTRY_PREFIX = "@entry:"
        const val ASSET_PREFIX = "@asset:"
        private val PENDING = UUID(0, 0)
    }
}

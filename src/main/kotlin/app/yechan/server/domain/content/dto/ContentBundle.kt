package app.yechan.server.domain.content.dto

import tools.jackson.databind.node.ObjectNode

// In entry content, "@entry:<ref>" and "@asset:<ref>" strings are replaced with the created ids.
data class ContentBundle(
    val blueprints: List<CreateBlueprintRequest> = emptyList(),
    val assets: List<BundleAsset> = emptyList(),
    val entries: List<BundleEntry> = emptyList(),
)

data class BundleAsset(
    val ref: String,
    val file: String,
)

data class BundleEntry(
    val ref: String,
    val blueprint: String,
    val slug: String? = null,
    val position: Int = 0,
    val publish: Boolean = true,
    val content: ObjectNode,
    val published: ObjectNode? = null,
)

data class ImportReport(
    val blueprintsCreated: Int,
    val assetsUploaded: Int,
    val entriesCreated: Int,
    val entriesSkipped: Int,
)

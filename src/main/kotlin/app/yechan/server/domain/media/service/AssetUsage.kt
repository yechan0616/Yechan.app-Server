package app.yechan.server.domain.media.service

import java.util.UUID

fun interface AssetUsage {
    fun isInUse(assetId: UUID): Boolean
}

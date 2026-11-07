package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.domain.media.service.AssetUsage
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EntryAssetUsage(
    private val entries: EntryRepository,
) : AssetUsage {
    override fun usedAmong(assetIds: Collection<UUID>): Set<UUID> =
        if (assetIds.isEmpty()) {
            emptySet()
        } else {
            entries
                .mentionedAmong(
                    assetIds.joinToString(","),
                ).map(UUID::fromString)
                .toSet()
        }
}

package app.yechan.server.domain.media.repository

import app.yechan.server.domain.media.entity.Asset
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AssetRepository : JpaRepository<Asset, UUID> {
    fun findBySha256(sha256: ByteArray): Asset?

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Asset>
}

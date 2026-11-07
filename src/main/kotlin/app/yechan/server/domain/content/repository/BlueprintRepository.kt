package app.yechan.server.domain.content.repository

import app.yechan.server.domain.content.entity.Blueprint
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BlueprintRepository : JpaRepository<Blueprint, UUID> {
    fun findByKey(key: String): Blueprint?

    fun existsByKey(key: String): Boolean

    fun findAllByOrderByNameAsc(): List<Blueprint>

    @Query("select b.key from Blueprint b")
    fun findAllKeys(): Set<String>

    // Row lock that serialises singleton entry creation and restoration.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Blueprint b where b.id = :id")
    fun lockById(id: UUID): Blueprint?
}

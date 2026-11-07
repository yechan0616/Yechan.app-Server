package app.yechan.server.domain.auth.repository

import app.yechan.server.domain.auth.entity.SecurityEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SecurityEventRepository : JpaRepository<SecurityEvent, UUID> {
    fun findAllByAdministratorIdOrderByCreatedAtDesc(
        administratorId: UUID,
        pageable: Pageable,
    ): Page<SecurityEvent>

    @Modifying
    @Query("delete from SecurityEvent e where e.createdAt < :before")
    fun deleteOlderThan(before: Instant): Int
}

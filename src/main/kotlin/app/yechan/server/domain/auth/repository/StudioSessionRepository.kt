package app.yechan.server.domain.auth.repository

import app.yechan.server.domain.auth.entity.StudioSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface StudioSessionRepository : JpaRepository<StudioSession, UUID> {
    @Query("select s from StudioSession s join fetch s.administrator where s.tokenHash = :tokenHash")
    fun findByTokenHash(tokenHash: ByteArray): StudioSession?

    @Modifying
    @Query("delete from StudioSession s where s.administrator.id = :administratorId and s.id <> :keep")
    fun deleteOthers(
        administratorId: UUID,
        keep: UUID,
    ): Int

    @Modifying
    @Query("delete from StudioSession s where s.expiresAt <= :now or s.idleExpiresAt <= :now")
    fun deleteExpired(now: Instant): Int
}

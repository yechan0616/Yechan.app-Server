package app.yechan.server.domain.auth.repository

import app.yechan.server.domain.auth.entity.PasskeyCeremony
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface PasskeyCeremonyRepository : JpaRepository<PasskeyCeremony, UUID> {
    fun countByExpiresAtAfter(now: Instant): Long

    fun countByIpAddressAndExpiresAtAfter(
        ipAddress: String,
        now: Instant,
    ): Long

    // Returns 1 only for the request that actually consumed the ceremony.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PasskeyCeremony c where c.id = :id")
    fun consume(id: UUID): Int

    @Modifying
    @Query("delete from PasskeyCeremony c where c.expiresAt <= :now")
    fun deleteExpired(now: Instant): Int

    @Modifying
    @Query(
        value = """
            delete from passkey_ceremony
            where id in (select id from passkey_ceremony order by created_at asc, id asc limit :count)
            """,
        nativeQuery = true,
    )
    fun deleteOldest(count: Long): Int
}

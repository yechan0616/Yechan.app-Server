package app.yechan.server.domain.content.repository

import app.yechan.server.domain.content.entity.EntryRevision
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EntryRevisionRepository : JpaRepository<EntryRevision, UUID> {
    fun findAllByEntryIdOrderByNumberDesc(entryId: UUID): List<EntryRevision>

    fun findByEntryIdAndNumber(
        entryId: UUID,
        number: Int,
    ): EntryRevision?

    @Query("select coalesce(max(r.number), 0) from EntryRevision r where r.entry.id = :entryId")
    fun latestNumber(entryId: UUID): Int

    @Modifying
    @Query("delete from EntryRevision r where r.entry.id = :entryId and r.number <= :number")
    fun deleteUpTo(
        entryId: UUID,
        number: Int,
    ): Int
}

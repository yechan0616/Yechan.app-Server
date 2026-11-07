package app.yechan.server.domain.content.repository

import app.yechan.server.domain.content.entity.Entry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface EntryRepository :
    JpaRepository<Entry, UUID>,
    EntrySearch {
    @Query("select e from Entry e join fetch e.blueprint where e.id = :id")
    fun findWithBlueprint(id: UUID): Entry?

    fun findAllByBlueprintId(
        blueprintId: UUID,
        pageable: Pageable,
    ): Page<Entry>

    fun findAllByBlueprintIdAndPublishedAtIsNotNull(
        blueprintId: UUID,
        pageable: Pageable,
    ): Page<Entry>

    fun findByBlueprintIdAndPublishedSlug(
        blueprintId: UUID,
        publishedSlug: String,
    ): Entry?

    fun findFirstByBlueprintId(blueprintId: UUID): Entry?

    fun findByBlueprintIdAndSlug(
        blueprintId: UUID,
        slug: String,
    ): Entry?

    fun countByBlueprintId(blueprintId: UUID): Long

    fun countByBlueprintIdAndIdIn(
        blueprintId: UUID,
        ids: Collection<UUID>,
    ): Long

    @Modifying
    @Query("update Entry e set e.position = :position where e.id = :id")
    fun updatePosition(
        id: UUID,
        position: Int,
    ): Int

    fun existsByBlueprintIdAndSlugAndIdNot(
        blueprintId: UUID,
        slug: String,
        id: UUID,
    ): Boolean

    fun existsByBlueprintIdAndSlug(
        blueprintId: UUID,
        slug: String,
    ): Boolean

    fun existsByBlueprintIdAndPublishedSlugAndIdNot(
        blueprintId: UUID,
        publishedSlug: String,
        id: UUID,
    ): Boolean

    fun existsByBlueprintIdAndPublishedSlug(
        blueprintId: UUID,
        publishedSlug: String,
    ): Boolean

    // Trashed entries and revisions count too, so restoring them never brings back a missing image.
    @Query(
        value = """
            select candidate from unnest(string_to_array(:ids, ',')) as candidate
            where exists (
                select 1 from entry e
                where strpos(e.draft::text, candidate) > 0 or strpos(coalesce(e.published::text, ''), candidate) > 0
            ) or exists (select 1 from entry_revision r where strpos(r.content::text, candidate) > 0)
            """,
        nativeQuery = true,
    )
    fun mentionedAmong(ids: String): List<String>

    @Query(
        value = """
            select e.id as id, b.key as blueprint, e.slug as slug from entry e join blueprint b on b.id = e.blueprint_id
            where e.deleted_at is null and e.id <> :id
              and (strpos(e.draft::text, :token) > 0 or strpos(coalesce(e.published::text, ''), :token) > 0)
            order by b.key, e.slug limit 20
            """,
        nativeQuery = true,
    )
    fun referencing(
        id: UUID,
        token: String,
    ): List<EntryLink>

    @Query(
        value = """
            select e.id as id, b.key as blueprint, e.slug as slug, e.published_slug as publishedSlug,
                   e.published_at as publishedAt, e.deleted_at as deletedAt
            from entry e join blueprint b on b.id = e.blueprint_id
            where e.deleted_at is not null order by e.deleted_at desc
            """,
        countQuery = "select count(*) from entry where deleted_at is not null",
        nativeQuery = true,
    )
    fun findTrash(pageable: Pageable): Page<TrashedEntry>

    @Query(
        value = """
            select e.id as id, b.key as blueprint, e.slug as slug, e.published_slug as publishedSlug,
                   e.published_at as publishedAt, e.deleted_at as deletedAt
            from entry e join blueprint b on b.id = e.blueprint_id where e.id = :id and e.deleted_at is not null
            """,
        nativeQuery = true,
    )
    fun findTrashed(id: UUID): TrashedEntry?

    @Modifying(clearAutomatically = true)
    @Query(value = "update entry set deleted_at = null where id = :id and deleted_at is not null", nativeQuery = true)
    fun restoreFromTrash(id: UUID): Int

    @Modifying
    @Query(value = "delete from entry where id = :id and deleted_at is not null", nativeQuery = true)
    fun deleteFromTrash(id: UUID): Int

    @Modifying
    @Query(value = "delete from entry where deleted_at is not null and deleted_at < :before", nativeQuery = true)
    fun purgeTrash(before: Instant): Int

    @Modifying
    @Query(value = "delete from entry where blueprint_id = :blueprintId and deleted_at is not null", nativeQuery = true)
    fun purgeTrashOf(blueprintId: UUID): Int

    @Query(
        """
        select e.id as id, b.key as blueprint, e.publishedSlug as slug, (e.publishedAt is not null) as published
        from Entry e join e.blueprint b where e.id in :ids
        """,
    )
    fun findReferences(ids: Collection<UUID>): List<EntryReference>
}

interface EntryLink {
    val id: UUID
    val blueprint: String
    val slug: String?
}

interface TrashedEntry {
    val id: UUID
    val blueprint: String
    val slug: String?
    val publishedSlug: String?
    val publishedAt: Instant?
    val deletedAt: Instant
}

interface EntryReference {
    val id: UUID
    val blueprint: String
    val slug: String?
    val published: Boolean
}

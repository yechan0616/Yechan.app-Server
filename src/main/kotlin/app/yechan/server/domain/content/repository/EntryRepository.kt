package app.yechan.server.domain.content.repository

import app.yechan.server.domain.content.entity.Entry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
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

    @Query(
        value = """
            select exists(
                select 1 from entry where strpos(draft::text, :id) > 0 or strpos(coalesce(published::text, ''), :id) > 0
            )
            """,
        nativeQuery = true,
    )
    fun mentions(id: String): Boolean

    @Query(
        """
        select e.id as id, b.key as blueprint, e.publishedSlug as slug, (e.publishedAt is not null) as published
        from Entry e join e.blueprint b where e.id in :ids
        """,
    )
    fun findReferences(ids: Collection<UUID>): List<EntryReference>
}

interface EntryReference {
    val id: UUID
    val blueprint: String
    val slug: String?
    val published: Boolean
}

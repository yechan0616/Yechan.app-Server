package app.yechan.server.domain.content.repository

import app.yechan.server.domain.content.entity.Entry
import app.yechan.server.domain.content.entity.EntryStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.UUID

data class PublishedQuery(
    val blueprintId: UUID,
    val filters: List<FieldFilter>,
    val sort: FieldSort?,
    val page: Int,
    val size: Int,
)

data class FieldFilter(
    val key: String,
    val value: Any,
    val numeric: Boolean,
)

data class FieldSort(
    val key: String,
    val kind: SortKind,
    val descending: Boolean,
)

enum class SortKind { TEXT, NUMBER, TIMESTAMP }

data class StudioQuery(
    val blueprintId: UUID,
    val search: String?,
    val status: EntryStatus?,
    val page: Int,
    val size: Int,
)

interface EntrySearch {
    fun findPublished(query: PublishedQuery): Page<Entry>

    fun findForStudio(query: StudioQuery): Page<Entry>
}

class EntrySearchImpl(
    @PersistenceContext private val entityManager: EntityManager,
) : EntrySearch {
    override fun findPublished(query: PublishedQuery): Page<Entry> {
        val parameters = mutableMapOf<String, Any>("blueprintId" to query.blueprintId)
        val conditions =
            mutableListOf("e.blueprint_id = :blueprintId", "e.published_at is not null", "e.deleted_at is null")
        query.filters.forEachIndexed { index, filter ->
            parameters["k$index"] = filter.key
            parameters["v$index"] = filter.value
            val field = if (filter.numeric) "(e.published ->> :k$index)::numeric" else "(e.published ->> :k$index)"
            conditions += "$field = :v$index"
        }

        val order = mutableListOf<String>()
        val orderParameters = mutableMapOf<String, Any>()
        query.sort?.let { sort ->
            orderParameters["sortKey"] = sort.key
            val expression =
                when (sort.kind) {
                    SortKind.TEXT -> "(e.published ->> :sortKey)"
                    SortKind.NUMBER -> "(e.published ->> :sortKey)::numeric"
                    SortKind.TIMESTAMP -> "(e.published ->> :sortKey)::timestamptz"
                }
            order += "$expression ${if (sort.descending) "desc" else "asc"} nulls last"
        }
        return page(conditions, parameters, order, orderParameters, query.page, query.size)
    }

    override fun findForStudio(query: StudioQuery): Page<Entry> {
        val parameters = mutableMapOf<String, Any>("blueprintId" to query.blueprintId)
        val conditions = mutableListOf("e.blueprint_id = :blueprintId", "e.deleted_at is null")
        query.search?.takeIf { it.isNotBlank() }?.let { search ->
            parameters["search"] =
                "%" +
                search
                    .trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_") + "%"
            conditions +=
                "(e.slug ilike :search or exists (select 1 from jsonb_each_text(e.draft) field where field.value ilike :search))"
        }
        val unchanged = "e.published = e.draft and e.published_slug is not distinct from e.slug"
        when (query.status) {
            EntryStatus.DRAFT -> conditions += "e.published is null"
            EntryStatus.PUBLISHED -> conditions += "e.published is not null and $unchanged"
            EntryStatus.CHANGED -> conditions += "e.published is not null and not ($unchanged)"
            null -> Unit
        }
        return page(conditions, parameters, emptyList(), emptyMap(), query.page, query.size)
    }

    private fun page(
        conditions: List<String>,
        parameters: Map<String, Any>,
        order: List<String>,
        orderParameters: Map<String, Any>,
        page: Int,
        size: Int,
    ): Page<Entry> {
        val where = conditions.joinToString(" and ")
        val orderBy = (order + listOf("e.position asc", "e.created_at asc")).joinToString(", ")
        val select =
            entityManager.createNativeQuery(
                "select e.* from entry e where $where order by $orderBy",
                Entry::class.java,
            )
        val count = entityManager.createNativeQuery("select count(*) from entry e where $where")
        parameters.forEach { (name, value) ->
            select.setParameter(name, value)
            count.setParameter(name, value)
        }
        orderParameters.forEach { (name, value) -> select.setParameter(name, value) }
        select.firstResult = page * size
        select.maxResults = size
        val entries = select.resultList.filterIsInstance<Entry>()
        return PageImpl(entries, PageRequest.of(page, size), (count.singleResult as Number).toLong())
    }
}

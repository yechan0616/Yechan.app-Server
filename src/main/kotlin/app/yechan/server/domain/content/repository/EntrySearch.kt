package app.yechan.server.domain.content.repository

import app.yechan.server.domain.content.entity.Entry
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

interface EntrySearch {
    fun findPublished(query: PublishedQuery): Page<Entry>
}

class EntrySearchImpl(
    @PersistenceContext private val entityManager: EntityManager,
) : EntrySearch {
    override fun findPublished(query: PublishedQuery): Page<Entry> {
        val parameters = mutableMapOf<String, Any>("blueprintId" to query.blueprintId)
        val conditions = mutableListOf("e.blueprint_id = :blueprintId", "e.published_at is not null")
        query.filters.forEachIndexed { index, filter ->
            parameters["k$index"] = filter.key
            parameters["v$index"] = filter.value
            val field = if (filter.numeric) "(e.published ->> :k$index)::numeric" else "(e.published ->> :k$index)"
            conditions += "$field = :v$index"
        }
        val where = conditions.joinToString(" and ")

        val order =
            buildList {
                query.sort?.let { sort ->
                    parameters["sortKey"] = sort.key
                    val expression =
                        when (sort.kind) {
                            SortKind.TEXT -> "(e.published ->> :sortKey)"
                            SortKind.NUMBER -> "(e.published ->> :sortKey)::numeric"
                            SortKind.TIMESTAMP -> "(e.published ->> :sortKey)::timestamptz"
                        }
                    add("$expression ${if (sort.descending) "desc" else "asc"} nulls last")
                }
                add("e.position asc")
                add("e.created_at asc")
            }.joinToString(", ")

        val select =
            entityManager.createNativeQuery(
                "select e.* from entry e where $where order by $order",
                Entry::class.java,
            )
        val count = entityManager.createNativeQuery("select count(*) from entry e where $where")
        parameters.forEach { (name, value) ->
            select.setParameter(name, value)
            if (name != "sortKey") count.setParameter(name, value)
        }
        select.firstResult = query.page * query.size
        select.maxResults = query.size

        val entries = select.resultList.filterIsInstance<Entry>()
        val total = (count.singleResult as Number).toLong()
        return PageImpl(entries, PageRequest.of(query.page, query.size), total)
    }
}

package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.entity.Blueprint
import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.repository.FieldFilter
import app.yechan.server.domain.content.repository.FieldSort
import app.yechan.server.domain.content.repository.PublishedQuery
import app.yechan.server.domain.content.repository.SortKind
import app.yechan.server.global.error.BusinessException
import app.yechan.server.global.error.FieldViolation
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

@Component
class PublishedQueries {
    fun build(
        blueprint: Blueprint,
        filters: Map<String, String>,
        sort: String?,
        page: Int,
        size: Int,
    ): PublishedQuery {
        val fields = blueprint.fields.associateBy { it.key }
        val violations = mutableListOf<FieldViolation>()
        if (filters.size > MAX_FILTERS) violations += FieldViolation("filter", "allows at most $MAX_FILTERS conditions")

        val parsedFilters =
            filters.mapNotNull { (key, value) ->
                val filter = fields[key]?.takeIf { value.length <= MAX_VALUE_LENGTH }?.let { filterOf(it, value) }
                if (filter == null) violations += FieldViolation("filter[$key]", "cannot filter by '$value'")
                filter
            }

        val parsedSort =
            sort?.takeIf { it.isNotBlank() }?.let { raw ->
                val field = fields[raw.removePrefix("-")]
                val kind = field?.let(::sortKindOf)
                if (field == null || kind == null) violations += FieldViolation("sort", "cannot sort by '$raw'")
                kind?.let { FieldSort(checkNotNull(field).key, it, descending = raw.startsWith("-")) }
            }

        if (violations.isNotEmpty()) {
            throw BusinessException(ContentErrorCode.ENTRY_QUERY_INVALID, violations = violations)
        }
        return PublishedQuery(blueprint.id, parsedFilters, parsedSort, page, size)
    }

    private fun filterOf(
        field: FieldDefinition,
        value: String,
    ): FieldFilter? {
        if (field.localized) return null
        val text = { accepted: Boolean -> if (accepted) FieldFilter(field.key, value, numeric = false) else null }
        return when (field.type) {
            FieldType.BOOLEAN -> {
                text(value == "true" || value == "false")
            }

            FieldType.SELECT -> {
                text(value in field.options.orEmpty())
            }

            FieldType.STRING -> {
                text(true)
            }

            FieldType.DATE -> {
                text(runCatching { LocalDate.parse(value) }.isSuccess)
            }

            FieldType.REFERENCE -> {
                text(runCatching { UUID.fromString(value) }.getOrNull()?.toString() == value)
            }

            FieldType.INTEGER, FieldType.NUMBER -> {
                value.toBigDecimalOrNull()?.let {
                    FieldFilter(
                        field.key,
                        it,
                        numeric = true,
                    )
                }
            }

            else -> {
                null
            }
        }
    }

    private fun sortKindOf(field: FieldDefinition): SortKind? {
        if (field.localized) return null
        return when (field.type) {
            FieldType.INTEGER, FieldType.NUMBER -> SortKind.NUMBER
            FieldType.DATETIME -> SortKind.TIMESTAMP
            FieldType.STRING, FieldType.SELECT, FieldType.DATE -> SortKind.TEXT
            else -> null
        }
    }

    private companion object {
        const val MAX_FILTERS = 5
        const val MAX_VALUE_LENGTH = 200
    }
}

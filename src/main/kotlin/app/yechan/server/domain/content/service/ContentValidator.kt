package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.global.error.FieldViolation
import app.yechan.server.global.i18n.ContentLocales
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

@Component
class ContentValidator(
    private val locales: ContentLocales,
) {
    enum class Mode { DRAFT, PUBLISH }

    fun validate(
        fields: List<FieldDefinition>,
        content: JsonNode,
        mode: Mode,
        references: Map<UUID, String> = emptyMap(),
        assets: Set<UUID> = emptySet(),
    ): List<FieldViolation> {
        val violations = mutableListOf<FieldViolation>()
        if (!content.isObject) {
            violations += FieldViolation(ROOT, "must be an object")
            return violations
        }
        if (content.toString().length > MAX_CONTENT_LENGTH) {
            violations += FieldViolation(ROOT, "must be at most ${MAX_CONTENT_LENGTH / 1024} KB")
            return violations
        }
        Walker(mode, references, assets, violations).checkObject(fields, content, ROOT)
        return violations
    }

    fun collectReferences(
        fields: List<FieldDefinition>,
        content: JsonNode?,
    ): Set<UUID> = collect(fields, content, FieldType.REFERENCE)

    fun collectAssets(
        fields: List<FieldDefinition>,
        content: JsonNode?,
    ): Set<UUID> = collect(fields, content, FieldType.ASSET)

    private fun collect(
        fields: List<FieldDefinition>,
        content: JsonNode?,
        type: FieldType,
    ): Set<UUID> {
        val ids = mutableSetOf<UUID>()

        fun visit(
            field: FieldDefinition,
            value: JsonNode?,
        ) {
            if (value == null || value.isNull) return
            when (field.type) {
                type -> {
                    idOf(field, value)?.let(ids::add)
                }

                FieldType.LIST -> {
                    if (value.isArray) value.values().forEach { visit(checkNotNull(field.item), it) }
                }

                FieldType.OBJECT -> {
                    if (value.isObject) checkNotNull(field.fields).forEach { visit(it, value.get(it.key)) }
                }

                else -> {
                    Unit
                }
            }
        }
        if (content != null && content.isObject) fields.forEach { visit(it, content.get(it.key)) }
        return ids
    }

    private fun idOf(
        field: FieldDefinition,
        value: JsonNode,
    ): UUID? {
        val raw = if (field.type == FieldType.ASSET) value.get(ASSET_ID) else value
        return raw?.takeIf { it.isString }?.let { parseUuid(it.stringValue()) }
    }

    private inner class Walker(
        private val mode: Mode,
        private val references: Map<UUID, String>,
        private val assets: Set<UUID>,
        private val violations: MutableList<FieldViolation>,
    ) {
        private val publishing = mode == Mode.PUBLISH

        fun checkObject(
            fields: List<FieldDefinition>,
            node: JsonNode,
            path: String,
        ) {
            val known = fields.map { it.key }.toSet()
            node.propertyNames().filterNot { it in known }.forEach {
                fail(
                    "$path.$it",
                    "is not defined in the blueprint",
                )
            }
            fields.forEach { field -> checkValue(field, node.get(field.key), "$path.${field.key}") }
        }

        private fun checkValue(
            field: FieldDefinition,
            value: JsonNode?,
            path: String,
            isItem: Boolean = false,
        ) {
            if (value == null || value.isNull) {
                if (isItem) {
                    fail(path, "must not be null")
                } else if (publishing && field.required) {
                    fail(path, "is required")
                }
                return
            }
            when (field.type) {
                FieldType.STRING, FieldType.TEXT, FieldType.MARKDOWN -> {
                    checkTextField(field, value, path, isItem)
                }

                FieldType.NUMBER -> {
                    checkNumber(field, value, path, integral = false)
                }

                FieldType.INTEGER -> {
                    checkNumber(field, value, path, integral = true)
                }

                FieldType.BOOLEAN -> {
                    if (!value.isBoolean) fail(path, "must be a boolean")
                }

                FieldType.URL -> {
                    checkFormatted(
                        field,
                        value,
                        path,
                        "must be an https URL or a path starting with /",
                        ::isUrl,
                    )
                }

                FieldType.EMAIL -> {
                    checkFormatted(field, value, path, "must be an email address", EMAIL::matches)
                }

                FieldType.DATE -> {
                    checkFormatted(field, value, path, "must be a date (YYYY-MM-DD)", ::isDate)
                }

                FieldType.DATETIME -> {
                    checkFormatted(
                        field,
                        value,
                        path,
                        "must be an ISO-8601 date-time with offset",
                        ::isDateTime,
                    )
                }

                FieldType.SELECT -> {
                    checkFormatted(
                        field,
                        value,
                        path,
                        "must be one of ${field.options}",
                        field.options.orEmpty()::contains,
                    )
                }

                FieldType.REFERENCE -> {
                    checkReference(field, value, path)
                }

                FieldType.ASSET -> {
                    checkAsset(value, path)
                }

                FieldType.LIST -> {
                    checkList(field, value, path)
                }

                FieldType.OBJECT -> {
                    checkNested(field, value, path)
                }
            }
        }

        private fun checkTextField(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
            isItem: Boolean,
        ) {
            if (field.localized) {
                checkLocalized(field, value, path)
            } else {
                checkText(field, value, path, mustNotBeBlank = field.required || isItem)
            }
        }

        private fun checkText(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
            mustNotBeBlank: Boolean,
        ) {
            if (!value.isString) return fail(path, "must be a string")
            val text = value.stringValue()
            if (text.isBlank()) {
                if (publishing && mustNotBeBlank) fail(path, "must not be blank")
                return
            }
            checkLength(field, text, path)
            field.pattern?.let { if (!Regex(it).matches(text)) fail(path, "must match $it") }
        }

        private fun checkLocalized(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
        ) {
            if (!value.isObject) return fail(path, "must be an object keyed by locale ${locales.supported}")
            val plain = field.copy(localized = false)
            value.properties().forEach { (locale, text) ->
                when {
                    !locales.isSupported(
                        locale,
                    ) -> fail("$path.$locale", "is not a supported locale ${locales.supported}")

                    text.isNull -> Unit

                    else -> checkText(plain, text, "$path.$locale", mustNotBeBlank = false)
                }
            }
            if (publishing && field.required) {
                val fallback = value.get(locales.default)
                if (fallback == null || !fallback.isString || fallback.stringValue().isBlank()) {
                    fail("$path.${locales.default}", "is required (default locale)")
                }
            }
        }

        private fun checkLength(
            field: FieldDefinition,
            text: String,
            path: String,
        ) {
            val length = text.codePointCount(0, text.length)
            field.effectiveMaxLength?.let { if (length > it) fail(path, "must be at most $it characters") }
            field.minLength?.let { if (publishing && length < it) fail(path, "must be at least $it characters") }
        }

        private fun checkNumber(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
            integral: Boolean,
        ) {
            if (!value.isNumber) return fail(path, "must be a number")
            if (integral &&
                !(value.isIntegralNumber && value.canConvertToLong())
            ) {
                return fail(path, "must be an integer")
            }
            val number = value.decimalValue()
            field.min?.let { if (number < it) fail(path, "must be greater than or equal to $it") }
            field.max?.let { if (number > it) fail(path, "must be less than or equal to $it") }
        }

        private fun checkFormatted(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
            message: String,
            accepts: (String) -> Boolean,
        ) {
            if (!value.isString) return fail(path, "must be a string")
            val text = value.stringValue()
            if (text.isEmpty()) {
                if (publishing && field.required) fail(path, "is required")
                return
            }
            field.effectiveMaxLength?.let { if (text.length > it) return fail(path, "must be at most $it characters") }
            if (!accepts(text)) fail(path, message)
        }

        private fun checkReference(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
        ) {
            val id =
                value.takeIf { it.isString }?.let { parseUuid(it.stringValue()) }
                    ?: return fail(path, "must be an entry id")
            if (!publishing) return
            val blueprint = references[id] ?: return fail(path, "refers to a missing entry")
            if (blueprint !in field.blueprints.orEmpty()) fail(path, "must refer to ${field.blueprints}")
        }

        private fun checkAsset(
            value: JsonNode,
            path: String,
        ) {
            if (!value.isObject) return fail(path, "must be an object with an asset id")
            value.propertyNames().filterNot { it == ASSET_ID || it == ASSET_ALT }.forEach {
                fail(
                    "$path.$it",
                    "is not allowed",
                )
            }
            val id = value.get(ASSET_ID)?.takeIf { it.isString }?.let { parseUuid(it.stringValue()) }
            if (id == null) return fail("$path.$ASSET_ID", "must be an asset id")
            value.get(ASSET_ALT)?.takeIf { !it.isNull }?.let { checkLocalized(ALT, it, "$path.$ASSET_ALT") }
            if (publishing && id !in assets) fail("$path.$ASSET_ID", "refers to a missing asset")
        }

        private fun checkList(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
        ) {
            if (!value.isArray) return fail(path, "must be an array")
            val size = value.size()
            field.maxItems?.let { if (size > it) fail(path, "must have at most $it items") }
            if (size > MAX_LIST_SIZE) return fail(path, "must have at most $MAX_LIST_SIZE items")
            if (publishing) {
                val min = maxOf(field.minItems ?: 0, if (field.required) 1 else 0)
                if (size < min) fail(path, "must have at least $min items")
            }
            val item = checkNotNull(field.item)
            value.values().forEachIndexed {
                index,
                element,
                ->
                checkValue(item, element, "$path[$index]", isItem = true)
            }
        }

        private fun checkNested(
            field: FieldDefinition,
            value: JsonNode,
            path: String,
        ) {
            if (value.isObject) {
                checkObject(
                    checkNotNull(field.fields),
                    value,
                    path,
                )
            } else {
                fail(path, "must be an object")
            }
        }

        private fun fail(
            path: String,
            reason: String,
        ) {
            if (violations.size < MAX_VIOLATIONS) violations += FieldViolation(path, reason)
        }
    }

    private companion object {
        const val ROOT = "content"
        const val ASSET_ID = "asset"
        const val ASSET_ALT = "alt"
        const val MAX_CONTENT_LENGTH = 512 * 1024
        const val MAX_LIST_SIZE = 1_000
        const val MAX_VIOLATIONS = 100
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val ALT = FieldDefinition(type = FieldType.STRING, localized = true, maxLength = 300)

        fun parseUuid(value: String): UUID? =
            runCatching { UUID.fromString(value) }.getOrNull()?.takeIf {
                it.toString() ==
                    value.lowercase()
            }

        fun isUrl(value: String): Boolean {
            // Browsers read "/\host" as "//host", so a second slash of either kind is a scheme-relative URL.
            if (value.startsWith("/")) return value.getOrNull(1) != '/' && value.getOrNull(1) != '\\'
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            return uri.scheme == "https" && !uri.host.isNullOrBlank()
        }

        fun isDate(value: String): Boolean =
            try {
                LocalDate.parse(value)
                true
            } catch (_: DateTimeParseException) {
                false
            }

        fun isDateTime(value: String): Boolean =
            try {
                OffsetDateTime.parse(value)
                true
            } catch (_: DateTimeParseException) {
                false
            }
    }
}

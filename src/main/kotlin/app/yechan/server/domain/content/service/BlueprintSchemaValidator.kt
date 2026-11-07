package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.global.error.FieldViolation
import org.springframework.stereotype.Component
import java.util.regex.PatternSyntaxException

@Component
class BlueprintSchemaValidator {
    fun validate(
        key: String,
        name: String,
        description: String?,
        fields: List<FieldDefinition>,
        knownBlueprints: Set<String>,
    ): List<FieldViolation> {
        val violations = mutableListOf<FieldViolation>()
        if (!BLUEPRINT_KEY.matches(key)) {
            violations +=
                FieldViolation("key", "must be kebab-case, start with a letter, max 64")
        }
        if (name.isBlank() || name.length > 100) violations += FieldViolation("name", "must be 1-100 characters")
        if ((description?.length ?: 0) >
            500
        ) {
            violations += FieldViolation("description", "must be at most 500 characters")
        }
        if (fields.isEmpty()) violations += FieldViolation("fields", "must define at least one field")
        val context = Context(knownBlueprints + key, violations)
        checkFields(fields, "fields", depth = 1, context)
        if (context.count >
            MAX_FIELDS
        ) {
            violations += FieldViolation("fields", "must define at most $MAX_FIELDS fields in total")
        }
        return violations
    }

    private fun checkFields(
        fields: List<FieldDefinition>,
        path: String,
        depth: Int,
        context: Context,
    ) {
        if (depth > MAX_DEPTH) {
            context.violations += FieldViolation(path, "must not nest deeper than $MAX_DEPTH levels")
            return
        }
        fields
            .groupBy { it.key }
            .filterValues { it.size > 1 }
            .keys
            .forEach { context.violations += FieldViolation(path, "duplicate field key '$it'") }
        fields.forEachIndexed { index, field -> checkField(field, "$path[$index]", depth, context, isItem = false) }
    }

    private fun checkField(
        field: FieldDefinition,
        path: String,
        depth: Int,
        context: Context,
        isItem: Boolean,
    ) {
        context.count++
        val v = context.violations
        val type = field.type
        if (!isItem) {
            if (!FIELD_KEY.matches(field.key)) {
                v +=
                    FieldViolation("$path.key", "must be camelCase, start with a letter, max 64")
            }
            if (field.label.isBlank() ||
                field.label.length > 100
            ) {
                v += FieldViolation("$path.label", "must be 1-100 characters")
            }
        }
        if ((field.description?.length ?: 0) >
            500
        ) {
            v += FieldViolation("$path.description", "must be at most 500 characters")
        }
        if (field.localized &&
            !type.localizable
        ) {
            v += FieldViolation("$path.localized", "is only allowed for string, text and markdown")
        }

        val lengthApplies = type.lengthCap != null
        disallow(field.minLength != null && !lengthApplies, "$path.minLength", v)
        disallow(field.maxLength != null && !lengthApplies, "$path.maxLength", v)
        if (lengthApplies) {
            val cap = checkNotNull(type.lengthCap)
            val min = field.minLength ?: 0
            val max = field.maxLength ?: cap
            if (min < 0 || max < 1 || min > max ||
                max > cap
            ) {
                v += FieldViolation("$path.maxLength", "must satisfy 0 <= minLength <= maxLength <= $cap")
            }
        }

        val numeric = type == FieldType.NUMBER || type == FieldType.INTEGER
        disallow(field.min != null && !numeric, "$path.min", v)
        disallow(field.max != null && !numeric, "$path.max", v)
        if (field.min != null && field.max != null &&
            field.min > field.max
        ) {
            v += FieldViolation("$path.max", "must be greater than or equal to min")
        }

        val patternApplies = type == FieldType.STRING || type == FieldType.TEXT
        disallow(field.pattern != null && !patternApplies, "$path.pattern", v)
        field.pattern?.takeIf { patternApplies }?.let { pattern ->
            if (pattern.length > 500) {
                v += FieldViolation("$path.pattern", "must be at most 500 characters")
            } else {
                try {
                    Regex(pattern)
                } catch (_: PatternSyntaxException) {
                    v += FieldViolation("$path.pattern", "is not a valid regular expression")
                }
            }
        }

        if (type == FieldType.SELECT) {
            val options = field.options
            if (options.isNullOrEmpty() || options.size > 100) {
                v += FieldViolation("$path.options", "must define 1-100 options")
            } else if (options.any { it.isBlank() || it.length > 100 } || options.toSet().size != options.size) {
                v += FieldViolation("$path.options", "must be unique and 1-100 characters each")
            }
        } else {
            disallow(field.options != null, "$path.options", v)
        }

        if (type == FieldType.REFERENCE) {
            val targets = field.blueprints
            if (targets.isNullOrEmpty() || targets.size > 20) {
                v += FieldViolation("$path.blueprints", "must list 1-20 blueprint keys")
            } else {
                targets.filterNot { it in context.knownBlueprints }.forEach {
                    v += FieldViolation("$path.blueprints", "unknown blueprint '$it'")
                }
            }
        } else {
            disallow(field.blueprints != null, "$path.blueprints", v)
        }

        if (type == FieldType.LIST) {
            val min = field.minItems ?: 0
            val max = field.maxItems ?: MAX_ITEMS
            if (min < 0 || min > max ||
                max > MAX_ITEMS
            ) {
                v +=
                    FieldViolation("$path.maxItems", "must satisfy 0 <= minItems <= maxItems <= $MAX_ITEMS")
            }
            val item = field.item
            when {
                item == null -> {
                    v += FieldViolation("$path.item", "is required for list")
                }

                item.type == FieldType.LIST -> {
                    v +=
                        FieldViolation("$path.item.type", "list items cannot be lists; wrap them in an object")
                }

                else -> {
                    checkField(item, "$path.item", depth + 1, context, isItem = true)
                }
            }
        } else {
            disallow(field.minItems != null, "$path.minItems", v)
            disallow(field.maxItems != null, "$path.maxItems", v)
            disallow(field.item != null, "$path.item", v)
        }

        if (type == FieldType.OBJECT) {
            val nested = field.fields
            if (nested.isNullOrEmpty()) {
                v += FieldViolation("$path.fields", "is required for object")
            } else {
                checkFields(nested, "$path.fields", depth + 1, context)
            }
        } else {
            disallow(field.fields != null, "$path.fields", v)
        }
    }

    private fun disallow(
        condition: Boolean,
        path: String,
        violations: MutableList<FieldViolation>,
    ) {
        if (condition) violations += FieldViolation(path, "is not allowed for this field type")
    }

    private class Context(
        val knownBlueprints: Set<String>,
        val violations: MutableList<FieldViolation>,
        var count: Int = 0,
    )

    companion object {
        val BLUEPRINT_KEY = Regex("^(?=.{1,64}$)[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
        private val FIELD_KEY = Regex("^[a-z][a-zA-Z0-9]{0,63}$")
        private const val MAX_DEPTH = 4
        private const val MAX_FIELDS = 200
        private const val MAX_ITEMS = 1_000
    }
}

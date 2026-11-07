package app.yechan.server.domain.content.entity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import java.math.BigDecimal

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldDefinition(
    val key: String = "",
    val label: String = "",
    val type: FieldType,
    val description: String? = null,
    val required: Boolean = false,
    val localized: Boolean = false,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val min: BigDecimal? = null,
    val max: BigDecimal? = null,
    val pattern: String? = null,
    val options: List<String>? = null,
    val blueprints: List<String>? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val item: FieldDefinition? = null,
    val fields: List<FieldDefinition>? = null,
) {
    val effectiveMaxLength: Int? get() = type.lengthCap?.let { cap -> maxLength?.coerceAtMost(cap) ?: cap }

    fun referencedBlueprints(): Set<String> =
        buildSet {
            if (type == FieldType.REFERENCE) addAll(blueprints.orEmpty())
            item?.let { addAll(it.referencedBlueprints()) }
            fields.orEmpty().forEach { addAll(it.referencedBlueprints()) }
        }
}

enum class FieldType(
    val lengthCap: Int? = null,
    val localizable: Boolean = false,
) {
    STRING(lengthCap = 500, localizable = true),
    TEXT(lengthCap = 10_000, localizable = true),
    MARKDOWN(lengthCap = 100_000, localizable = true),
    NUMBER,
    INTEGER,
    BOOLEAN,
    URL(lengthCap = 2_048),
    EMAIL(lengthCap = 254),
    DATE,
    DATETIME,
    SELECT,
    REFERENCE,
    ASSET,
    LIST,
    OBJECT,
    ;

    @JsonValue
    fun json(): String = name.lowercase()

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String): FieldType =
            entries.firstOrNull { it.json() == value } ?: throw IllegalArgumentException("Unknown field type: $value")
    }
}

package app.yechan.server.domain.content.entity

import app.yechan.server.global.common.entity.BaseEntity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.ColumnTransformer

@Entity
@Table(name = "blueprint")
class Blueprint(
    @Column(name = "key", nullable = false, unique = true, length = 64, updatable = false)
    val key: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var kind: BlueprintKind,
    name: String,
    description: String?,
    fields: List<FieldDefinition>,
) : BaseEntity() {
    @Column(nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(length = 500)
    var description: String? = description
        protected set

    @Convert(converter = FieldDefinitionsConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(nullable = false, columnDefinition = "jsonb")
    var fields: List<FieldDefinition> = fields
        protected set

    @Version
    @Column(nullable = false)
    var revision: Long = 0
        protected set

    fun revise(
        name: String,
        description: String?,
        kind: BlueprintKind,
        fields: List<FieldDefinition>,
    ) {
        this.name = name
        this.description = description
        this.kind = kind
        this.fields = fields
    }
}

enum class BlueprintKind {
    COLLECTION,

    SINGLETON,
    ;

    @JsonValue
    fun json(): String = name.lowercase()

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String): BlueprintKind =
            entries.firstOrNull { it.json() == value }
                ?: throw IllegalArgumentException("Unknown blueprint kind: $value")
    }
}

package app.yechan.server.domain.content

import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.domain.content.service.BlueprintSchemaValidator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlueprintSchemaValidatorTest {
    private val validator = BlueprintSchemaValidator()

    private fun violations(
        vararg fields: FieldDefinition,
        key: String = "project",
        known: Set<String> = emptySet(),
    ) = validator.validate(key, "Project", null, fields.toList(), known)

    @Test
    fun `올바른 정의는 통과한다`() {
        val result =
            violations(
                FieldDefinition("title", "Title", FieldType.STRING, required = true, maxLength = 120),
                FieldDefinition("summary", "Summary", FieldType.TEXT, localized = true),
                FieldDefinition("year", "Year", FieldType.INTEGER, min = BigDecimal(1900), max = BigDecimal(2100)),
                FieldDefinition("status", "Status", FieldType.SELECT, options = listOf("live", "archived")),
                FieldDefinition("related", "Related", FieldType.REFERENCE, blueprints = listOf("project")),
                FieldDefinition(
                    "links",
                    "Links",
                    FieldType.LIST,
                    maxItems = 10,
                    item =
                        FieldDefinition(
                            type = FieldType.OBJECT,
                            fields = listOf(FieldDefinition("href", "Href", FieldType.URL, required = true)),
                        ),
                ),
            )
        assertEquals(emptyList(), result)
    }

    @Test
    fun `키 형식과 중복을 검사한다`() {
        val result =
            violations(
                FieldDefinition("Title", "Title", FieldType.STRING),
                FieldDefinition("dup", "A", FieldType.STRING),
                FieldDefinition("dup", "B", FieldType.STRING),
                key = "Bad_Key",
            )
        val paths = result.map { it.field }
        assertTrue("key" in paths)
        assertTrue("fields[0].key" in paths)
        assertTrue(result.any { it.reason.contains("duplicate field key 'dup'") })
    }

    @Test
    fun `타입에 맞지 않는 제약은 거부한다`() {
        val result =
            violations(
                FieldDefinition(
                    "count",
                    "Count",
                    FieldType.NUMBER,
                    localized = true,
                    maxLength = 10,
                    options = listOf("a"),
                ),
                FieldDefinition(
                    "title",
                    "Title",
                    FieldType.STRING,
                    min = BigDecimal.ONE,
                    item = FieldDefinition(type = FieldType.STRING),
                ),
            )
        val paths = result.map { it.field }.toSet()
        assertEquals(
            setOf("fields[0].localized", "fields[0].maxLength", "fields[0].options", "fields[1].min", "fields[1].item"),
            paths,
        )
    }

    @Test
    fun `종류별 필수 구성 요소와 범위를 검사한다`() {
        val result =
            violations(
                FieldDefinition("status", "Status", FieldType.SELECT),
                FieldDefinition("related", "Related", FieldType.REFERENCE, blueprints = listOf("missing")),
                FieldDefinition("tags", "Tags", FieldType.LIST),
                FieldDefinition("meta", "Meta", FieldType.OBJECT),
                FieldDefinition("title", "Title", FieldType.STRING, maxLength = 100_000),
                FieldDefinition("code", "Code", FieldType.STRING, pattern = "(unclosed"),
                FieldDefinition("score", "Score", FieldType.NUMBER, min = BigDecimal.TEN, max = BigDecimal.ONE),
                FieldDefinition("nested", "Nested", FieldType.LIST, item = FieldDefinition(type = FieldType.LIST)),
            )
        val paths = result.map { it.field }.toSet()
        assertEquals(
            setOf(
                "fields[0].options",
                "fields[1].blueprints",
                "fields[2].item",
                "fields[3].fields",
                "fields[4].maxLength",
                "fields[5].pattern",
                "fields[6].max",
                "fields[7].item.type",
            ),
            paths,
        )
    }

    @Test
    fun `참조는 이미 있는 Blueprint나 자기 자신만 가리킬 수 있다`() {
        val field = FieldDefinition("author", "Author", FieldType.REFERENCE, blueprints = listOf("person"))
        assertTrue(violations(field).isNotEmpty())
        assertEquals(emptyList(), violations(field, known = setOf("person")))
    }

    @Test
    fun `중첩 깊이를 제한한다`() {
        fun nest(depth: Int): FieldDefinition =
            if (depth == 0) {
                FieldDefinition("leaf", "Leaf", FieldType.STRING)
            } else {
                FieldDefinition("level$depth", "Level", FieldType.OBJECT, fields = listOf(nest(depth - 1)))
            }
        assertEquals(emptyList(), violations(nest(3)))
        assertTrue(violations(nest(4)).any { it.reason.contains("deeper") })
    }
}

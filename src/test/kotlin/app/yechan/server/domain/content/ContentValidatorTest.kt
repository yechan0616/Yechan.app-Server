package app.yechan.server.domain.content

import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.domain.content.service.ContentValidator
import app.yechan.server.domain.content.service.ContentValidator.Mode
import app.yechan.server.global.common.json.Json
import app.yechan.server.global.i18n.ContentLocaleProperties
import app.yechan.server.global.i18n.ContentLocales
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

class ContentValidatorTest {
    private val validator = ContentValidator(ContentLocales(ContentLocaleProperties(listOf("en", "ko"), "en")))

    private val fields =
        listOf(
            FieldDefinition("title", "Title", FieldType.STRING, required = true, maxLength = 20),
            FieldDefinition("summary", "Summary", FieldType.TEXT, required = true, localized = true),
            FieldDefinition("year", "Year", FieldType.INTEGER, min = BigDecimal(1900), max = BigDecimal(2100)),
            FieldDefinition("repository", "Repository", FieldType.URL),
            FieldDefinition("status", "Status", FieldType.SELECT, options = listOf("live", "archived")),
            FieldDefinition(
                "skills",
                "Skills",
                FieldType.LIST,
                minItems = 1,
                maxItems = 3,
                item = FieldDefinition(type = FieldType.STRING),
            ),
            FieldDefinition("project", "Project", FieldType.REFERENCE, blueprints = listOf("project")),
            FieldDefinition("launchedOn", "Launched on", FieldType.DATE),
        )

    private fun check(
        content: String,
        mode: Mode,
        references: Map<UUID, String> = emptyMap(),
    ) = validator.validate(fields, Json.mapper.readTree(content), mode, references).associate { it.field to it.reason }

    @Test
    fun `초안은 비어 있어도 된다`() {
        assertEquals(emptyMap(), check("{}", Mode.DRAFT))
    }

    @Test
    fun `발행할 때는 필수 값과 최소 개수를 요구한다`() {
        val result = check("""{"summary":{"ko":"한국어만"},"skills":[]}""", Mode.PUBLISH)
        assertEquals(setOf("content.title", "content.summary.en", "content.skills"), result.keys)
    }

    @Test
    fun `초안에서도 형태는 검사한다`() {
        val result =
            check(
                """
                {"title":"this title is far too long","summary":{"ja":"x"},"year":1800,"repository":"http://insecure.example",
                 "status":"draft","skills":["a","b","c","d"],"project":"not-a-uuid","launchedOn":"2026-13-01","extra":true}
                """,
                Mode.DRAFT,
            )
        assertEquals(
            setOf(
                "content.title",
                "content.summary.ja",
                "content.year",
                "content.repository",
                "content.status",
                "content.skills",
                "content.project",
                "content.launchedOn",
                "content.extra",
            ),
            result.keys,
        )
    }

    @Test
    fun `참조는 발행할 때 대상의 존재와 Blueprint를 확인한다`() {
        val project = UUID.randomUUID()
        val history = UUID.randomUUID()
        val base = """"title":"Toast","summary":{"en":"Summary"},"skills":["Kotlin"]"""
        assertEquals(emptyMap(), check("""{$base,"project":"$project"}""", Mode.PUBLISH, mapOf(project to "project")))
        assertEquals(setOf("content.project"), check("""{$base,"project":"$project"}""", Mode.PUBLISH).keys)
        val wrongTarget = check("""{$base,"project":"$history"}""", Mode.PUBLISH, mapOf(history to "history"))
        assertEquals(setOf("content.project"), wrongTarget.keys)
    }

    @Test
    fun `내부 경로와 https 링크만 URL로 허용한다`() {
        val base = """"title":"T","summary":{"en":"S"},"skills":["K"]"""
        listOf("https://github.com/yechan", "/projects/toast").forEach {
            assertEquals(emptyMap(), check("""{$base,"repository":"$it"}""", Mode.PUBLISH), it)
        }
        listOf(
            "javascript:alert(1)",
            "//evil.example",
            "/\\evil.example",
            "/\t/evil.example",
            "/\n/evil.example",
            "https://ok.example/\r",
            "ftp://files.example",
        ).forEach {
            val encoded = Json.mapper.writeValueAsString(it)
            assertEquals(setOf("content.repository"), check("""{$base,"repository":$encoded}""", Mode.PUBLISH).keys, it)
        }
    }

    @Test
    fun `내용은 객체여야 한다`() {
        assertEquals(setOf("content"), check("[]", Mode.DRAFT).keys)
    }

    @Test
    fun `markdown 안의 asset 참조는 초안에서 형식을, 발행에서 존재를 검사한다`() {
        val body = listOf(FieldDefinition("body", "Body", FieldType.MARKDOWN, localized = true))
        val known = UUID.randomUUID()
        val missing = UUID.randomUUID()

        fun check(
            markdown: String,
            mode: Mode,
        ) = validator
            .validate(
                body,
                Json.mapper.readTree("""{"body":{"en":${Json.mapper.writeValueAsString(markdown)}}}"""),
                mode,
                assets = setOf(known),
            ).map { it.field to it.reason }

        assertEquals(emptyList(), check("![Cover](asset:$known) and [a link](https://yechan.app)", Mode.PUBLISH))
        assertEquals(emptyList(), check("![Cover](asset:$missing)", Mode.DRAFT))
        assertEquals(
            listOf("content.body.en" to "refers to a missing asset 'asset:$missing'"),
            check("![Cover](asset:$missing)", Mode.PUBLISH),
        )
        assertEquals(
            listOf("content.body.en" to "contains an invalid asset reference 'asset:cover'"),
            check("![Cover](asset:cover)", Mode.DRAFT),
        )
        assertEquals(emptyList(), check("the asset: is mentioned in prose, not as a destination", Mode.PUBLISH))
    }

    @Test
    fun `asset id를 asset 필드와 markdown 본문에서 함께 모은다`() {
        val fromField = UUID.randomUUID()
        val fromMarkdown = UUID.randomUUID()
        val nested = UUID.randomUUID()
        val fields =
            listOf(
                FieldDefinition("cover", "Cover", FieldType.ASSET),
                FieldDefinition("body", "Body", FieldType.MARKDOWN, localized = true),
                FieldDefinition("notes", "Notes", FieldType.LIST, item = FieldDefinition(type = FieldType.MARKDOWN)),
            )
        val content =
            Json.mapper.readTree(
                """
                {"cover":{"asset":"$fromField"},"body":{"en":"![a](asset:$fromMarkdown)","ko":"![b](asset:bad)"},
                 "notes":["see ![c](asset:$nested)"]}
                """,
            )
        assertEquals(setOf(fromField, fromMarkdown, nested), validator.collectAssets(fields, content))
    }

    @Test
    fun `참조 id를 중첩 구조에서도 모은다`() {
        val id = UUID.randomUUID()
        val target = FieldDefinition("target", "Target", FieldType.REFERENCE, blueprints = listOf("project"))
        val nested =
            listOf(
                FieldDefinition(
                    "links",
                    "Links",
                    FieldType.LIST,
                    item =
                        FieldDefinition(
                            type = FieldType.OBJECT,
                            fields = listOf(target),
                        ),
                ),
            )
        val content = Json.mapper.readTree("""{"links":[{"target":"$id"},{"target":"bad"}]}""")
        assertEquals(setOf(id), validator.collectReferences(nested, content))
    }
}

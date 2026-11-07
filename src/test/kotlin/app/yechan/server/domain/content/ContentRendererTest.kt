package app.yechan.server.domain.content

import app.yechan.server.domain.content.entity.FieldDefinition
import app.yechan.server.domain.content.entity.FieldType
import app.yechan.server.domain.content.service.ContentRenderer
import app.yechan.server.domain.content.service.ContentRenderer.LinkedEntry
import app.yechan.server.global.common.json.Json
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentRendererTest {
    private val renderer = ContentRenderer()
    private val linked = UUID.randomUUID()
    private val hidden = UUID.randomUUID()
    private val fields =
        listOf(
            FieldDefinition("title", "Title", FieldType.STRING),
            FieldDefinition("summary", "Summary", FieldType.TEXT, localized = true),
            FieldDefinition("project", "Project", FieldType.REFERENCE, blueprints = listOf("project")),
            FieldDefinition("draftOnly", "Draft only", FieldType.REFERENCE, blueprints = listOf("project")),
            FieldDefinition(
                "points",
                "Points",
                FieldType.LIST,
                item = FieldDefinition(type = FieldType.TEXT, localized = true),
            ),
            FieldDefinition("missing", "Missing", FieldType.STRING),
        )
    private val content =
        Json.mapper.readTree(
            """
            {"title":"Toast","summary":{"en":"English","ko":"한국어"},"project":"$linked","draftOnly":"$hidden",
             "points":[{"en":"One","ko":"하나"},{"en":"Two","ko":" "}],"removedField":"ignored"}
            """,
        )

    private fun render(locale: String) =
        renderer.render(fields, content, locale, "en", mapOf(linked to LinkedEntry(linked, "project", "toast")))

    @Test
    fun `요청 언어를 고르고 비어 있으면 기본 언어로 대체한다`() {
        val ko = render("ko")
        assertEquals("한국어", ko["summary"].stringValue())
        assertEquals(listOf("하나", "Two"), ko["points"].values().map { it.stringValue() })
        assertEquals("English", render("en")["summary"].stringValue())
    }

    @Test
    fun `참조는 발행된 대상만 펼친다`() {
        val result = render("en")
        assertEquals("toast", result["project"]["slug"].stringValue())
        assertEquals("project", result["project"]["blueprint"].stringValue())
        assertTrue(result["draftOnly"].isNull)
    }

    @Test
    fun `정의된 필드만 같은 모양으로 내보낸다`() {
        val result = render("en")
        assertEquals(fields.map { it.key }.toSet(), result.propertyNames().toSet())
        assertTrue(result["missing"].isNull)
    }
}

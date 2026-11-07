package app.yechan.server.domain.content

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.service.StudioAuthentication
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import java.util.UUID

class ContentFeaturesTest : IntegrationTest() {
    private val administrator =
        StudioAuthentication(AdministratorPrincipal(UUID.randomUUID(), "yechan", "Yechan", UUID.randomUUID()))

    @Test
    fun `공개 목록은 필드 값으로 거르고 필드 기준으로 정렬한다`() {
        defineHistory()
        listOf(2024 to false, 2026 to true, 2025 to true, 2026 to false).forEachIndexed { index, (year, home) ->
            publish(
                create("history", "e$index", """{"year":$year,"title":"T$index","status":"done","showOnHome":$home}"""),
            )
        }

        public("/collections/history?locale=en&filter[showOnHome]=true&sort=-year").andExpect {
            jsonPath("$.totalItems") { value(2) }
            jsonPath("$.items[0].content.year") { value(2026) }
            jsonPath("$.items[1].content.year") { value(2025) }
        }
        public("/collections/history?locale=en&filter[year]=2026").andExpect { jsonPath("$.totalItems") { value(2) } }
        public("/collections/history?locale=en&sort=year&size=1").andExpect {
            jsonPath("$.items[0].content.year") { value(2024) }
            jsonPath("$.totalPages") { value(4) }
        }
    }

    @Test
    fun `지원하지 않는 필터와 정렬은 이유와 함께 거절한다`() {
        defineHistory()
        public(
            "/collections/history?filter[summary]=x&filter[showOnHome]=maybe&filter[status]=nope&sort=summary",
        ).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ENTRY_QUERY_INVALID") }
            jsonPath("$.errors.length()") { value(4) }
        }
        public("/collections/history?filter[year]=1;drop table entry").andExpect {
            jsonPath("$.code") { value("ENTRY_QUERY_INVALID") }
        }
    }

    @Test
    fun `순서를 한 번에 바꾸고 revision은 그대로 둔다`() {
        defineHistory()
        val a = publish(create("history", "a", """{"year":2026,"title":"A"}"""))
        val b = publish(create("history", "b", """{"year":2026,"title":"B"}"""))
        val c = publish(create("history", "c", """{"year":2026,"title":"C"}"""))

        studio {
            put(
                "/api/v1/studio/blueprints/history/entries/order",
            ) { body("""{"entries":["${c.id}","${a.id}","${b.id}"]}""") }
        }.andExpect { status { isNoContent() } }

        public("/collections/history?locale=en").andExpect {
            jsonPath("$.items[0].slug") { value("c") }
            jsonPath("$.items[1].slug") { value("a") }
            jsonPath("$.items[2].slug") { value("b") }
        }
        studio {
            get(
                "/api/v1/studio/entries/${a.id}",
            ) { body("") }
        }.andExpect { jsonPath("$.revision") { value(a.revision) } }
    }

    @Test
    fun `순서 변경은 같은 Blueprint의 서로 다른 entry만 받는다`() {
        defineHistory()
        val a = create("history", "a", """{"year":2026,"title":"A"}""")
        listOf("""["${a.id}","${a.id}"]""", """["${a.id}","${UUID.randomUUID()}"]""").forEach { ids ->
            studio { put("/api/v1/studio/blueprints/history/entries/order") { body("""{"entries":$ids}""") } }
                .andExpect { jsonPath("$.code") { value("ENTRY_ORDER_INVALID") } }
        }
    }

    @Test
    fun `미리보기 링크로 발행 전 초안을 볼 수 있고 캐시되지 않는다`() {
        defineHistory()
        val entry = publish(create("history", "draft", """{"year":2026,"title":"Published"}"""))
        studio {
            put("/api/v1/studio/entries/${entry.id}") {
                body("""{"slug":"draft","content":{"year":2026,"title":"Edited"},"revision":${entry.revision}}""")
            }
        }.andExpect { status { isOk() } }

        val token =
            json(
                studio { post("/api/v1/studio/entries/${entry.id}/preview") { body("") } },
            )["token"].stringValue()

        public("/preview/$token?locale=en").andExpect {
            status { isOk() }
            jsonPath("$.content.title") { value("Edited") }
            header { string(HttpHeaders.CACHE_CONTROL, "no-store") }
            header { string("X-Robots-Tag", "noindex") }
        }
        public("/collections/history/draft?locale=en").andExpect { jsonPath("$.content.title") { value("Published") } }
    }

    @Test
    fun `위조되거나 형식이 틀린 미리보기 토큰은 거절한다`() {
        defineHistory()
        val entry = create("history", "x", """{"year":2026,"title":"X"}""")
        val token =
            json(
                studio { post("/api/v1/studio/entries/${entry.id}/preview") { body("") } },
            )["token"].stringValue()
        val tampered = token.dropLast(2) + if (token.endsWith("AA")) "BB" else "AA"

        listOf(tampered, "garbage", "a.b").forEach {
            public("/preview/$it").andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("PREVIEW_TOKEN_INVALID") }
            }
        }
        mockMvc.post("/api/v1/studio/entries/${entry.id}/preview").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `API 명세는 공개와 studio 그룹으로 나뉜다`() {
        mockMvc.get("/v3/api-docs/public").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/public/collections/{key}']") { exists() }
        }
        mockMvc.get("/v3/api-docs/studio").andExpect {
            jsonPath("$.paths['/api/v1/studio/assets']") { exists() }
            jsonPath("$.components.securitySchemes.session.in") { value("cookie") }
        }
    }

    private data class Saved(
        val id: String,
        val revision: Long,
    )

    private fun defineHistory() {
        studio {
            post("/api/v1/studio/blueprints") {
                body(
                    """{"key":"history","name":"History","kind":"collection","fields":[
                        {"key":"year","label":"Year","type":"integer","required":true},
                        {"key":"title","label":"Title","type":"string"},
                        {"key":"summary","label":"Summary","type":"text","localized":true},
                        {"key":"status","label":"Status","type":"select","options":["done","planned"]},
                        {"key":"showOnHome","label":"Home","type":"boolean"}]}""",
                )
            }
        }.andExpect { status { isCreated() } }
    }

    private fun create(
        blueprint: String,
        slug: String,
        content: String,
    ): Saved =
        saved(
            studio {
                post("/api/v1/studio/blueprints/$blueprint/entries") { body("""{"slug":"$slug","content":$content}""") }
            },
        )

    private fun publish(entry: Saved): Saved =
        saved(
            studio {
                put(
                    "/api/v1/studio/entries/${entry.id}/publication",
                ) { body("""{"revision":${entry.revision}}""") }
            },
        )

    private fun saved(result: ResultActionsDsl): Saved {
        val node = json(result.andExpect { status { is2xxSuccessful() } })
        return Saved(node["id"].stringValue(), node["revision"].longValue())
    }

    private fun studio(request: org.springframework.test.web.servlet.MockMvc.() -> ResultActionsDsl): ResultActionsDsl =
        mockMvc.request()

    private fun public(path: String): ResultActionsDsl = mockMvc.get("/api/v1/public$path")

    private fun json(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private fun MockHttpServletRequestDsl.body(content: String) {
        with(authentication(administrator))
        if (content.isNotEmpty()) {
            contentType = MediaType.APPLICATION_JSON
            this.content = content
        }
    }
}

package app.yechan.server.domain.content

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.service.StudioAuthentication
import app.yechan.server.support.IntegrationTest
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import java.util.UUID

class ContentApiTest : IntegrationTest() {
    private val administrator =
        StudioAuthentication(AdministratorPrincipal(UUID.randomUUID(), "yechan", "Yechan", UUID.randomUUID()))

    @Test
    fun `초안은 공개되지 않고 발행해야 공개 API에 나타난다`() {
        definePortfolio()
        val draft = createEntry("project", """{"slug":"toast-acs","content":{"summary":{"en":"Access control"}}}""")

        public("/collections/project?locale=en").andExpect { jsonPath("$.totalItems") { value(0) } }
        studio {
            put(
                "/api/v1/studio/entries/${draft.id}/publication",
            ) { jsonBody("""{"revision":${draft.revision}}""") }
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ENTRY_INVALID") }
            jsonPath("$.errors[*].field") { value(hasItem("content.title")) }
        }

        val complete = updateEntry(draft, """{"slug":"toast-acs","content":$PROJECT_CONTENT}""")
        val published = publish(complete)
        published.json["status"].stringValue().let { check(it == "PUBLISHED") { it } }

        public("/collections/project?locale=ko").andExpect {
            status { isOk() }
            jsonPath("$.items[0].slug") { value("toast-acs") }
            jsonPath("$.items[0].content.title") { value("Toast ACS") }
            jsonPath("$.items[0].content.summary") { value("출입 관리 서비스") }
            jsonPath("$.items[0].content.skills[0]") { value("Kotlin") }
            jsonPath("$.items[0].content.repositoryUrl") { value(null) }
        }
    }

    @Test
    fun `발행 후 수정한 초안은 다시 발행하기 전까지 공개되지 않는다`() {
        definePortfolio()
        val entry = publish(createEntry("project", """{"slug":"toast-acs","content":$PROJECT_CONTENT}"""))
        val edited =
            updateEntry(
                entry,
                """{"slug":"toast-acs-v2","content":${PROJECT_CONTENT.replace("Toast ACS", "Toast ACS 2")}}""",
            )
        check(edited.json["status"].stringValue() == "CHANGED")

        public(
            "/collections/project/toast-acs?locale=en",
        ).andExpect { jsonPath("$.content.title") { value("Toast ACS") } }
        public("/collections/project/toast-acs-v2?locale=en").andExpect { status { isNotFound() } }

        publish(edited)
        public(
            "/collections/project/toast-acs-v2?locale=en",
        ).andExpect { jsonPath("$.content.title") { value("Toast ACS 2") } }
        public("/collections/project/toast-acs?locale=en").andExpect { jsonPath("$.code") { value("ENTRY_NOT_FOUND") } }
    }

    @Test
    fun `언어는 locale 파라미터, Accept-Language, 기본 언어 순으로 고르고 캐시 정책이 달라진다`() {
        definePortfolio()
        publish(createEntry("project", """{"slug":"oneday","content":${PROJECT_CONTENT.replace("출입 관리 서비스", "")}}"""))

        public("/collections/project/oneday?locale=ko").andExpect {
            jsonPath("$.content.summary") { value("Access control service") }
            header { string(HttpHeaders.CONTENT_LANGUAGE, "ko") }
            header { string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("public")) }
        }
        mockMvc
            .get("/api/v1/public/collections/project/oneday") {
                header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9")
            }.andExpect {
                header { string(HttpHeaders.CONTENT_LANGUAGE, "ko") }
                header { string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("private")) }
                match { check(HttpHeaders.ACCEPT_LANGUAGE in it.response.getHeaders(HttpHeaders.VARY)) }
            }
        public(
            "/collections/project/oneday?locale=ja",
        ).andExpect { header { string(HttpHeaders.CONTENT_LANGUAGE, "en") } }
        public("/locales").andExpect {
            jsonPath("$.supported[1]") { value("ko") }
            jsonPath("$.default") { value("en") }
        }
    }

    @Test
    fun `참조는 발행된 대상만 펼쳐지고, 대상이 내려가면 null이 된다`() {
        definePortfolio()
        val project = publish(createEntry("project", """{"slug":"toast-acs","content":$PROJECT_CONTENT}"""))
        val history =
            createEntry(
                "history",
                """{"slug":"2026-hackathon","content":{"year":2026,"title":"Hackathon","description":{"en":"Won"},"project":"${project.id}"}}""",
            )
        publish(history)

        public("/collections/history?locale=en").andExpect {
            jsonPath("$.items[0].content.project.slug") { value("toast-acs") }
            jsonPath("$.items[0].content.project.blueprint") { value("project") }
        }
        studio { authDelete("/api/v1/studio/entries/${project.id}/publication") }.andExpect { status { isOk() } }
        public("/collections/history?locale=en").andExpect { jsonPath("$.items[0].content.project") { value(null) } }
    }

    @Test
    fun `발행 취소는 revision을 보내면 충돌을 검사한다`() {
        definePortfolio()
        val project = publish(createEntry("project", """{"slug":"toast-acs","content":$PROJECT_CONTENT}"""))
        studio {
            delete("/api/v1/studio/entries/${project.id}/publication") {
                jsonBody("""{"revision":${project.revision + 1}}""")
            }
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ENTRY_REVISION_CONFLICT") }
        }
        studio {
            delete("/api/v1/studio/entries/${project.id}/publication") {
                jsonBody("""{"revision":${project.revision}}""")
            }
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DRAFT") }
        }
    }

    @Test
    fun `요청에 모르는 필드가 있으면 조용히 버리지 않고 거절한다`() {
        studio {
            post("/api/v1/studio/blueprints") {
                jsonBody(
                    """{"key":"typo","name":"Typo","kind":"collection",
                        "fields":[{"key":"title","label":"Title","type":"string","maxLenght":5}]}""",
                )
            }
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `참조 대상이 없거나 다른 Blueprint면 발행할 수 없다`() {
        definePortfolio()
        val profile = createEntry("profile", """{"content":{"name":"Yechan"}}""")
        val history =
            createEntry(
                "history",
                """{"slug":"wrong","content":{"year":2026,"description":{"en":"x"},"project":"${profile.id}"}}""",
            )
        studio {
            put(
                "/api/v1/studio/entries/${history.id}/publication",
            ) { jsonBody("""{"revision":${history.revision}}""") }
        }.andExpect { jsonPath("$.errors[0].field") { value("content.project") } }
    }

    @Test
    fun `singleton은 entry가 하나뿐이고 전용 경로로만 공개된다`() {
        definePortfolio()
        publish(
            createEntry(
                "profile",
                """{"content":{"name":"Yechan","links":[{"label":"GitHub","href":"https://github.com/yechan"}]}}""",
            ),
        )

        studio { post("/api/v1/studio/blueprints/profile/entries") { jsonBody("""{"content":{}}""") } }
            .andExpect { jsonPath("$.code") { value("ENTRY_SINGLETON_EXISTS") } }
        public("/singletons/profile?locale=en").andExpect {
            jsonPath("$.content.name") { value("Yechan") }
            jsonPath("$.content.links[0].href") { value("https://github.com/yechan") }
        }
        public("/collections/profile").andExpect { jsonPath("$.code") { value("BLUEPRINT_NOT_FOUND") } }
    }

    @Test
    fun `동시에 수정하면 늦게 저장한 쪽이 충돌을 받는다`() {
        definePortfolio()
        val entry = createEntry("project", """{"slug":"toast","content":{}}""")
        updateEntry(entry, """{"slug":"toast","content":{"title":"First"}}""")
        studio {
            put("/api/v1/studio/entries/${entry.id}") {
                jsonBody("""{"slug":"toast","content":{"title":"Second"},"revision":${entry.revision}}""")
            }
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ENTRY_REVISION_CONFLICT") }
        }
    }

    @Test
    fun `slug는 Blueprint 안에서 유일하고 형식이 정해져 있다`() {
        definePortfolio()
        createEntry("project", """{"slug":"toast","content":{}}""")
        studio { post("/api/v1/studio/blueprints/project/entries") { jsonBody("""{"slug":"toast","content":{}}""") } }
            .andExpect { jsonPath("$.code") { value("ENTRY_SLUG_TAKEN") } }
        studio {
            post(
                "/api/v1/studio/blueprints/project/entries",
            ) { jsonBody("""{"slug":"Toast ACS","content":{}}""") }
        }.andExpect { jsonPath("$.errors[0].field") { value("slug") } }
    }

    @Test
    fun `정의되지 않은 필드나 잘못된 형태는 초안 저장부터 막는다`() {
        definePortfolio()
        studio {
            post("/api/v1/studio/blueprints/project/entries") {
                jsonBody("""{"slug":"x","content":{"title":42,"unknown":true,"summary":"not localized"}}""")
            }
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.length()") { value(3) }
        }
    }

    @Test
    fun `잘못된 Blueprint 정의는 필드별 이유와 함께 거절된다`() {
        studio {
            post("/api/v1/studio/blueprints") {
                jsonBody(
                    """{"key":"Bad Key","name":"Bad","kind":"collection",
                        "fields":[{"key":"count","label":"Count","type":"number","localized":true},
                                  {"key":"kind","label":"Kind","type":"select"}]}""",
                )
            }
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("BLUEPRINT_INVALID") }
            jsonPath("$.errors[*].field") { value(hasItem("key")) }
            jsonPath("$.errors[*].field") { value(hasItem("fields[0].localized")) }
            jsonPath("$.errors[*].field") { value(hasItem("fields[1].options")) }
        }
    }

    @Test
    fun `entry가 있거나 다른 Blueprint가 참조하면 삭제할 수 없다`() {
        definePortfolio()
        studio {
            authDelete("/api/v1/studio/blueprints/project")
        }.andExpect { jsonPath("$.code") { value("BLUEPRINT_IN_USE") } }
        studio { authDelete("/api/v1/studio/blueprints/history") }.andExpect { status { isNoContent() } }
        studio { authDelete("/api/v1/studio/blueprints/project") }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `entry가 있으면 Blueprint 종류를 바꿀 수 없다`() {
        definePortfolio()
        createEntry("project", """{"slug":"toast","content":{}}""")
        val blueprint = studioJson { authGet("/api/v1/studio/blueprints/project") }
        val body =
            """{"name":"Projects","kind":"singleton","fields":${blueprint["fields"]},"revision":${blueprint["revision"]}}"""
        studio { put("/api/v1/studio/blueprints/project") { jsonBody(body) } }
            .andExpect { jsonPath("$.code") { value("BLUEPRINT_KIND_LOCKED") } }
    }

    @Test
    fun `studio API는 관리자 세션이 필요하다`() {
        mockMvc.get("/api/v1/studio/blueprints").andExpect { status { isUnauthorized() } }
        mockMvc
            .post("/api/v1/studio/blueprints") {
                contentType = MediaType.APPLICATION_JSON
                content = PROFILE_BLUEPRINT
            }.andExpect { status { isUnauthorized() } }
    }

    private fun definePortfolio() {
        listOf(PROJECT_BLUEPRINT, HISTORY_BLUEPRINT, PROFILE_BLUEPRINT).forEach { blueprint ->
            studio { post("/api/v1/studio/blueprints") { jsonBody(blueprint) } }.andExpect { status { isCreated() } }
        }
    }

    private data class Saved(
        val json: JsonNode,
    ) {
        val id: String get() = json["id"].stringValue()
        val revision: Long get() = json["revision"].longValue()
        val slug: String? get() = json["slug"]?.takeIf { it.isString }?.stringValue()
    }

    private fun createEntry(
        blueprint: String,
        body: String,
    ) = Saved(studioJson { post("/api/v1/studio/blueprints/$blueprint/entries") { jsonBody(body) } })

    private fun updateEntry(
        entry: Saved,
        body: String,
    ): Saved {
        val request = (json.readTree(body) as tools.jackson.databind.node.ObjectNode).put("revision", entry.revision)
        return Saved(studioJson { put("/api/v1/studio/entries/${entry.id}") { jsonBody(request.toString()) } })
    }

    private fun publish(entry: Saved) =
        Saved(
            studioJson {
                put("/api/v1/studio/entries/${entry.id}/publication") { jsonBody("""{"revision":${entry.revision}}""") }
            },
        )

    private fun studio(request: org.springframework.test.web.servlet.MockMvc.() -> ResultActionsDsl): ResultActionsDsl =
        mockMvc.request()

    private fun studioJson(request: org.springframework.test.web.servlet.MockMvc.() -> ResultActionsDsl): JsonNode {
        val result = mockMvc.request().andExpect { status { is2xxSuccessful() } }.andReturn()
        return json.readTree(result.response.contentAsString)
    }

    private fun public(path: String): ResultActionsDsl = mockMvc.get("/api/v1/public$path")

    private fun MockHttpServletRequestDsl.jsonBody(body: String) {
        with(authentication(administrator))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun org.springframework.test.web.servlet.MockMvc.authGet(path: String): ResultActionsDsl =
        get(path) { with(authentication(administrator)) }

    private fun org.springframework.test.web.servlet.MockMvc.authDelete(path: String): ResultActionsDsl =
        delete(path) { with(authentication(administrator)) }

    private companion object {
        val PROJECT_BLUEPRINT =
            """
            {"key":"project","name":"Projects","kind":"collection","fields":[
              {"key":"title","label":"Title","type":"string","required":true,"maxLength":100},
              {"key":"summary","label":"Summary","type":"text","required":true,"localized":true},
              {"key":"skills","label":"Skills","type":"list","maxItems":20,"item":{"type":"string","maxLength":40}},
              {"key":"repositoryUrl","label":"Repository","type":"url"},
              {"key":"showOnHome","label":"Show on home","type":"boolean"}
            ]}
            """
        val HISTORY_BLUEPRINT =
            """
            {"key":"history","name":"History","kind":"collection","fields":[
              {"key":"year","label":"Year","type":"integer","required":true,"min":1900,"max":2100},
              {"key":"title","label":"Title","type":"string"},
              {"key":"description","label":"Description","type":"text","required":true,"localized":true},
              {"key":"project","label":"Project","type":"reference","blueprints":["project"]}
            ]}
            """
        val PROFILE_BLUEPRINT =
            """
            {"key":"profile","name":"Profile","kind":"singleton","fields":[
              {"key":"name","label":"Name","type":"string","required":true},
              {"key":"links","label":"Links","type":"list","item":{"type":"object","fields":[
                {"key":"label","label":"Label","type":"string","required":true},
                {"key":"href","label":"URL","type":"url","required":true}
              ]}}
            ]}
            """
        const val PROJECT_CONTENT =
            """{"title":"Toast ACS","summary":{"en":"Access control service","ko":"출입 관리 서비스"},"skills":["Kotlin","Spring"],"showOnHome":true}"""
    }
}

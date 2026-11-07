package app.yechan.server.domain.content

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.service.StudioAuthentication
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
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

class EntryHistoryTest : IntegrationTest() {
    private val administrator =
        StudioAuthentication(AdministratorPrincipal(UUID.randomUUID(), "yechan", "Yechan", UUID.randomUUID()))

    @Test
    fun `발행할 때마다 이력이 남고 이전 버전을 초안으로 되돌린다`() {
        define()
        var entry = create("toast", """{"title":"First"}""")
        entry = publish(entry)
        entry = update(entry, "toast", """{"title":"Second"}""")
        entry = publish(entry)

        get("/api/v1/studio/entries/${entry.id}/revisions").andExpect {
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].number") { value(2) }
        }
        get(
            "/api/v1/studio/entries/${entry.id}/revisions/1",
        ).andExpect { jsonPath("$.content.title") { value("First") } }

        send {
            post("/api/v1/studio/entries/${entry.id}/revisions/1/restore") {
                body("""{"revision":${entry.revision}}""")
            }
        }.andExpect {
            jsonPath("$.content.title") { value("First") }
            jsonPath("$.status") { value("CHANGED") }
        }
        mockMvc.get("/api/v1/public/collections/note/toast?locale=en").andExpect {
            jsonPath("$.content.title") { value("Second") }
        }
        get("/api/v1/studio/entries/${entry.id}/revisions/9").andExpect {
            jsonPath("$.code") { value("ENTRY_REVISION_NOT_FOUND") }
        }
    }

    @Test
    fun `삭제한 entry는 휴지통에 들어가고 복원할 수 있다`() {
        define()
        val entry = publish(create("toast", """{"title":"Toast"}"""))

        send { delete("/api/v1/studio/entries/${entry.id}") { auth() } }.andExpect { status { isNoContent() } }
        get("/api/v1/studio/entries/${entry.id}").andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/public/collections/note/toast").andExpect { status { isNotFound() } }
        get("/api/v1/studio/trash").andExpect {
            jsonPath("$.items[0].id") { value(entry.id) }
            jsonPath("$.items[0].purgeAt") { exists() }
        }

        send { post("/api/v1/studio/trash/${entry.id}/restore") { auth() } }.andExpect {
            jsonPath("$.status") { value("DRAFT") }
            jsonPath("$.slug") { value("toast") }
        }
        get("/api/v1/studio/trash").andExpect { jsonPath("$.totalItems") { value(0) } }
    }

    @Test
    fun `같은 slug가 생겼으면 복원하지 않고, 휴지통에서 완전히 지울 수 있다`() {
        define()
        val old = create("toast", """{"title":"Old"}""")
        send { delete("/api/v1/studio/entries/${old.id}") { auth() } }
        create("toast", """{"title":"New"}""")

        send {
            post("/api/v1/studio/trash/${old.id}/restore") {
                auth()
            }
        }.andExpect { jsonPath("$.code") { value("ENTRY_SLUG_TAKEN") } }
        send { delete("/api/v1/studio/trash/${old.id}") { auth() } }.andExpect { status { isNoContent() } }
        send {
            delete("/api/v1/studio/trash/${old.id}") { auth() }
        }.andExpect { jsonPath("$.code") { value("ENTRY_NOT_IN_TRASH") } }
    }

    @Test
    fun `다른 entry가 참조하면 경고하고 force로만 삭제한다`() {
        define()
        val target = create("target", """{"title":"Target"}""")
        create("source", """{"title":"Source","related":"${target.id}"}""")

        send { delete("/api/v1/studio/entries/${target.id}") { auth() } }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ENTRY_REFERENCED") }
            jsonPath("$.errors[0].reason") { value("note/source") }
        }
        send {
            delete(
                "/api/v1/studio/entries/${target.id}?force=true",
            ) { auth() }
        }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `studio 목록은 검색어와 상태로 거른다`() {
        define()
        publish(create("alpha", """{"title":"Kotlin server"}"""))
        val changed = publish(create("beta", """{"title":"React client"}"""))
        update(changed, "beta", """{"title":"React client v2"}""")
        create("gamma", """{"title":"100% done_ok"}""")

        get("/api/v1/studio/blueprints/note/entries?q=kotlin").andExpect {
            jsonPath("$.totalItems") { value(1) }
            jsonPath("$.items[0].slug") { value("alpha") }
        }
        get("/api/v1/studio/blueprints/note/entries?q=gam").andExpect { jsonPath("$.items[0].slug") { value("gamma") } }
        listOf("100%", "_").forEach { search ->
            mockMvc
                .get("/api/v1/studio/blueprints/note/entries") {
                    auth()
                    param("q", search)
                }.andExpect { jsonPath("$.totalItems") { value(1) } }
        }
        get(
            "/api/v1/studio/blueprints/note/entries?status=DRAFT",
        ).andExpect { jsonPath("$.items[0].slug") { value("gamma") } }
        get("/api/v1/studio/blueprints/note/entries?status=PUBLISHED").andExpect {
            jsonPath("$.items[0].slug") { value("alpha") }
        }
        get(
            "/api/v1/studio/blueprints/note/entries?status=CHANGED",
        ).andExpect { jsonPath("$.items[0].slug") { value("beta") } }
    }

    @Test
    fun `휴지통에만 entry가 남은 Blueprint는 삭제하면서 함께 비운다`() {
        define()
        val entry = create("only", """{"title":"Only"}""")
        send {
            delete("/api/v1/studio/blueprints/note") { auth() }
        }.andExpect { jsonPath("$.code") { value("BLUEPRINT_IN_USE") } }
        send { delete("/api/v1/studio/entries/${entry.id}") { auth() } }
        send { delete("/api/v1/studio/blueprints/note") { auth() } }.andExpect { status { isNoContent() } }
        get("/api/v1/studio/trash").andExpect { jsonPath("$.totalItems") { value(0) } }
    }

    private data class Saved(
        val id: String,
        val revision: Long,
    )

    private fun define() {
        send {
            post("/api/v1/studio/blueprints") {
                body(
                    """{"key":"note","name":"Notes","kind":"collection","fields":[
                        {"key":"title","label":"Title","type":"string","required":true},
                        {"key":"related","label":"Related","type":"reference","blueprints":["note"]}]}""",
                )
            }
        }.andExpect { status { isCreated() } }
    }

    private fun create(
        slug: String,
        content: String,
    ) = saved(
        send { post("/api/v1/studio/blueprints/note/entries") { body("""{"slug":"$slug","content":$content}""") } },
    )

    private fun update(
        entry: Saved,
        slug: String,
        content: String,
    ) = saved(
        send {
            put("/api/v1/studio/entries/${entry.id}") {
                body("""{"slug":"$slug","content":$content,"revision":${entry.revision}}""")
            }
        },
    )

    private fun publish(entry: Saved) =
        saved(
            send {
                put(
                    "/api/v1/studio/entries/${entry.id}/publication",
                ) { body("""{"revision":${entry.revision}}""") }
            },
        )

    private fun saved(result: ResultActionsDsl): Saved {
        val node: JsonNode =
            json.readTree(
                result
                    .andExpect {
                        status { is2xxSuccessful() }
                    }.andReturn()
                    .response.contentAsString,
            )
        return Saved(node["id"].stringValue(), node["revision"].longValue())
    }

    private fun get(path: String): ResultActionsDsl = mockMvc.get(path) { auth() }

    private fun send(request: org.springframework.test.web.servlet.MockMvc.() -> ResultActionsDsl): ResultActionsDsl =
        mockMvc.request()

    private fun MockHttpServletRequestDsl.auth() {
        with(authentication(administrator))
    }

    private fun MockHttpServletRequestDsl.body(content: String) {
        auth()
        contentType = MediaType.APPLICATION_JSON
        this.content = content
    }
}

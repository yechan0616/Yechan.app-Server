package app.yechan.server.domain.content

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.service.StudioAuthentication
import app.yechan.server.support.Images
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentTransferTest : IntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val administrator =
        StudioAuthentication(AdministratorPrincipal(UUID.randomUUID(), "yechan", "Yechan", UUID.randomUUID()))

    @Test
    fun `내보낸 zip을 빈 서버에 가져오면 발행 상태와 참조, 이미지가 그대로 돌아온다`() {
        seed()
        val archive = export()
        val names =
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                generateSequence { zip.nextEntry }.toList().map { it.name }
            }
        assertTrue("bundle.json" in names)
        assertTrue(names.any { it.startsWith("assets/") && it.endsWith(".png") })

        val before = publicState()
        jdbc.execute("truncate table entry_revision, entry, blueprint, asset cascade")

        mockMvc
            .multipart("/api/v1/studio/import") {
                file(MockMultipartFile("file", "content.zip", "application/zip", archive))
                with(authentication(administrator))
            }.andExpect {
                status { isOk() }
                jsonPath("$.blueprintsCreated") { value(2) }
                jsonPath("$.entriesCreated") { value(3) }
            }

        assertEquals(before, publicState())
        studio { get("/api/v1/studio/blueprints/post/entries?status=CHANGED") { auth() } }.andExpect {
            jsonPath("$.items[0].content.title") { value("Draft title") }
        }
    }

    @Test
    fun `zip 밖으로 나가는 경로는 거절한다`() {
        val malicious =
            java.io
                .ByteArrayOutputStream()
                .also { out ->
                    java.util.zip.ZipOutputStream(out).use { zip ->
                        zip.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
                        zip.write("x".toByteArray())
                        zip.closeEntry()
                    }
                }.toByteArray()
        mockMvc
            .multipart("/api/v1/studio/import") {
                file(MockMultipartFile("file", "evil.zip", "application/zip", malicious))
                with(authentication(administrator))
            }.andExpect { status { is5xxServerError() } }
    }

    private fun seed() {
        val asset =
            json(
                mockMvc.multipart("/api/v1/studio/assets") {
                    file(MockMultipartFile("file", "cover.png", "image/png", Images.encode("png", 30, 20, seed = 77)))
                    auth()
                },
            )
        studio {
            post("/api/v1/studio/blueprints") {
                body(
                    """{"key":"author","name":"Authors","kind":"collection","fields":[{"key":"name","label":"Name","type":"string"}]}""",
                )
            }
        }
        studio {
            post("/api/v1/studio/blueprints") {
                body(
                    """{"key":"post","name":"Posts","kind":"collection","fields":[
                        {"key":"title","label":"Title","type":"string","required":true},
                        {"key":"author","label":"Author","type":"reference","blueprints":["author"]},
                        {"key":"cover","label":"Cover","type":"asset"},
                        {"key":"links","label":"Links","type":"list","item":{"type":"object","fields":[
                          {"key":"to","label":"To","type":"reference","blueprints":["post"]}]}}]}""",
                )
            }
        }
        val author = create("author", "yechan", """{"name":"Yechan"}""", publish = true)
        val authorId = author["id"].stringValue()
        val first = create("post", "first", """{"title":"First","author":"$authorId"}""", publish = true)
        val second =
            create(
                "post",
                "second",
                """{"title":"Published title","cover":{"asset":"${asset["id"].stringValue()}","alt":{"en":"Cover"}},
                   "links":[{"to":"${first["id"].stringValue()}"}]}""",
                publish = true,
            )
        studio {
            put("/api/v1/studio/entries/${second["id"].stringValue()}") {
                body("""{"slug":"second","content":{"title":"Draft title"},"revision":${second["revision"]}}""")
            }
        }.andExpect { status { isOk() } }
    }

    private fun create(
        blueprint: String,
        slug: String,
        content: String,
        publish: Boolean,
    ): JsonNode {
        val body = """{"slug":"$slug","content":$content}"""
        val entry = json(studio { post("/api/v1/studio/blueprints/$blueprint/entries") { body(body) } })
        if (!publish) return entry
        val id = entry["id"].stringValue()
        val revision = """{"revision":${entry["revision"]}}"""
        return json(studio { put("/api/v1/studio/entries/$id/publication") { body(revision) } })
    }

    // Ids and storage URLs change on import, so compare everything else.
    private fun publicState(): List<String> =
        listOf("author", "post").map { key ->
            val response = mockMvc.get("/api/v1/public/collections/$key?locale=en").andReturn().response
            json.readTree(response.contentAsString)["items"].values().joinToString("|") { item ->
                val content = item["content"].deepCopy() as ObjectNode
                (content["author"] as? ObjectNode)?.remove("id")
                (content["cover"] as? ObjectNode)?.remove("url")
                content["links"]?.values()?.forEach { (it["to"] as? ObjectNode)?.remove("id") }
                "${item["slug"].stringValue()}=$content"
            }
        }

    private fun export(): ByteArray {
        val started =
            mockMvc
                .get("/api/v1/studio/export") { auth() }
                .andExpect { request { asyncStarted() } }
                .andReturn()
        return mockMvc
            .perform(asyncDispatch(started))
            .andReturn()
            .response.contentAsByteArray
    }

    private fun studio(request: MockMvc.() -> ResultActionsDsl): ResultActionsDsl = mockMvc.request()

    private fun json(result: ResultActionsDsl): JsonNode =
        json.readTree(
            result
                .andExpect { status { is2xxSuccessful() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun MockHttpServletRequestDsl.auth() {
        with(authentication(administrator))
    }

    private fun MockHttpServletRequestDsl.body(content: String) {
        auth()
        contentType = MediaType.APPLICATION_JSON
        this.content = content
    }
}

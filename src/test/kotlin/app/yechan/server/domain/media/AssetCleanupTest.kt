package app.yechan.server.domain.media

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
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import java.util.UUID

class AssetCleanupTest : IntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val administrator =
        StudioAuthentication(AdministratorPrincipal(UUID.randomUUID(), "yechan", "Yechan", UUID.randomUUID()))

    @Test
    fun `어디에도 쓰이지 않는 오래된 이미지만 정리한다`() {
        val used = upload(1)
        val history = upload(2)
        val unused = upload(3)
        val fresh = upload(4)
        definePage()
        var entry = createPage("""{"cover":{"asset":"${history["id"].stringValue()}"}}""")
        entry = publish(entry)
        update(entry, """{"cover":{"asset":"${used["id"].stringValue()}"}}""")
        jdbc.update(
            "update asset set created_at = now() - interval '2 days' where id <> ?",
            UUID.fromString(fresh["id"].stringValue()),
        )

        mockMvc.get("/api/v1/studio/assets?unused=true") { auth() }.andExpect {
            jsonPath("$.totalItems") { value(2) }
        }
        mockMvc.post("/api/v1/studio/assets/cleanup") { auth() }.andExpect { jsonPath("$.deleted") { value(1) } }

        mockMvc
            .get(
                "/api/v1/studio/assets/${unused["id"].stringValue()}",
            ) { auth() }
            .andExpect { status { isNotFound() } }
        listOf(used, history, fresh).forEach { asset ->
            mockMvc.get("/api/v1/studio/assets/${asset["id"].stringValue()}") { auth() }.andExpect { status { isOk() } }
        }
    }

    @Test
    fun `회전 정보가 있는 사진은 화면에 보이는 가로세로로 저장한다`() {
        val jpeg = Images.encode("jpg", 40, 20, seed = 5)
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + ORIENTATION_6
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        val rotated = jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)

        mockMvc
            .multipart("/api/v1/studio/assets") {
                file(MockMultipartFile("file", "photo.jpg", "image/jpeg", rotated))
                auth()
            }.andExpect {
                jsonPath("$.width") { value(20) }
                jsonPath("$.height") { value(40) }
            }
    }

    private fun upload(seed: Int): JsonNode =
        json.readTree(
            mockMvc
                .multipart("/api/v1/studio/assets") {
                    file(MockMultipartFile("file", "$seed.png", "image/png", Images.encode("png", 8, 8, seed = seed)))
                    auth()
                }.andReturn()
                .response.contentAsString,
        )

    private fun definePage() {
        mockMvc.post("/api/v1/studio/blueprints") {
            body(
                """{"key":"page","name":"Pages","kind":"collection","fields":[{"key":"cover","label":"Cover","type":"asset"}]}""",
            )
        }
    }

    private fun createPage(content: String): JsonNode =
        read(mockMvc.post("/api/v1/studio/blueprints/page/entries") { body("""{"slug":"home","content":$content}""") })

    private fun publish(entry: JsonNode): JsonNode =
        read(
            mockMvc.put("/api/v1/studio/entries/${entry["id"].stringValue()}/publication") {
                body("""{"revision":${entry["revision"]}}""")
            },
        )

    private fun update(
        entry: JsonNode,
        content: String,
    ): JsonNode =
        read(
            mockMvc.put("/api/v1/studio/entries/${entry["id"].stringValue()}") {
                body("""{"slug":"home","content":$content,"revision":${entry["revision"]}}""")
            },
        )

    private fun read(result: ResultActionsDsl): JsonNode =
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

    private companion object {
        val ORIENTATION_6 =
            byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 0x2A, 0, 0, 0, 8, 0, 1) +
                byteArrayOf(0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0) +
                byteArrayOf(0, 0, 0, 0)
    }
}

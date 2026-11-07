package app.yechan.server.domain.media

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.service.StudioAuthentication
import app.yechan.server.support.Images
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaApiTest : IntegrationTest() {
    private val administrator =
        StudioAuthentication(AdministratorPrincipal(UUID.randomUUID(), "yechan", "Yechan", UUID.randomUUID()))
    private val http = HttpClient.newHttpClient()

    @Test
    fun `업로드한 이미지는 저장소에 올라가고 공개 URL로 받을 수 있다`() {
        val png = Images.encode("png", 320, 200, seed = 1)
        val asset = json(upload(png, "../../etc/hero\u0000.png").andExpect { status { isCreated() } })

        assertEquals("image/png", asset["contentType"].stringValue())
        assertEquals(320, asset["width"].intValue())
        assertEquals(200, asset["height"].intValue())
        assertEquals("hero.png", asset["filename"].stringValue())
        assertTrue(
            Regex(".*/yechan-media/assets/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png$").matches(asset["url"].stringValue()),
        )

        val response =
            http.send(
                HttpRequest.newBuilder(URI(asset["url"].stringValue())).build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
        assertEquals(200, response.statusCode())
        assertContentEquals(png, response.body())
        assertEquals("image/png", response.headers().firstValue("Content-Type").orElse(""))
    }

    @Test
    fun `같은 파일을 다시 올리면 기존 asset을 돌려준다`() {
        val jpeg = Images.encode("jpg", 64, 64, seed = 2)
        val first = json(upload(jpeg, "a.jpg").andExpect { status { isCreated() } })
        val second = json(upload(jpeg, "b.jpg").andExpect { status { isOk() } })
        assertEquals(first["id"], second["id"])
    }

    @Test
    fun `WebP도 업로드할 수 있다`() {
        upload(Images.webpLossless(800, 600), "cover.webp").andExpect {
            status { isCreated() }
            jsonPath("$.contentType") { value("image/webp") }
            jsonPath("$.width") { value(800) }
        }
    }

    @Test
    fun `이미지가 아니거나 비었거나 너무 크면 거절한다`() {
        upload("<svg/>".toByteArray(), "x.svg").andExpect {
            status { isUnsupportedMediaType() }
            jsonPath("$.code") { value("ASSET_UNSUPPORTED_TYPE") }
        }
        upload(ByteArray(0), "empty.png").andExpect { jsonPath("$.code") { value("ASSET_EMPTY") } }
        val oversized = Images.encode("png", 8, 8) + ByteArray(1_100_000)
        upload(oversized, "big.png").andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value("ASSET_TOO_LARGE") }
        }
        upload(Images.webpExtended(20_000, 10), "wide.webp").andExpect {
            jsonPath("$.code") { value("ASSET_DIMENSIONS_INVALID") }
        }
    }

    @Test
    fun `asset 필드는 발행된 공개 응답에서 URL, 크기, 언어별 대체 텍스트로 펼쳐진다`() {
        val asset =
            json(upload(Images.encode("png", 1200, 630, seed = 3), "toast.png").andExpect { status { isCreated() } })
        studio {
            post("/api/v1/studio/blueprints") {
                body(
                    """{"key":"project","name":"Projects","kind":"collection","fields":[
                        {"key":"title","label":"Title","type":"string","required":true},
                        {"key":"cover","label":"Cover","type":"asset","required":true}]}""",
                )
            }
        }.andExpect { status { isCreated() } }

        val content =
            """{"title":"Toast","cover":{"asset":"${asset["id"].stringValue()}","alt":{"en":"Dashboard","ko":"대시보드"}}}"""
        val entry =
            json(
                studio {
                    post(
                        "/api/v1/studio/blueprints/project/entries",
                    ) { body("""{"slug":"toast","content":$content}""") }
                },
            )
        studio {
            put(
                "/api/v1/studio/entries/${entry["id"].stringValue()}/publication",
            ) { body("""{"revision":${entry["revision"]}}""") }
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/public/collections/project/toast?locale=ko").andExpect {
            jsonPath("$.content.cover.url") { value(asset["url"].stringValue()) }
            jsonPath("$.content.cover.width") { value(1200) }
            jsonPath("$.content.cover.height") { value(630) }
            jsonPath("$.content.cover.alt") { value("대시보드") }
        }
        studio { delete("/api/v1/studio/assets/${asset["id"].stringValue()}") { with(authentication(administrator)) } }
            .andExpect { jsonPath("$.code") { value("ASSET_IN_USE") } }
    }

    @Test
    fun `없는 asset을 가리키면 발행할 수 없고 형식이 틀리면 초안부터 막는다`() {
        studio {
            post("/api/v1/studio/blueprints") {
                body(
                    """{"key":"page","name":"Pages","kind":"collection","fields":[{"key":"cover","label":"Cover","type":"asset"}]}""",
                )
            }
        }.andExpect { status { isCreated() } }

        studio {
            post("/api/v1/studio/blueprints/page/entries") {
                body("""{"slug":"a","content":{"cover":{"asset":"nope","extra":1}}}""")
            }
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.length()") { value(2) }
        }

        val missing = UUID.randomUUID()
        val entry =
            json(
                studio {
                    post(
                        "/api/v1/studio/blueprints/page/entries",
                    ) { body("""{"slug":"b","content":{"cover":{"asset":"$missing"}}}""") }
                },
            )
        studio {
            put("/api/v1/studio/entries/${entry["id"].stringValue()}/publication") {
                body("""{"revision":${entry["revision"]}}""")
            }
        }.andExpect { jsonPath("$.errors[0].field") { value("content.cover.asset") } }
    }

    @Test
    fun `쓰이지 않는 asset은 삭제되고 저장소에서도 지워진다`() {
        val asset = json(upload(Images.encode("gif", 4, 4, seed = 4), "dot.gif").andExpect { status { isCreated() } })
        studio { delete("/api/v1/studio/assets/${asset["id"].stringValue()}") { with(authentication(administrator)) } }
            .andExpect { status { isNoContent() } }

        val response =
            http.send(
                HttpRequest.newBuilder(URI(asset["url"].stringValue())).build(),
                HttpResponse.BodyHandlers.discarding(),
            )
        assertEquals(404, response.statusCode())
        studio { get("/api/v1/studio/assets/${asset["id"].stringValue()}") { with(authentication(administrator)) } }
            .andExpect { jsonPath("$.code") { value("ASSET_NOT_FOUND") } }
    }

    @Test
    fun `asset 목록은 최신순으로 페이지를 나눠 준다`() {
        upload(Images.encode("png", 2, 2, seed = 5), "one.png")
        upload(Images.encode("png", 3, 3, seed = 6), "two.png")
        studio { get("/api/v1/studio/assets?size=1") { with(authentication(administrator)) } }.andExpect {
            jsonPath("$.items[0].filename") { value("two.png") }
            jsonPath("$.totalItems") { value(2) }
            jsonPath("$.totalPages") { value(2) }
        }
    }

    @Test
    fun `업로드는 관리자 세션이 필요하다`() {
        mockMvc
            .multipart("/api/v1/studio/assets") {
                file(MockMultipartFile("file", "a.png", "image/png", Images.encode("png", 2, 2)))
            }.andExpect { status { isUnauthorized() } }
    }

    private fun upload(
        bytes: ByteArray,
        filename: String,
    ): ResultActionsDsl =
        mockMvc.multipart("/api/v1/studio/assets") {
            file(MockMultipartFile("file", filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes))
            with(authentication(administrator))
        }

    private fun studio(request: org.springframework.test.web.servlet.MockMvc.() -> ResultActionsDsl): ResultActionsDsl =
        mockMvc.request()

    private fun json(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.body(content: String) {
        with(authentication(administrator))
        contentType = MediaType.APPLICATION_JSON
        this.content = content
    }
}

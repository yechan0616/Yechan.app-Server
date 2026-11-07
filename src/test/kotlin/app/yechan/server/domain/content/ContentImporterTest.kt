package app.yechan.server.domain.content

import app.yechan.server.domain.auth.dto.AdministratorPrincipal
import app.yechan.server.domain.auth.service.StudioAuthentication
import app.yechan.server.domain.content.dto.ImportReport
import app.yechan.server.domain.content.service.ContentImporter
import app.yechan.server.support.Images
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.get
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals

class ContentImporterTest : IntegrationTest() {
    @Autowired
    private lateinit var importer: ContentImporter

    private val administrator =
        StudioAuthentication(AdministratorPrincipal(UUID.randomUUID(), "yechan", "Yechan", UUID.randomUUID()))

    @TempDir
    private lateinit var directory: Path

    @Test
    fun `번들을 가져오면 앞뒤 참조와 이미지가 연결되고 다시 가져와도 중복되지 않는다`() {
        Files.createDirectories(directory.resolve("images"))
        Files.write(directory.resolve("images/cover.png"), Images.encode("png", 40, 20, seed = 9))
        Files.writeString(directory.resolve("bundle.json"), BUNDLE)

        assertEquals(ImportReport(2, 1, 3, 0), importer.import(directory))
        assertEquals(ImportReport(0, 0, 0, 3), importer.import(directory))

        mockMvc.get("/api/v1/public/collections/note/hello?locale=ko").andExpect {
            jsonPath("$.content.title") { value("안녕") }
            jsonPath("$.content.cover.width") { value(40) }
            jsonPath("$.content.related.slug") { value("first") }
        }
        mockMvc.get("/api/v1/public/collections/note/first?locale=en").andExpect {
            jsonPath("$.content.related.slug") { value("hello") }
        }
        mockMvc
            .get(
                "/api/v1/public/singletons/site?locale=en",
            ).andExpect { jsonPath("$.content.name") { value("Yechan") } }
    }

    @Test
    fun `ref가 UUID면 entry id로 보존되어 slug 없는 초안도 다시 가져올 때 중복되지 않는다`() {
        val ref = UUID.randomUUID()
        Files.writeString(
            directory.resolve("bundle.json"),
            """
            {
              "blueprints": [{"key":"note","name":"Notes","kind":"collection","fields":[{"key":"title","label":"Title","type":"string"}]}],
              "entries": [{"ref":"$ref","blueprint":"note","publish":false,"content":{"title":"Untitled"}}]
            }
            """,
        )

        assertEquals(ImportReport(1, 0, 1, 0), importer.import(directory))
        assertEquals(ImportReport(0, 0, 0, 1), importer.import(directory))

        mockMvc.get("/api/v1/studio/entries/$ref") { with(authentication(administrator)) }.andExpect {
            status { isOk() }
            jsonPath("$.content.title") { value("Untitled") }
        }
    }

    @Test
    fun `번들 밖의 파일은 읽지 않는다`() {
        Files.writeString(directory.resolve("bundle.json"), """{"assets":[{"ref":"x","file":"../../etc/passwd"}]}""")
        assertThrows<IllegalArgumentException> { importer.import(directory) }
    }

    private companion object {
        const val BUNDLE = """
        {
          "blueprints": [
            {"key":"note","name":"Notes","kind":"collection","fields":[
              {"key":"title","label":"Title","type":"string","required":true,"localized":true},
              {"key":"cover","label":"Cover","type":"asset"},
              {"key":"related","label":"Related","type":"reference","blueprints":["note"]}]},
            {"key":"site","name":"Site","kind":"singleton","fields":[{"key":"name","label":"Name","type":"string"}]}
          ],
          "assets": [{"ref":"cover","file":"images/cover.png"}],
          "entries": [
            {"ref":"hello","blueprint":"note","slug":"hello","position":1,
             "content":{"title":{"en":"Hello","ko":"안녕"},"cover":{"asset":"@asset:cover"},"related":"@entry:first"}},
            {"ref":"first","blueprint":"note","slug":"first","content":{"title":{"en":"First"},"related":"@entry:hello"}},
            {"ref":"site","blueprint":"site","content":{"name":"Yechan"}}
          ]
        }
        """
    }
}

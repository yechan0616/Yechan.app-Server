package app.yechan.server.domain.content

import app.yechan.server.domain.content.dto.ImportReport
import app.yechan.server.domain.content.service.ContentImporter
import app.yechan.server.support.Images
import app.yechan.server.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class ContentImporterTest : IntegrationTest() {
    @Autowired
    private lateinit var importer: ContentImporter

    @TempDir
    private lateinit var directory: Path

    @Test
    fun `번들을 가져오면 참조와 이미지가 연결되고 다시 가져와도 중복되지 않는다`() {
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
        mockMvc
            .get(
                "/api/v1/public/singletons/site?locale=en",
            ).andExpect { jsonPath("$.content.name") { value("Yechan") } }
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
            {"ref":"first","blueprint":"note","slug":"first","content":{"title":{"en":"First"}}},
            {"ref":"hello","blueprint":"note","slug":"hello","position":1,
             "content":{"title":{"en":"Hello","ko":"안녕"},"cover":{"asset":"@asset:cover"},"related":"@entry:first"}},
            {"ref":"site","blueprint":"site","content":{"name":"Yechan"}}
          ]
        }
        """
    }
}

package app.yechan.server.domain.content.controller

import app.yechan.server.domain.content.dto.ImportReport
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.service.ContentExporter
import app.yechan.server.domain.content.service.ContentImporter
import app.yechan.server.global.error.BusinessException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import tools.jackson.core.JacksonException
import java.nio.file.Files
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/v1/studio")
class ContentTransferController(
    private val exporter: ContentExporter,
    private val importer: ContentImporter,
    private val clock: Clock,
) {
    @GetMapping("/export", produces = ["application/zip"])
    fun export(): ResponseEntity<StreamingResponseBody> {
        val filename = "yechan-content-${STAMP.format(clock.instant().atZone(SEOUL))}.zip"
        val bundle = exporter.pack()
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(filename)
                    .build()
                    .toString(),
            ).header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentLength(Files.size(bundle))
            .body(
                StreamingResponseBody { output ->
                    try {
                        Files.copy(bundle, output)
                    } finally {
                        Files.deleteIfExists(bundle)
                    }
                },
            )
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun import(
        @RequestPart("file") file: MultipartFile,
    ): ImportReport =
        try {
            file.inputStream.use(importer::importArchive)
        } catch (ex: IllegalArgumentException) {
            throw BusinessException(
                ContentErrorCode.BUNDLE_INVALID,
                ex.message ?: ContentErrorCode.BUNDLE_INVALID.message,
                cause = ex,
            )
        } catch (ex: JacksonException) {
            throw BusinessException(ContentErrorCode.BUNDLE_INVALID, "bundle.json cannot be parsed.", cause = ex)
        }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    }
}

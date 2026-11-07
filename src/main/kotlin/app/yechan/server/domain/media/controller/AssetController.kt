package app.yechan.server.domain.media.controller

import app.yechan.server.domain.media.dto.AssetResponse
import app.yechan.server.domain.media.dto.CleanupResponse
import app.yechan.server.domain.media.service.MediaAssets
import app.yechan.server.global.common.response.PageResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/studio/assets")
class AssetController(
    private val assets: MediaAssets,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<AssetResponse> {
        assets.checkSize(file.size)
        val uploaded = assets.upload(file.bytes, file.originalFilename)
        return if (uploaded.created) {
            ResponseEntity.created(URI.create("/api/v1/studio/assets/${uploaded.asset.id}")).body(uploaded.asset)
        } else {
            ResponseEntity.ok(uploaded.asset)
        }
    }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "30") @Min(1) @Max(100) size: Int,
        @RequestParam(defaultValue = "false") unused: Boolean,
    ): PageResponse<AssetResponse> = if (unused) assets.unused(page, size) else assets.list(page, size)

    @PostMapping("/cleanup")
    fun cleanup(): CleanupResponse = CleanupResponse(assets.deleteUnused())

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): AssetResponse = assets.get(id)

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        assets.delete(id)
        return ResponseEntity.noContent().build()
    }
}

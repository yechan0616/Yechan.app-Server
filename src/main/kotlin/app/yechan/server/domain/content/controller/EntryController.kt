package app.yechan.server.domain.content.controller

import app.yechan.server.domain.content.dto.CreateEntryRequest
import app.yechan.server.domain.content.dto.EntryResponse
import app.yechan.server.domain.content.dto.PreviewLinkResponse
import app.yechan.server.domain.content.dto.PublishEntryRequest
import app.yechan.server.domain.content.dto.ReorderEntriesRequest
import app.yechan.server.domain.content.dto.UpdateEntryRequest
import app.yechan.server.domain.content.service.Entries
import app.yechan.server.global.common.response.PageResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/studio")
class EntryController(
    private val entries: Entries,
) {
    @GetMapping("/blueprints/{key}/entries")
    fun list(
        @PathVariable key: String,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<EntryResponse> = entries.list(key, page, size)

    @PostMapping("/blueprints/{key}/entries")
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateEntryRequest,
    ): ResponseEntity<EntryResponse> {
        val created = entries.create(key, request)
        return ResponseEntity.created(URI.create("/api/v1/studio/entries/${created.id}")).body(created)
    }

    @PutMapping("/blueprints/{key}/entries/order")
    fun reorder(
        @PathVariable key: String,
        @Valid @RequestBody request: ReorderEntriesRequest,
    ): ResponseEntity<Unit> {
        entries.reorder(key, request)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/entries/{id}/preview")
    fun preview(
        @PathVariable id: UUID,
    ): PreviewLinkResponse = entries.preview(id)

    @GetMapping("/entries/{id}")
    fun get(
        @PathVariable id: UUID,
    ): EntryResponse = entries.get(id)

    @PutMapping("/entries/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateEntryRequest,
    ): EntryResponse = entries.update(id, request)

    @DeleteMapping("/entries/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        entries.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/entries/{id}/publication")
    fun publish(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PublishEntryRequest,
    ): EntryResponse = entries.publish(id, request)

    @DeleteMapping("/entries/{id}/publication")
    fun unpublish(
        @PathVariable id: UUID,
    ): EntryResponse = entries.unpublish(id)
}

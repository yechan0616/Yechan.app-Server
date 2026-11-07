package app.yechan.server.domain.content.controller

import app.yechan.server.domain.content.dto.EntryResponse
import app.yechan.server.domain.content.dto.RestoreRevisionRequest
import app.yechan.server.domain.content.dto.RevisionResponse
import app.yechan.server.domain.content.dto.RevisionSummaryResponse
import app.yechan.server.domain.content.dto.TrashedEntryResponse
import app.yechan.server.domain.content.service.EntryHistory
import app.yechan.server.global.common.response.PageResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/studio")
class EntryHistoryController(
    private val history: EntryHistory,
) {
    @GetMapping("/entries/{id}/revisions")
    fun revisions(
        @PathVariable id: UUID,
    ): List<RevisionSummaryResponse> = history.revisions(id)

    @GetMapping("/entries/{id}/revisions/{number}")
    fun revision(
        @PathVariable id: UUID,
        @PathVariable number: Int,
    ): RevisionResponse = history.revision(id, number)

    @PostMapping("/entries/{id}/revisions/{number}/restore")
    fun restoreRevision(
        @PathVariable id: UUID,
        @PathVariable number: Int,
        @Valid @RequestBody request: RestoreRevisionRequest,
    ): EntryResponse = history.restoreRevision(id, number, request)

    @GetMapping("/trash")
    fun trash(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "30") @Min(1) @Max(100) size: Int,
    ): PageResponse<TrashedEntryResponse> = history.trash(page, size)

    @PostMapping("/trash/{id}/restore")
    fun restore(
        @PathVariable id: UUID,
    ): EntryResponse = history.restoreFromTrash(id)

    @DeleteMapping("/trash/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = history.deleteFromTrash(id)
}

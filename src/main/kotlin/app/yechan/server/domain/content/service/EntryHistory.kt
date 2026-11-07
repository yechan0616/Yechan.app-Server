package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.EntryResponse
import app.yechan.server.domain.content.dto.RestoreRevisionRequest
import app.yechan.server.domain.content.dto.RevisionResponse
import app.yechan.server.domain.content.dto.RevisionSummaryResponse
import app.yechan.server.domain.content.dto.TrashedEntryResponse
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.repository.EntryRepository
import app.yechan.server.global.common.response.PageResponse
import app.yechan.server.global.error.BusinessException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class EntryHistory(
    private val entries: Entries,
    private val repository: EntryRepository,
    private val blueprints: Blueprints,
    private val revisions: Revisions,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun revisions(entryId: UUID): List<RevisionSummaryResponse> = revisions.list(entries.require(entryId).id)

    @Transactional(readOnly = true)
    fun revision(
        entryId: UUID,
        number: Int,
    ): RevisionResponse = revisions.get(entries.require(entryId).id, number)

    @Transactional
    fun restoreRevision(
        entryId: UUID,
        number: Int,
        request: RestoreRevisionRequest,
    ): EntryResponse {
        val entry = entries.require(entryId)
        entries.checkRevision(entry, request.revision)
        val snapshot = revisions.require(entry.id, number)
        entries.checkDraft(entry.blueprint, snapshot.slug, snapshot.content)
        snapshot.slug?.let { slug ->
            if (repository.existsByBlueprintIdAndSlugAndIdNot(entry.blueprint.id, slug, entry.id)) {
                throw BusinessException(ContentErrorCode.ENTRY_SLUG_TAKEN)
            }
        }
        entry.restoreDraft(snapshot.slug, snapshot.content)
        return entries.describe(repository.saveAndFlush(entry))
    }

    @Transactional(readOnly = true)
    fun trash(
        page: Int,
        size: Int,
    ): PageResponse<TrashedEntryResponse> =
        PageResponse.of(repository.findTrash(PageRequest.of(page, size))) {
            TrashedEntryResponse(
                id = it.id,
                blueprint = it.blueprint,
                slug = it.slug,
                publishedSlug = it.publishedSlug,
                publishedAt = it.publishedAt,
                deletedAt = it.deletedAt,
                purgeAt = it.deletedAt.plus(TRASH_RETENTION),
            )
        }

    @Transactional
    fun restoreFromTrash(id: UUID): EntryResponse {
        val trashed = repository.findTrashed(id) ?: throw BusinessException(ContentErrorCode.ENTRY_NOT_IN_TRASH)
        val blueprint = blueprints.require(trashed.blueprint)
        blueprints.requireSingletonVacant(blueprint)
        val slugTaken =
            trashed.slug?.let { repository.existsByBlueprintIdAndSlug(blueprint.id, it) } == true ||
                trashed.publishedSlug?.let { repository.existsByBlueprintIdAndPublishedSlug(blueprint.id, it) } == true
        if (slugTaken) throw BusinessException(ContentErrorCode.ENTRY_SLUG_TAKEN)
        repository.restoreFromTrash(id)
        return entries.get(id)
    }

    @Transactional
    fun deleteFromTrash(id: UUID) {
        if (repository.deleteFromTrash(id) == 0) throw BusinessException(ContentErrorCode.ENTRY_NOT_IN_TRASH)
    }

    @Transactional
    fun purgeTrash(): Int = repository.purgeTrash(clock.instant().minus(TRASH_RETENTION))

    companion object {
        val TRASH_RETENTION: Duration = Duration.ofDays(30)
    }
}

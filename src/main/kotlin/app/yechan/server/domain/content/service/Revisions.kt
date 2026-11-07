package app.yechan.server.domain.content.service

import app.yechan.server.domain.content.dto.RevisionResponse
import app.yechan.server.domain.content.dto.RevisionSummaryResponse
import app.yechan.server.domain.content.entity.Entry
import app.yechan.server.domain.content.entity.EntryRevision
import app.yechan.server.domain.content.exception.ContentErrorCode
import app.yechan.server.domain.content.repository.EntryRevisionRepository
import app.yechan.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class Revisions(
    private val repository: EntryRevisionRepository,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun record(entry: Entry) {
        val number = repository.latestNumber(entry.id) + 1
        repository.save(EntryRevision(entry, number, entry.publishedSlug, checkNotNull(entry.published).deepCopy()))
        if (number > KEEP) repository.deleteUpTo(entry.id, number - KEEP)
    }

    @Transactional(readOnly = true)
    fun list(entryId: UUID): List<RevisionSummaryResponse> =
        repository.findAllByEntryIdOrderByNumberDesc(entryId).map {
            RevisionSummaryResponse(it.number, it.slug, checkNotNull(it.createdAt))
        }

    @Transactional(readOnly = true)
    fun get(
        entryId: UUID,
        number: Int,
    ): RevisionResponse =
        require(entryId, number).let {
            RevisionResponse(it.number, it.slug, it.content, checkNotNull(it.createdAt))
        }

    fun require(
        entryId: UUID,
        number: Int,
    ): EntryRevision =
        repository.findByEntryIdAndNumber(entryId, number)
            ?: throw BusinessException(ContentErrorCode.ENTRY_REVISION_NOT_FOUND)

    private companion object {
        const val KEEP = 50
    }
}

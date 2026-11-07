package app.yechan.server.domain.content.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.SQLRestriction
import tools.jackson.databind.node.ObjectNode
import java.time.Instant

// Trashed entries stay in the table for a while; every JPA query skips them.
@Entity
@Table(name = "entry")
@SQLRestriction("deleted_at is null")
class Entry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blueprint_id", nullable = false, updatable = false)
    val blueprint: Blueprint,
    slug: String?,
    position: Int,
    draft: ObjectNode,
) : BaseEntity() {
    @Column(length = 96)
    var slug: String? = slug
        protected set

    @Column(nullable = false)
    var position: Int = position
        protected set

    @Convert(converter = ObjectNodeConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(nullable = false, columnDefinition = "jsonb")
    var draft: ObjectNode = draft
        protected set

    @Convert(converter = ObjectNodeConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(columnDefinition = "jsonb")
    var published: ObjectNode? = null
        protected set

    @Column(length = 96)
    var publishedSlug: String? = null
        protected set

    var publishedAt: Instant? = null
        protected set

    @Version
    @Column(nullable = false)
    var revision: Long = 0
        protected set

    val status: EntryStatus
        get() =
            when {
                published == null -> EntryStatus.DRAFT
                published == draft && publishedSlug == slug -> EntryStatus.PUBLISHED
                else -> EntryStatus.CHANGED
            }

    fun edit(
        slug: String?,
        position: Int,
        draft: ObjectNode,
    ) {
        this.slug = slug
        this.position = position
        this.draft = draft
    }

    fun publish(at: Instant) {
        published = draft.deepCopy()
        publishedSlug = slug
        publishedAt = at
    }

    var deletedAt: Instant? = null
        protected set

    fun moveToTrash(at: Instant) {
        unpublish()
        deletedAt = at
    }

    fun restoreDraft(
        slug: String?,
        content: ObjectNode,
    ) {
        this.slug = slug
        this.draft = content.deepCopy()
    }

    fun unpublish() {
        published = null
        publishedSlug = null
        publishedAt = null
    }
}

enum class EntryStatus {
    DRAFT,
    PUBLISHED,

    CHANGED,
}

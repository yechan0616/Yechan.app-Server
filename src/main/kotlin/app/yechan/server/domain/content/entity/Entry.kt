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
import tools.jackson.databind.node.ObjectNode
import java.time.Instant

@Entity
@Table(name = "entry")
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

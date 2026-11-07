package app.yechan.server.domain.content.entity

import app.yechan.server.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import tools.jackson.databind.node.ObjectNode

@Entity
@Table(name = "entry_revision")
class EntryRevision(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    val entry: Entry,
    @Column(nullable = false, updatable = false)
    val number: Int,
    @Column(length = 96, updatable = false)
    val slug: String?,
    @Convert(converter = ObjectNodeConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    val content: ObjectNode,
) : BaseEntity()

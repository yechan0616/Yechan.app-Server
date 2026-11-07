alter table entry
    add column deleted_at timestamptz;

alter table entry
    drop constraint entry_slug_unique,
    drop constraint entry_published_slug_unique;

create unique index entry_slug_active_idx on entry (blueprint_id, slug) where deleted_at is null;
create unique index entry_published_slug_active_idx on entry (blueprint_id, published_slug) where deleted_at is null;
create index entry_deleted_at_idx on entry (deleted_at) where deleted_at is not null;

create table entry_revision
(
    id         uuid primary key,
    entry_id   uuid        not null references entry (id) on delete cascade,
    number     integer     not null,
    slug       varchar(96),
    content    jsonb       not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint entry_revision_number_unique unique (entry_id, number),
    constraint entry_revision_number_positive check (number > 0),
    constraint entry_revision_content_object check (jsonb_typeof(content) = 'object')
);

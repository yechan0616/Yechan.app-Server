create table blueprint
(
    id          uuid primary key,
    key         varchar(64)  not null unique,
    kind        varchar(20)  not null,
    name        varchar(100) not null,
    description varchar(500),
    fields      jsonb        not null,
    revision    bigint       not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint blueprint_key_format check (key ~ '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'),
    constraint blueprint_kind check (kind in ('COLLECTION', 'SINGLETON')),
    constraint blueprint_fields_array check (jsonb_typeof(fields) = 'array')
);

create table entry
(
    id             uuid primary key,
    blueprint_id   uuid        not null references blueprint (id) on delete restrict,
    slug           varchar(96),
    position       integer     not null default 0,
    draft          jsonb       not null,
    published      jsonb,
    published_slug varchar(96),
    published_at   timestamptz,
    revision       bigint      not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    constraint entry_slug_format check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint entry_position_positive check (position >= 0),
    constraint entry_draft_object check (jsonb_typeof(draft) = 'object'),
    constraint entry_published_object check (published is null or jsonb_typeof(published) = 'object'),
    constraint entry_publication_consistent check ((published is null) = (published_at is null)),
    constraint entry_slug_unique unique (blueprint_id, slug),
    constraint entry_published_slug_unique unique (blueprint_id, published_slug)
);

create index entry_listing_idx on entry (blueprint_id, position, created_at);
create index entry_published_listing_idx on entry (blueprint_id, position, created_at) where published_at is not null;

create table asset
(
    id           uuid primary key,
    storage_key  varchar(200) not null unique,
    content_type varchar(50)  not null,
    byte_size    bigint       not null,
    width        integer      not null,
    height       integer      not null,
    sha256       bytea        not null unique,
    filename     varchar(255),
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    constraint asset_content_type check (content_type in ('image/jpeg', 'image/png', 'image/gif', 'image/webp')),
    constraint asset_byte_size_positive check (byte_size > 0),
    constraint asset_dimensions_positive check (width > 0 and height > 0),
    constraint asset_sha256_length check (octet_length(sha256) = 32)
);

create index asset_created_at_idx on asset (created_at desc);

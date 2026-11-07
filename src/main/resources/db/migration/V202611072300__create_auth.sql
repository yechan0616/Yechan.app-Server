create table administrator
(
    id           uuid primary key,
    username     varchar(32)  not null unique,
    display_name varchar(100) not null,
    user_handle  bytea        not null unique,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    constraint administrator_username_format check (username ~ '^[a-z0-9][a-z0-9._-]{2,31}$'),
    constraint administrator_user_handle_length check (octet_length(user_handle) between 16 and 64)
);

create table passkey
(
    id                       uuid primary key,
    administrator_id         uuid         not null references administrator (id) on delete cascade,
    credential_id            bytea        not null unique,
    attested_credential_data bytea        not null,
    attestation_format       varchar(32)  not null,
    backup_eligible          boolean      not null,
    transports               varchar(200) not null,
    sign_count               bigint       not null,
    user_verified            boolean      not null,
    backed_up                boolean      not null,
    label                    varchar(100) not null,
    last_used_at             timestamptz,
    created_at               timestamptz  not null,
    updated_at               timestamptz  not null,
    constraint passkey_credential_id_length check (octet_length(credential_id) between 1 and 1023),
    constraint passkey_sign_count_positive check (sign_count >= 0)
);

create index passkey_administrator_idx on passkey (administrator_id);

create table passkey_ceremony
(
    id               uuid primary key,
    purpose          varchar(20) not null,
    challenge        bytea       not null,
    expires_at       timestamptz not null,
    administrator_id uuid references administrator (id) on delete cascade,
    user_handle      bytea,
    username         varchar(32),
    display_name     varchar(100),
    label            varchar(100),
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    constraint passkey_ceremony_purpose check (purpose in ('ONBOARDING', 'ENROLLMENT', 'SIGN_IN')),
    constraint passkey_ceremony_challenge_length check (octet_length(challenge) >= 16)
);

create index passkey_ceremony_expires_idx on passkey_ceremony (expires_at);

create table studio_session
(
    id               uuid primary key,
    administrator_id uuid        not null references administrator (id) on delete cascade,
    token_hash       bytea       not null unique,
    expires_at       timestamptz not null,
    idle_expires_at  timestamptz not null,
    last_seen_at     timestamptz not null,
    user_agent       varchar(255),
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    constraint studio_session_token_hash_length check (octet_length(token_hash) = 32)
);

create index studio_session_administrator_idx on studio_session (administrator_id);
create index studio_session_idle_expires_idx on studio_session (idle_expires_at);

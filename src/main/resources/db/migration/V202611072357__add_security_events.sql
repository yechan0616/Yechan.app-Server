alter table studio_session
    add column ip_address varchar(45);

create table security_event
(
    id               uuid primary key,
    administrator_id uuid,
    type             varchar(40) not null,
    method           varchar(20),
    ip_address       varchar(45),
    user_agent       varchar(255),
    detail           varchar(200),
    created_at       timestamptz not null,
    updated_at       timestamptz not null
);

create index security_event_administrator_idx on security_event (administrator_id, created_at desc);
create index security_event_created_at_idx on security_event (created_at);

alter table administrator
    add column email               varchar(254) unique,
    add column password_hash       varchar(255),
    add column password_changed_at timestamptz,
    add column failed_sign_ins     integer not null default 0,
    add column locked_until        timestamptz,
    add constraint administrator_email_normalized check (email = lower(trim(email))),
    add constraint administrator_failed_sign_ins_positive check (failed_sign_ins >= 0);

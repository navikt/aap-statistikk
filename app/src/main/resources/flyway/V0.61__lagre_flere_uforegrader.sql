create table grunnlag_uforegrader
(
    id                 SERIAL PRIMARY KEY,
    grunnlag_ufore_id  bigint references grunnlag_ufore (id),
    uforegrad          int  not null,
    virkningstidspunkt date not null
);
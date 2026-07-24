CREATE TABLE enhet
(
    ID   BIGSERIAL NOT NULL PRIMARY KEY,
    kode TEXT      NOT NULL
);

CREATE UNIQUE INDEX enhet_unique_idx ON enhet (kode);

CREATE TABLE saksbehandler
(
    ID        BIGSERIAL NOT NULL PRIMARY KEY,
    nav_ident text      not null
);

CREATE UNIQUE INDEX saksbehandler_unique_idx ON saksbehandler (nav_ident);

CREATE TABLE reservasjon
(
    ID            BIGSERIAL    NOT NULL PRIMARY KEY,
    reservert_av  bigint       not null references saksbehandler,
    opprettet_tid timestamp(3) not null
);


CREATE TABLE oppgave
(
    ID                  BIGSERIAL    NOT NULL PRIMARY KEY,
    PERSON_ID           INT REFERENCES person (id),
    behandling_id       int references behandling (id),
    enhet_id            bigint       not null references enhet (id),
    status              text         NOT NULL,
    opprettet_tidspunkt timestamp(3) not null,
    reservasjon_id      bigint references reservasjon (id)
);

create unique index reservasjon_unique_idx on oppgave (reservasjon_id);

CREATE TABLE oppgave_hendelser
(
    ID                   BIGSERIAL    NOT NULL PRIMARY KEY,
    oppgave_id           bigint references oppgave (id),
    identifikator        bigint       not null,
    type                 text         NOT NULL,
    mottatt_tidspunkt    timestamp(6) NOT NULL,
    person_ident         text,
    saksnummer           text,
    behandling_referanse UUID,
    journalpost_id       bigint,
    enhet                text         not null,
    avklaringsbehov_kode text         not null,
    status               text         not null,
    reservert_av         text,
    reservert_tidspunkt  timestamp(6),
    opprettet_tidspunkt  timestamp(6) not null,
    endret_av            text,
    endret_tidspunkt     timestamp(6)
);
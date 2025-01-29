CREATE TABLE postmottak_behandling
(
    ID              BIGSERIAL    NOT NULL PRIMARY KEY,
    journalpost_id  bigint,
    referanse       uuid         not null,
    person_id       bigint       not null references person (id),
    type_behandling text         not null,
    mottatt_tid     timestamp(3) not null
);

CREATE UNIQUE INDEX unik_postmottak_behandling_referanse ON postmottak_behandling (referanse);

CREATE TABLE postmottak_behandling_historikk
(
    ID                        BIGSERIAL    NOT NULL PRIMARY KEY,
    postmottak_behandling_id  bigint       not null references postmottak_behandling (id),
    oppdatert_tid             timestamp(3) not null,
    gjeldende                 bool         not null,
    siste_saksbehandler       text,
    gjeldende_avklaringsbehov text,
    status                    text         not null
);


CREATE UNIQUE INDEX unik_aktiv_postmottak_historikk_per_id
    ON postmottak_behandling_historikk (postmottak_behandling_id)
    WHERE gjeldende = TRUE;

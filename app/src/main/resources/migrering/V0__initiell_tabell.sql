CREATE TABLE motta_statistikk
(
    ID              BIGSERIAL    NOT NULL PRIMARY KEY,
    saksnummer      VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    behandlingstype VARCHAR(255) NOT NULL
);

CREATE TABLE VILKARSRESULTAT
(
    id              BIGSERIAL NOT NULL PRIMARY KEY,
    saksnummer      TEXT      NOT NULL,
    type_behandling TEXT      NOT NULL
);

CREATE TABLE VILKAR
(
    id              BIGSERIAL NOT NULL PRIMARY KEY,
    vilkar_type     TEXT      NOT NULL,
    vilkarresult_id BIGINT REFERENCES VILKARSRESULTAT
);

CREATE TABLE VILKARSPERIODE
(
    id                 SERIAL PRIMARY KEY,
    fra_dato           TIMESTAMP(3) NOT NULL,
    til_dato           TIMESTAMP(3) NOT NULL,
    utfall             TEXT         NOT NULL,
    manuell_vurdering  BOOLEAN      NOT NULL,
    innvilgelsesaarsak TEXT,
    avslagsaarsak      TEXT,
    vilkar_id          BIGINT REFERENCES VILKAR
);

CREATE TABLE TILKJENT_YTELSE
(
    ID BIGSERIAL NOT NULL PRIMARY KEY
);

CREATE TABLE TILKJENT_YTELSE_PERIODE
(
    ID                 BIGSERIAL      NOT NULL PRIMARY KEY,
    FRA_DATO           TIMESTAMP(3)   NOT NULL,
    TIL_DATO           TIMESTAMP(3)   NOT NULL,
    DAGSATS            NUMERIC(21, 0) NOT NULL,
    GRADERING          SMALLINT       NOT NULL,
    TILKJENT_YTELSE_ID BIGINT         NOT NULL REFERENCES TILKJENT_YTELSE (ID)
);
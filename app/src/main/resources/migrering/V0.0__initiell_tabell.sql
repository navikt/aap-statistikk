CREATE TABLE person
(
    ID    BIGSERIAL          NOT NULL PRIMARY KEY,
    IDENT VARCHAR(19) UNIQUE NOT NULL -- fra DB i behandlingsflyt
);

CREATE TABLE versjon
(
    ID      BIGSERIAL    NOT NULL PRIMARY KEY,
    versjon VARCHAR(100) NOT NULL UNIQUE
);

CREATE INDEX IDX_VERSJON ON versjon (versjon);

CREATE TABLE sak
(
    ID         BIGSERIAL   NOT NULL PRIMARY KEY,
    SAKSNUMMER VARCHAR(19) NOT NULL UNIQUE,
    PERSON_ID  BIGINT      NOT NULL REFERENCES person (id)
);

CREATE TABLE sak_historikk
(
    ID            BIGSERIAL    NOT NULL PRIMARY KEY,
    GJELDENDE     BOOLEAN      NOT NULL,
    oppdatert_tid timestamp(3) not null,
    SAK_ID        BIGINT       NOT NULL REFERENCES sak (id),
    versjon       BIGINT       NOT NULL references versjon (id)
);


CREATE TABLE behandling
(
    ID            BIGSERIAL    NOT NULL PRIMARY KEY,
    SAK_ID        BIGINT       NOT NULL REFERENCES SAK (ID),
    REFERANSE     UUID UNIQUE  NOT NULL,
    TYPE          VARCHAR(100) NOT NULL,
    OPPRETTET_TID TIMESTAMP(3) NOT NULL
);

CREATE TABLE behandling_historikk
(
    ID            BIGSERIAL    NOT NULL PRIMARY KEY,
    behandling_id BIGINT       NOT NULL REFERENCES behandling (id),
    versjon_id    BIGINT       NOT NULL references versjon (id),
    GJELDENDE     BOOLEAN      NOT NULL,
    oppdatert_tid timestamp(3) not null,
    mottatt_tid   timestamp(3) not null,
    status        VARCHAR(20)  not null
);

CREATE TABLE avsluttet_behandling
(
    ID      BIGSERIAL NOT NULL PRIMARY KEY,
    payload TEXT      NOT NULL
);

CREATE TABLE VILKARSRESULTAT
(
    id            BIGSERIAL     NOT NULL PRIMARY KEY,
    behandling_id BIGINT UNIQUE NOT NULL REFERENCES behandling (ID)
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
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    behandling_id BIGINT    NOT NULL REFERENCES behandling (id) UNIQUE
);

CREATE TABLE TILKJENT_YTELSE_PERIODE
(
    ID                 BIGSERIAL      NOT NULL PRIMARY KEY,
    FRA_DATO           TIMESTAMP(3)   NOT NULL,
    TIL_DATO           TIMESTAMP(3)   NOT NULL,
    DAGSATS            NUMERIC(21, 5) NOT NULL,
    GRADERING          NUMERIC(21, 5) NOT NULL,
    TILKJENT_YTELSE_ID BIGINT         NOT NULL REFERENCES TILKJENT_YTELSE (ID)
);

CREATE TABLE GRUNNLAG
(
    ID            BIGSERIAL   NOT NULL PRIMARY KEY,
    type          VARCHAR(10) not null,
    behandling_id BIGINT      NOT NULL REFERENCES behandling (ID)
);

CREATE TABLE GRUNNLAG_11_19
(
    ID              BIGSERIAL      NOT NULL PRIMARY KEY,
    grunnlag_id     BIGINT         NOT NULL REFERENCES GRUNNLAG (ID),
    grunnlag        NUMERIC(21, 5) NOT NULL,
    er6g_begrenset  BOOLEAN        NOT NULL,
    er_gjennomsnitt BOOLEAN        NOT NULL,
    inntekter       JSONB          NOT NULL
);

CREATE TABLE GRUNNLAG_UFORE
(
    ID                                       BIGSERIAL      NOT NULL PRIMARY KEY,
    grunnlag_id                              BIGINT         NOT NULL REFERENCES GRUNNLAG (ID),
    grunnlag                                 NUMERIC(21, 5) NOT NULL,
    type                                     VARCHAR(20)    NOT NULL,
    grunnlag_11_19_id                        BIGINT         NOT NULL REFERENCES GRUNNLAG_11_19,
    uforegrad                                INT            NOT NULL,
    ufore_inntekter_fra_foregaende_ar        JSONB          NOT NULL,
    ufore_ytterligere_nedsatt_arbeidsevne_ar INT            NOT NULL
);

CREATE TABLE GRUNNLAG_YRKESSKADE
(
    ID                                          BIGSERIAL      NOT NULL PRIMARY KEY,
    grunnlag                                    NUMERIC(21, 5) NOT NULL,
    beregningsgrunnlag_id                       BIGINT         NOT NULL,
    beregningsgrunnlag_type                     varchar(10)    NOT NULL,
    terskelverdi_for_yrkesskade                 INT            NOT NULL,
    andel_som_skyldes_yrkesskade                NUMERIC        NOT NULL,
    andel_yrkesskade                            INT            NOT NULL,
    benyttet_andel_for_yrkesskade               INT            NOT NULL,
    andel_som_ikke_skyldes_yrkesskade           NUMERIC        NOT NULL,
    antatt_arlig_inntekt_yrkesskade_tidspunktet NUMERIC        NOT NULL,
    yrkesskade_tidspunkt                        INT            NOT NULL,
    grunnlag_for_beregning_av_yrkesskadeandel   NUMERIC        NOT NULL,
    yrkesskadeinntekt_ig                        NUMERIC        NOT NULL,
    grunnlag_etter_yrkesskade_fordel            NUMERIC        NOT NULL
);
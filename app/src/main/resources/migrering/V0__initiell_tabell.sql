CREATE TABLE motta_statistikk
(
    ID              BIGSERIAL    NOT NULL PRIMARY KEY,
    saksnummer      VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    behandlingstype VARCHAR(255) NOT NULL
);

CREATE TABLE VILKARSRESULTAT
(
    id                   BIGSERIAL NOT NULL PRIMARY KEY,
    saksnummer           TEXT      NOT NULL,
    behandlingsreferanse uuid      NOT NULL UNIQUE,
    type_behandling      TEXT      NOT NULL
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
    ID                   BIGSERIAL   NOT NULL PRIMARY KEY,
    saksnummer           VARCHAR(10) NOT NULL,
    behandlingsreferanse uuid        NOT NULL UNIQUE
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


CREATE TABLE GRUNNLAG_11_19
(
    ID             BIGSERIAL      NOT NULL PRIMARY KEY,
    grunnlag       NUMERIC(21, 5) NOT NULL,
    er6g_begrenset BOOLEAN        NOT NULL,
    inntekter      JSONB          NOT NULL
);

CREATE TABLE GRUNNLAG_UFORE
(
    ID                                       BIGSERIAL      NOT NULL PRIMARY KEY,
    grunnlag                                 NUMERIC(21, 5) NOT NULL,
    er6g_begrenset                           BOOLEAN        NOT NULL,
    type                                     VARCHAR(20)    NOT NULL,
    grunnlag_11_19_id                        BIGINT         NOT NULL REFERENCES GRUNNLAG_11_19,
    uforegrad                                INT            NOT NULL,
    ufore_inntekter_fra_foregaende_ar        JSONB          NOT NULL,
    ufore_inntekt_i_kroner                   NUMERIC        NOT NULL,
    ufore_ytterligere_nedsatt_arbeidsevne_ar INT            NOT NULL
);

CREATE TABLE GRUNNLAG_YRKESSKADE
(
    ID                                          BIGSERIAL      NOT NULL PRIMARY KEY,
    grunnlag                                    NUMERIC(21, 5) NOT NULL,
    er6g_begrenset                              BOOLEAN        NOT NULL,
    beregningsgrunnlag_id                       BIGINT         NOT NULL REFERENCES Grunnlag_11_19,
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

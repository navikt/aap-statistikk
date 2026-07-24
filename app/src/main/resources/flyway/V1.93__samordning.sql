CREATE TABLE samordning_ufore
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id),
    FRA_DATO      DATE      NOT NULL,
    TIL_DATO      DATE      NOT NULL,
    GRAD          INT       NOT NULL
);

CREATE INDEX idx_samordning_ufore_behandling_id ON samordning_ufore (BEHANDLING_ID);

CREATE TABLE samordning_statlig_ytelse
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id),
    FRA_DATO      DATE      NOT NULL,
    TIL_DATO      DATE      NOT NULL,
    YTELSE        TEXT      NOT NULL,
    PROSENT       INT       NOT NULL
);

CREATE INDEX idx_samordning_statlig_ytelse_behandling_id ON samordning_statlig_ytelse (BEHANDLING_ID);

CREATE TABLE samordning_avregning_andre_ytelser
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id),
    FRA_DATO      DATE      NOT NULL,
    TIL_DATO      DATE      NOT NULL,
    YTELSE        TEXT      NOT NULL
);

CREATE INDEX idx_samordning_avregning_behandling_id ON samordning_avregning_andre_ytelser (BEHANDLING_ID);

CREATE TABLE samordning_arbeidsgiver
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id),
    FRA_DATO      DATE      NOT NULL,
    TIL_DATO      DATE      NOT NULL
);

CREATE INDEX idx_samordning_arbeidsgiver_behandling_id ON samordning_arbeidsgiver (BEHANDLING_ID);

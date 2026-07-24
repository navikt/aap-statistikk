CREATE TABLE institusjonsopphold
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id),
    FRA_DATO      DATE      NOT NULL,
    TIL_DATO      DATE      NOT NULL
);

CREATE INDEX idx_institusjonsopphold_behandling_id ON institusjonsopphold (BEHANDLING_ID);

CREATE TABLE vedtatt_stans_opphor
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id),
    TYPE          TEXT      NOT NULL,
    FOM           DATE      NOT NULL
);

CREATE INDEX idx_vedtatt_stans_opphor_behandling_id ON vedtatt_stans_opphor (BEHANDLING_ID);

CREATE TABLE vedtatt_stans_opphor_aarsak
(
    ID                      BIGSERIAL NOT NULL PRIMARY KEY,
    VEDTATT_STANS_OPPHOR_ID BIGINT    NOT NULL REFERENCES vedtatt_stans_opphor (id),
    AARSAK                  TEXT      NOT NULL
);

CREATE TABLE diagnose_periode
(
    id            BIGSERIAL NOT NULL PRIMARY KEY,
    behandling_id BIGINT    NOT NULL REFERENCES behandling (id),
    fra_dato      DATE      NOT NULL,
    til_dato      DATE      NOT NULL,
    kodeverk      TEXT      NOT NULL,
    diagnosekode  TEXT      NOT NULL,
    bidiagnoser   TEXT[]
);

CREATE INDEX idx_diagnose_periode_behandling_id ON diagnose_periode (behandling_id);

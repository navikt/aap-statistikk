CREATE TABLE diagnose
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id),
    kodeverk      text      not null,
    diagnosekode  text      not null,
    bidiagnoser   text[]
);
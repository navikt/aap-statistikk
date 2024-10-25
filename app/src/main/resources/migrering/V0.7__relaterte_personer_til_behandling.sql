CREATE TABLE relaterte_personer
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    behandling_id BIGINT    NOT NULL REFERENCES behandling_historikk (id),
    person_id     BIGINT    NOT NULL REFERENCES person
);

UPDATE behandling_historikk
SET oppdatert_tid = oppdatert_tid + INTERVAL '2 hours';
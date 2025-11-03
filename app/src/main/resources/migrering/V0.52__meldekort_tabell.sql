CREATE TABLE meldekort
(
    id SERIAL PRIMARY KEY,
    behandling_id BIGINT NOT NULL REFERENCES behandling (id),
    journalpost_id VARCHAR(50) NOT NULL
);

CREATE TABLE arbeid_i_periode (
    id SERIAL PRIMARY KEY,
    periode daterange NOT NULL,
    timerArbeidet NUMERIC(5,2) NOT NULL,
    meldekort_id INTEGER NOT NULL REFERENCES meldekort (id)
);
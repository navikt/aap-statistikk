CREATE TABLE behandling_tilbakebetaling
(
    id                        BIGSERIAL PRIMARY KEY,
    sak_id                    BIGINT        NOT NULL REFERENCES sak (id),
    behandling_ref            TEXT          NOT NULL,
    behandling_status         TEXT          NOT NULL,
    sak_opprettet             TIMESTAMP     NOT NULL,
    totalt_feilutbetalt_belop NUMERIC(9, 2) NOT NULL,
    saksbehandling_url        TEXT          NOT NULL,
    opprettet_tid             TIMESTAMP     NOT NULL
);

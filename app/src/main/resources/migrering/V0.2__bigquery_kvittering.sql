ALTER TABLE sak_historikk
    DROP COLUMN versjon;

ALTER TABLE sak_historikk
    ADD COLUMN sak_status VARCHAR(15) NOT NULL default 'OPPRETTET';

CREATE TABLE bigquery_kvittering
(
    ID                     BIGSERIAL    NOT NULL PRIMARY KEY,
    sak_snapshot_id        BIGINT       NOT NULL REFERENCES sak_historikk (id),
    behandling_snapshot_id BIGINT       NOT NULL REFERENCES behandling_historikk (id),
    tidspunkt              timestamp(3) not null
)
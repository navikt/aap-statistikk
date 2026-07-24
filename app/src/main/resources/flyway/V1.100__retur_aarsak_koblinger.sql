CREATE TABLE behandling_historikk_retur_aarsak
(
    id                      BIGSERIAL    NOT NULL PRIMARY KEY,
    behandling_historikk_id BIGINT       NOT NULL REFERENCES behandling_historikk (id),
    avklaringsbehov_kode    TEXT         NOT NULL,
    retur_aarsak            TEXT         NOT NULL
);

CREATE UNIQUE INDEX idx_behandling_historikk_retur_aarsak_unique
    ON behandling_historikk_retur_aarsak (behandling_historikk_id, avklaringsbehov_kode, retur_aarsak);

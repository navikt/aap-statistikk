CREATE INDEX idx_saksstatistikk_endret_tid ON saksstatistikk (endret_tid);
CREATE INDEX idx_saksstatistikk_ansvarlig_enhet ON saksstatistikk (ansvarlig_enhet_kode);
CREATE INDEX idx_saksstatistikk_uuid_endret_teknisk ON saksstatistikk (behandling_uuid, endret_tid, teknisk_tid DESC);

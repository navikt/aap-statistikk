DROP INDEX unik_aktiv_postmottak_historikk_per_id;

CREATE INDEX idx_postmottak_historikk_behandling_id on postmottak_behandling_historikk (postmottak_behandling_id);

CREATE INDEX idx_rettighetstypeperioder_rettighetstype_id
    ON rettighetstypeperioder (rettighetstype_id);

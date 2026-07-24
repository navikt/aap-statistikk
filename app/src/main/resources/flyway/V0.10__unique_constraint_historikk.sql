CREATE UNIQUE INDEX unik_aktiv_historikk_per_id
    ON behandling_historikk (behandling_id)
    WHERE gjeldende = TRUE;

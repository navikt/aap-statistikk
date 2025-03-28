ALTER TABLE behandling_historikk
    ADD COLUMN vedtak_tidspunkt    timestamp(3),
    ADD COLUMN ansvarlig_beslutter text;
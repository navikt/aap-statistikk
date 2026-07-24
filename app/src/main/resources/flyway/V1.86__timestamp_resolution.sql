ALTER TABLE oppgave_hendelser
    ALTER COLUMN sendt_tid TYPE TIMESTAMP(6);

ALTER TABLE oppgave
    ALTER COLUMN opprettet_tidspunkt TYPE TIMESTAMP(6),
    ALTER COLUMN oppdatert_rad TYPE TIMESTAMP(6),
    ALTER COLUMN opprettet_rad TYPE TIMESTAMP(6);

ALTER TABLE behandling_historikk
    ALTER COLUMN oppdatert_tid TYPE TIMESTAMP(6),
    ALTER COLUMN mottatt_tid TYPE TIMESTAMP(6);
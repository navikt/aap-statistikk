-- coalesce brukes fordi to NULL-verdier ikke regnes som like i en unik indeks i Postgres.
CREATE UNIQUE INDEX uidx_diagnose_periode_unik ON diagnose_periode (
    behandling_id,
    fra_dato,
    til_dato,
    kodeverk,
    diagnosekode,
    (coalesce(bidiagnoser, '{}'::text[]))
);

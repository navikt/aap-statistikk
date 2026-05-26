-- Migrerer historiske diagnose-rader til diagnose_periode med datoer fra rettighetstypeperioder.
-- Behandlinger uten rettighetstype-perioder migreres ikke (de finnes fortsatt i diagnose-tabellen).
INSERT INTO diagnose_periode (behandling_id, fra_dato, til_dato, kodeverk, diagnosekode, bidiagnoser)
SELECT
    d.behandling_id,
    MIN(rtp.fra_dato)::date AS fra_dato,
    MAX(rtp.til_dato)::date AS til_dato,
    d.kodeverk,
    d.diagnosekode,
    d.bidiagnoser
FROM diagnose d
    JOIN rettighetstype rt ON rt.behandling_id = d.behandling_id
    JOIN rettighetstypeperioder rtp ON rtp.rettighetstype_id = rt.id
WHERE NOT EXISTS (
    SELECT 1
    FROM diagnose_periode dp
    WHERE dp.behandling_id = d.behandling_id
)
GROUP BY d.behandling_id, d.kodeverk, d.diagnosekode, d.bidiagnoser;

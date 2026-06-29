ALTER TABLE diagnose
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE rettighetstype
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

UPDATE diagnose d
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling b
         JOIN behandling_historikk bh ON bh.behandling_id = b.id
WHERE d.behandling_id = b.id
  AND bh.gjeldende = TRUE;

UPDATE rettighetstype r
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling b
         JOIN behandling_historikk bh ON bh.behandling_id = b.id
WHERE r.behandling_id = b.id
  AND bh.gjeldende = TRUE;

ALTER TABLE diagnose
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE rettighetstype
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE vilkarsresultat
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE tilkjent_ytelse
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE grunnlag
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE samordning_ufore
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE samordning_statlig_ytelse
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE samordning_avregning_andre_ytelser
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE samordning_arbeidsgiver
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

UPDATE vilkarsresultat vr
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE vr.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE tilkjent_ytelse ty
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE ty.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE grunnlag g
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE g.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE samordning_ufore su
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE su.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE samordning_statlig_ytelse ssy
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE ssy.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE samordning_avregning_andre_ytelser saay
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE saay.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE samordning_arbeidsgiver sag
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE sag.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

ALTER TABLE vilkarsresultat
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE tilkjent_ytelse
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE grunnlag
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE samordning_ufore
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE samordning_statlig_ytelse
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE samordning_avregning_andre_ytelser
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE samordning_arbeidsgiver
    ALTER COLUMN oppdatert_tid SET NOT NULL;

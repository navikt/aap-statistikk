ALTER TABLE diagnose
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE diagnose_periode
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE rettighetstype
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE rettighetstypeperioder
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

UPDATE diagnose d
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling b
         JOIN behandling_historikk bh ON bh.behandling_id = b.id
WHERE d.behandling_id = b.id
  AND bh.gjeldende = TRUE;

UPDATE diagnose_periode dp
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling b
         JOIN behandling_historikk bh ON bh.behandling_id = b.id
WHERE dp.behandling_id = b.id
  AND bh.gjeldende = TRUE;

UPDATE rettighetstype r
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling b
         JOIN behandling_historikk bh ON bh.behandling_id = b.id
WHERE r.behandling_id = b.id
  AND bh.gjeldende = TRUE;

UPDATE rettighetstypeperioder rp
SET oppdatert_tid = bh.oppdatert_tid
FROM rettighetstype r
         JOIN behandling b ON r.behandling_id = b.id
         JOIN behandling_historikk bh ON bh.behandling_id = b.id
WHERE rp.rettighetstype_id = r.id
  AND bh.gjeldende = TRUE;

ALTER TABLE diagnose
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE diagnose_periode
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE rettighetstype
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE rettighetstypeperioder
    ALTER COLUMN oppdatert_tid SET NOT NULL;

CREATE OR REPLACE FUNCTION oppdater_oppdatert_tid()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.oppdatert_tid = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER diagnose_oppdatert_tid_trg
    BEFORE UPDATE
    ON diagnose
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER diagnose_periode_oppdatert_tid_trg
    BEFORE UPDATE
    ON diagnose_periode
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER rettighetstype_oppdatert_tid_trg
    BEFORE UPDATE
    ON rettighetstype
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER rettighetstypeperioder_oppdatert_tid_trg
    BEFORE UPDATE
    ON rettighetstypeperioder
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

ALTER TABLE vilkarsresultat
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE vilkar
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE vilkarsperiode
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE tilkjent_ytelse
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE tilkjent_ytelse_periode
    ADD COLUMN oppdatert_tid TIMESTAMP(6);

ALTER TABLE grunnlag
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE grunnlag_11_19
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE grunnlag_yrkesskade
    ADD COLUMN oppdatert_tid TIMESTAMP(6);
ALTER TABLE grunnlag_ufore
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

UPDATE vilkar v
SET oppdatert_tid = vr.oppdatert_tid
FROM vilkarsresultat vr
WHERE v.vilkarresult_id = vr.id;

UPDATE vilkarsperiode vp
SET oppdatert_tid = v.oppdatert_tid
FROM vilkar v
WHERE vp.vilkar_id = v.id;

UPDATE tilkjent_ytelse ty
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE ty.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE tilkjent_ytelse_periode tp
SET oppdatert_tid = ty.oppdatert_tid
FROM tilkjent_ytelse ty
WHERE tp.tilkjent_ytelse_id = ty.id;

UPDATE grunnlag g
SET oppdatert_tid = bh.oppdatert_tid
FROM behandling_historikk bh
WHERE g.behandling_id = bh.behandling_id
  AND bh.gjeldende = TRUE;

UPDATE grunnlag_11_19 g11
SET oppdatert_tid = g.oppdatert_tid
FROM grunnlag g
WHERE g11.grunnlag_id = g.id;

UPDATE grunnlag_yrkesskade gy
SET oppdatert_tid = g.oppdatert_tid
FROM grunnlag g
WHERE gy.beregningsgrunnlag_id = g.id;

UPDATE grunnlag_ufore gu
SET oppdatert_tid = g.oppdatert_tid
FROM grunnlag g
WHERE gu.grunnlag_id = g.id;

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
ALTER TABLE vilkar
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE vilkarsperiode
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE tilkjent_ytelse
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE tilkjent_ytelse_periode
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE grunnlag
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE grunnlag_11_19
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE grunnlag_yrkesskade
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE grunnlag_ufore
    ALTER COLUMN oppdatert_tid SET NOT NULL;

ALTER TABLE samordning_ufore
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE samordning_statlig_ytelse
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE samordning_avregning_andre_ytelser
    ALTER COLUMN oppdatert_tid SET NOT NULL;
ALTER TABLE samordning_arbeidsgiver
    ALTER COLUMN oppdatert_tid SET NOT NULL;

CREATE TRIGGER vilkarsresultat_oppdatert_tid_trg
    BEFORE UPDATE ON vilkarsresultat
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER vilkar_oppdatert_tid_trg
    BEFORE UPDATE ON vilkar
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER vilkarsperiode_oppdatert_tid_trg
    BEFORE UPDATE ON vilkarsperiode
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER tilkjent_ytelse_oppdatert_tid_trg
    BEFORE UPDATE ON tilkjent_ytelse
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER tilkjent_ytelse_periode_oppdatert_tid_trg
    BEFORE UPDATE ON tilkjent_ytelse_periode
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER grunnlag_oppdatert_tid_trg
    BEFORE UPDATE ON grunnlag
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER grunnlag_11_19_oppdatert_tid_trg
    BEFORE UPDATE ON grunnlag_11_19
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER grunnlag_yrkesskade_oppdatert_tid_trg
    BEFORE UPDATE ON grunnlag_yrkesskade
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER grunnlag_ufore_oppdatert_tid_trg
    BEFORE UPDATE ON grunnlag_ufore
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER samordning_ufore_oppdatert_tid_trg
    BEFORE UPDATE ON samordning_ufore
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER samordning_statlig_ytelse_oppdatert_tid_trg
    BEFORE UPDATE ON samordning_statlig_ytelse
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER samordning_avregning_andre_ytelser_oppdatert_tid_trg
    BEFORE UPDATE ON samordning_avregning_andre_ytelser
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

CREATE TRIGGER samordning_arbeidsgiver_oppdatert_tid_trg
    BEFORE UPDATE ON samordning_arbeidsgiver
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppdatert_tid();

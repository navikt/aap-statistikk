ALTER TABLE tilkjent_ytelse_periode
    ALTER COLUMN dagsats type NUMERIC(21, 10);

ALTER TABLE tilkjent_ytelse_periode
    ADD COLUMN redusert_dagsats NUMERIC(21, 10);

UPDATE tilkjent_ytelse_periode
SET redusert_dagsats = dagsats * gradering / 100.0
WHERE redusert_dagsats is null;

ALTER TABLE tilkjent_ytelse_periode
    ALTER COLUMN redusert_dagsats set not null;

ALTER TABLE tilkjent_ytelse_periode
    ADD COLUMN antall_barn INT DEFAULT null;

ALTER TABLE tilkjent_ytelse_periode
    ADD COLUMN barnetillegg_sats NUMERIC(21, 10) NOT NULL DEFAULT 37;

ALTER TABLE tilkjent_ytelse_periode
    ALTER COLUMN barnetillegg_sats DROP DEFAULT;

ALTER TABLE tilkjent_ytelse_periode
    ADD COLUMN barnetillegg NUMERIC(21, 10) DEFAULT null;
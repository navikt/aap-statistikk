ALTER TABLE tilkjent_ytelse_periode
    ALTER COLUMN barnepensjon_dagsats DROP DEFAULT,
    ALTER COLUMN barnepensjon_dagsats DROP NOT NULL,
    ADD COLUMN samordning_gradering DOUBLE PRECISION NULL,
    ADD COLUMN institusjon_gradering DOUBLE PRECISION NULL,
    ADD COLUMN arbeid_gradering DOUBLE PRECISION NULL,
    ADD COLUMN samordning_uforegradering DOUBLE PRECISION NULL,
    ADD COLUMN samordning_arbeidsgiver_gradering DOUBLE PRECISION NULL,
    ADD COLUMN meldeplikt_gradering DOUBLE PRECISION NULL;

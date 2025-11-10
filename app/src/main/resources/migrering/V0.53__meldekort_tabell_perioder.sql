ALTER TABLE arbeid_i_periode
    ADD COLUMN fra_dato DATE,
    ADD COLUMN til_dato DATE;

UPDATE arbeid_i_periode
SET fra_dato = lower(periode)
WHERE fra_dato IS NULL;

UPDATE arbeid_i_periode
SET til_dato = date(upper(periode) - INTERVAL '1' DAY)
WHERE til_dato IS NULL;

ALTER TABLE arbeid_i_periode
    ALTER COLUMN fra_dato SET NOT NULL,
    ALTER COLUMN til_dato SET NOT NULL;

ALTER TABLE arbeid_i_periode
    drop column periode;
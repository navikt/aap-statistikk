ALTER TABLE person
    ADD COLUMN skjermet BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE person_ident
(
    ID        BIGSERIAL   NOT NULL PRIMARY KEY,
    PERSON_ID BIGINT      NOT NULL REFERENCES person (id),
    IDENT     VARCHAR(19) NOT NULL UNIQUE,
    AKTIV     BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX IDX_PERSON_IDENT_PERSON_ID ON person_ident (person_id);

-- En person kan bare ha én aktiv ident om gangen.
CREATE UNIQUE INDEX UIDX_PERSON_IDENT_AKTIV ON person_ident (person_id) WHERE aktiv;

INSERT INTO person_ident (person_id, ident, aktiv)
SELECT id, ident, TRUE
FROM person;

ALTER TABLE relaterte_personer
    ADD COLUMN ident VARCHAR(19),
    ALTER COLUMN person_id DROP NOT NULL;

UPDATE relaterte_personer rp
SET ident = p.ident
FROM person p
WHERE p.id = rp.person_id;

ALTER TABLE relaterte_personer
    ALTER COLUMN ident SET NOT NULL;

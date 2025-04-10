ALTER TABLE grunnlag_11_19
    ALTER COLUMN grunnlag type NUMERIC(21, 10);

ALTER TABLE grunnlag_ufore
    ALTER COLUMN grunnlag type NUMERIC(21, 10);

ALTER TABLE grunnlag_yrkesskade
    ALTER COLUMN grunnlag type NUMERIC(21, 10);

ALTER TABLE behandling
    add constraint unique_uuid unique (referanse_id);
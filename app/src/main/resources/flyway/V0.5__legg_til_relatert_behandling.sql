ALTER TABLE behandling
    ADD COLUMN forrige_behandling_id BIGINT;

ALTER TABLE behandling
    ADD CONSTRAINT fk_behandling_forrige_id
        FOREIGN KEY (forrige_behandling_id) REFERENCES behandling (id);

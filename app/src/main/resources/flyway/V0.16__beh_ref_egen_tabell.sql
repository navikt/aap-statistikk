CREATE TABLE behandling_referanse
(
    ID        BIGSERIAL NOT NULL PRIMARY KEY,
    referanse UUID      not null
);

CREATE UNIQUE INDEX behandling_referanse_unique_idx on behandling_referanse (referanse);


-- Populer
INSERT INTO behandling_referanse (referanse)
SELECT referanse
FROM behandling;

ALTER TABLE behandling
    ADD COLUMN referanse_id bigint references behandling_referanse (id);

UPDATE behandling
SET referanse_id = br.id
from behandling_referanse br
where br.referanse = behandling.referanse;

ALTER TABLE behandling
    alter column referanse_id set not null;

ALTER TABLE behandling
    drop column referanse;

ALTER TABLE oppgave
    drop column behandling_id;

ALTER TABLE oppgave
    ADD COLUMN behandling_referanse_id bigint references behandling_referanse (id);
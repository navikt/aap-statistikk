ALTER TABLE oppgave
    add column identifikator bigint;

ALTER TABLE oppgave
    add column avklaringsbehov text default 'IKKE_SATT';

ALTER TABLE oppgave
    alter column avklaringsbehov set not null;
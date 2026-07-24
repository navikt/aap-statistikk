CREATE INDEX idx_jobb_arkiv_neste_kjoring ON jobb_arkiv (neste_kjoring);
CREATE INDEX idx_jobb_arkiv_opprettet_tid ON jobb_arkiv (opprettet_tid);

-- Delvis indeks på JSON-felter for den vanligste jobbtypen
CREATE INDEX idx_jobb_arkiv_payload_saksnummer
    ON jobb_arkiv ((payload::jsonb ->> 'saksnummer'))
    WHERE type = 'statistikk.lagreHendelse';

CREATE INDEX idx_jobb_arkiv_payload_behandling_referanse
    ON jobb_arkiv ((payload::jsonb ->> 'behandlingReferanse'))
    WHERE type = 'statistikk.lagreHendelse';

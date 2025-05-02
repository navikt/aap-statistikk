ALTER TABLE behandling_historikk
    ADD COLUMN hendelsestidspunkt TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE behandling_historikk
    ALTER COLUMN hendelsestidspunkt SET NOT NULL;
ALTER TABLE behandling_historikk
    ALTER COLUMN hendelsestidspunkt DROP DEFAULT;
-- PS, hopper spesifikt over versjon 0.27, for den ligger i test-scope for å opprette
-- cloudsqliamserviceaccount-rollen, som allerede finnes i GCP.

GRANT
SELECT
    ON ALL TABLES IN SCHEMA public to cloudsqliamserviceaccount;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT
SELECT
    ON TABLES TO cloudsqliamserviceaccount;
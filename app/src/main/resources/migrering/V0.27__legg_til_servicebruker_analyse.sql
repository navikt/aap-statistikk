GRANT
SELECT
    ON ALL TABLES IN SCHEMA public to cloudsqliamserviceaccount;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT
SELECT
    ON TABLES TO cloudsqliamserviceaccount;
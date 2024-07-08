CREATE TABLE motta_statistikk
(
    ID              BIGSERIAL    NOT NULL PRIMARY KEY,
    saksnummer      VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    behandlingstype VARCHAR(255) NOT NULL
);
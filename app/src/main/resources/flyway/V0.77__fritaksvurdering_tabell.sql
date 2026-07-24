CREATE TABLE fritaksvurdering
(
    id            SERIAL PRIMARY KEY,
    behandling_id BIGINT  NOT NULL REFERENCES behandling (id),
    har_fritak    BOOLEAN NOT NULL,
    fra_dato      DATE    NOT NULL,
    til_dato      DATE
);

CREATE INDEX idx_fritaksvurdering_behandling_id ON fritaksvurdering (behandling_id);

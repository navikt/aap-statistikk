CREATE TABLE rettighetstype
(
    ID            BIGSERIAL NOT NULL PRIMARY KEY,
    BEHANDLING_ID BIGINT    NOT NULL REFERENCES behandling (id)
);

CREATE UNIQUE INDEX unik_behandling_id_rettighetstype ON rettighetstype (BEHANDLING_ID);

CREATE TABLE rettighetstypeperioder
(
    ID                BIGSERIAL    NOT NULL PRIMARY KEY,
    rettighetstype_id bigint       not null references rettighetstype,
    fra_dato          TIMESTAMP(3) NOT NULL,
    til_dato          TIMESTAMP(3) NOT NULL,
    rettighetstype    text         not null
)
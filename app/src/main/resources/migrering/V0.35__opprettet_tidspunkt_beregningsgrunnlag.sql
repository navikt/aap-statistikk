ALTER TABLE grunnlag
    ADD COLUMN opprettet_tidspunkt TIMESTAMP(3) not null default current_timestamp;
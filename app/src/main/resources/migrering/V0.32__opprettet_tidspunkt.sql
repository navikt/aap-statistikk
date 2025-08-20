ALTER TABLE tilkjent_ytelse
    ADD COLUMN opprettet_tidspunkt TIMESTAMP(3) not null default now();

ALTER TABLe vilkarsresultat
    ADD COLUMN opprettet_tidspunkt TIMESTAMP(3) not null default now();
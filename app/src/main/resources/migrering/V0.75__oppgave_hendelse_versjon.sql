alter table oppgave_hendelser
    add column versjon   bigint,
    add column sendt_tid timestamp(3);

update oppgave_hendelser
set sendt_tid = mottatt_tidspunkt
where sendt_tid is null;

alter table oppgave_hendelser
    alter column sendt_tid set not null;
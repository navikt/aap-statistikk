insert into kodeverk_vilkar (kode, beskrivelse, gyldig_fra)
values ('INNTEKTSBORTFALL', '§ 11-4 2. ledd', '2026-01-05');

update kodeverk_vilkar
set gyldig_fra = '2025-04-01'
where gyldig_fra = '2025-01-04';
update kodeverk_vilkar
set gyldig_fra = '2025-12-08'
where gyldig_fra = '2025-08-12';
update kodeverk_vilkar
set gyldig_fra = '2025-10-09'
where gyldig_fra = '2025-09-10';

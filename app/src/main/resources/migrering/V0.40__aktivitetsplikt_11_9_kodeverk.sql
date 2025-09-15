INSERT INTO kodeverk_behandlingstype (kode, beskrivelse, gyldig_fra)
values ('Aktivitetsplikt11_9',
        'Vurdere og registrere brudd på aktivitetsplikt § 11-9',
        '09-15-2025');

UPDATE kodeverk_behandlingstype
SET beskrivelse = 'Vurdere og registrere brudd på aktivitetsplikt § 11-7'
WHERE kode = 'Aktivitetsplikt';
CREATE TABLE kodeverk_behandlingstype
(
    ID          BIGSERIAL NOT NULL PRIMARY KEY,
    kode        text      NOT NULL,
    beskrivelse text      NOT NULL,
    gyldig_fra  date      NOT NULL,
    gyldig_til  date
);

    INSERT INTO kodeverk_behandlingstype (kode, beskrivelse, gyldig_fra)
values ('Førstegangsbehandling',
        'Iinitieres når det kommer en søknad om AAP og det ikke eksisterer en aktiv sak på personen søknaden gjelder.',
        '01-04-2025'),
       ('Revurdering',
        'Initieres når det kommer nye opplysninger (ny søknad, meldekort, nye opplysninger fra lege etc.) i en sak der det foreligger et vedtak.',
        '01-04-2025'),
       ('Klage', 'Initieres ved at en bruker klager på et vedtak.', '01-04-2025'),
       ('SvarFraAndreinstans', 'Svar fra andre instans.', '01-04-2025');

CREATE TABLE kodeverk_resultat
(
    ID          BIGSERIAL NOT NULL PRIMARY KEY,
    kode        text      NOT NULL,
    beskrivelse text      NOT NULL,
    gyldig_fra  date      NOT NULL,
    gyldig_til  date
);

INSERT INTO kodeverk_resultat (kode, beskrivelse, gyldig_fra)
values ('INNVILGET', 'Innvilgelse av førstegangsvedtak.', '01-04-2025'),
       ('AVSLAG', 'Avslag ved førstegangssøknad.', '01-04-2025'),
       ('TRUKKET', 'Søknad trukket.', '01-04-2025'),
       ('KLAGE_OPPRETTHOLDES', 'Resultat av klage på vedtak. Originalt vedtak opprettholdes.',
        '01-04-2025'),
       ('KLAGE_AVVIST', 'Klage avvist.', '01-04-2025'),
       ('KLAGE_OMGJØRES', 'Resultat av klage på vedtak. Originalt vedtak omgjøres.', '01-04-2025'),
       ('KLAGE_DELVIS_OMGJØRES', 'Resultat av klage på vedtak. Originalt vedtak omgjøres.',
        '01-04-2025'),
       ('KLAGE_AVSLÅTT', 'Resultat av klage på vedtak. Klagen avslås.', '01-04-2025'),
       ('KLAGE_TRUKKET', 'Klagen ble trukket.', '01-04-2025');

CREATE TABLE kodeverk_rettighetstype
(
    ID          BIGSERIAL NOT NULL PRIMARY KEY,
    kode        text      NOT NULL,
    beskrivelse text      NOT NULL,
    gyldig_fra  date      NOT NULL,
    gyldig_til  date
);

INSERT INTO kodeverk_rettighetstype (kode, beskrivelse, gyldig_fra)
values ('BISTANDSBEHOV', '§ 11-6', '01-04-2025'),
       ('SYKEPENGEERSTATNING', '§ 11-13', '01-04-2025'),
       ('STUDENT', '§ 11-14', '01-04-2025');


CREATE TABLE kodeverk_vilkar
(
    ID          BIGSERIAL NOT NULL PRIMARY KEY,
    kode        text      NOT NULL,
    beskrivelse text      NOT NULL,
    gyldig_fra  date      NOT NULL,
    gyldig_til  date
);

INSERT INTO kodeverk_vilkar (kode, beskrivelse, gyldig_fra)
values ('ALDERSVILKÅRET', '§ 11-4', '01-04-2025'),
       ('SYKDOMSVILKÅRET', '§ 11-5', '01-04-2025'),
       ('BISTANDSVILKÅRET', '§ 11-6', '01-04-2025'),
       ('MEDLEMSKAP', '§ 11-2', '01-04-2025'),
       ('GRUNNLAGET', '§ 11-19', '01-04-2025'),
       ('SYKEPENGEERSTATNING', '§ 11-13', '01-04-2025'),
       ('LOVVALG', '§ 11-3', '01-04-2025'),
       ('SAMORDNING',
        '§ 11-27. Hundre prosent samordning modelleres som avslag inntil virkningstidspunkt er bestemt.',
        '01-04-2025');
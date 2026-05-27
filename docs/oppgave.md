# oppgave

Postgres-tabell i `hendelser.public` for gjeldende oppgavetilstand.

## Formål

Tabellen viser siste kjente versjon av en oppgave: status, enhet, eventuell reservasjon,
behandlingsreferanse og hvem oppgaven gjelder.

## Hvor data kommer fra

Oppgaver kommer som hendelser fra oppgave-appen og lagres i statistikkappen som gjeldende
oppgavetilstand.

Når en oppgave endrer seg, oppdateres raden med de nyeste verdiene. Tabellen fungerer derfor som en
gjeldende visning av oppgaven, ikke som full historikk.

## Kolonner

| Kolonne                   | Type        | Nullable | Beskrivelse                                    |
| ------------------------- | ----------- | -------- | ---------------------------------------------- |
| `id`                      | `bigserial` | no       | Primærnøkkel                                   |
| `person_id`               | `int4`      | yes      | Personen oppgaven gjelder                      |
| `enhet_id`                | `int8`      | no       | Ansvarlig enhet                                |
| `status`                  | `text`      | no       | Oppgavestatus                                  |
| `opprettet_tidspunkt`     | `timestamp` | no       | Når oppgaven ble opprettet                     |
| `reservasjon_id`          | `int8`      | yes      | Eventuell reservasjon                          |
| `behandling_referanse_id` | `int8`      | yes      | Behandlingsreferanse                           |
| `identifikator`           | `int8`      | yes      | Ekstern oppgave-id                             |
| `avklaringsbehov`         | `text`      | no       | Avklaringsbehovskode                           |
| `har_hastemarkering`      | `bool`      | yes      | Hastemarkering                                 |
| `opprettet_rad`           | `timestamp` | yes      | Når raden ble opprettet i statistikkappen      |
| `oppdatert_rad`           | `timestamp` | yes      | Når raden sist ble oppdatert i statistikkappen |

## Hvordan radene skrives

Når statistikkappen mottar en oppgave, sørger den for at nødvendige støtteobjekter som person,
enhet og eventuell reservasjon finnes. Deretter lagres oppgaven som en ny rad, eller eksisterende
rad oppdateres dersom oppgaven allerede finnes.

## Nøkler og indekser

- Primærnøkkel: `oppgave_pkey`
- Indeks: `idx_oppgave_behandling_referanse`
- Indeks: `idx_oppgave_enhet`
- Indeks: `idx_oppgave_identifikator_oppgave`
- Indeks: `idx_oppgave_person`
- Unik indeks: `reservasjon_unique_idx` på `reservasjon_id`

## Fremmednøkler

- `person_id` -> `person.id`
- `enhet_id` -> `enhet.id`
- `reservasjon_id` -> `reservasjon.id`
- `behandling_referanse_id` -> `behandling_referanse.id`

# oppgave_hendelser

Postgres-tabell i `hendelser.public` for hendelseshistorikken til oppgaver.

## Formål

Tabellen lagrer én rad per oppgavehendelse. Den brukes som historikkgrunnlag for oppgaver, og den
gjør det mulig å bygge opp gjeldende tilstand ved å lese hendelsene i rekkefølge.

Hendelsene kommer fra oppgave-appen og lagres én og én i statistikkappen.

## Hvor data kommer fra

Hver rad representerer en innkommende hendelse fra oppgaveflyten. Feltene beskriver hva som skjedde
da hendelsen kom inn, og hva den tilhørte.

## Kolonner

| Kolonne                | Type        | Nullable | Beskrivelse                         |
| ---------------------- | ----------- | -------- | ----------------------------------- |
| `id`                   | `bigserial` | no       | Primærnøkkel                        |
| `identifikator`        | `int8`      | no       | Oppgave-id hendelsen tilhører       |
| `type`                 | `text`      | no       | Hendelsestype                       |
| `mottatt_tidspunkt`    | `timestamp` | no       | Når hendelsen ble mottatt           |
| `person_ident`         | `text`      | yes      | Personident                         |
| `saksnummer`           | `text`      | yes      | Saksnummer                          |
| `behandling_referanse` | `uuid`      | yes      | Behandlingsreferanse                |
| `journalpost_id`       | `int8`      | yes      | Journalpost-id                      |
| `enhet`                | `text`      | no       | Enhetskode                          |
| `avklaringsbehov_kode` | `text`      | no       | Avklaringsbehovskode                |
| `status`               | `text`      | no       | Oppgavestatus                       |
| `reservert_av`         | `text`      | yes      | Hvem som reserverte oppgaven        |
| `reservert_tidspunkt`  | `timestamp` | yes      | Når oppgaven ble reservert          |
| `opprettet_tidspunkt`  | `timestamp` | no       | Når oppgaven ble opprettet i kilden |
| `endret_av`            | `text`      | yes      | Hvem som endret oppgaven            |
| `endret_tidspunkt`     | `timestamp` | yes      | Når oppgaven ble endret i kilden    |
| `har_hastemarkering`   | `bool`      | yes      | Hastemarkering                      |
| `versjon`              | `int8`      | yes      | Eventversjon                        |
| `sendt_tid`            | `timestamp` | no       | Når hendelsen ble sendt             |

## Hvordan radene skrives

Tabellen er append-only. Hver gang en ny oppgavehendelse kommer inn, settes det inn én ny rad.
Senere kan oppgavehistorikken leses tilbake og brukes til å beregne gjeldende oppgavetilstand.

## Nøkler og indekser

- Primærnøkkel: `oppgave_hendelser_pkey`
- Indeks: `idx_oppgave_hendelser_avklaringsbehov_kode`
- Indeks: `idx_oppgave_hendelser_referanse`
- Indeks: `idx_oppgave_identifikator`

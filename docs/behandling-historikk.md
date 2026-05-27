# behandling_historikk

Postgres-tabell i `hendelser.public` for historikk for en behandling.

## Formål

Tabellen viser hvordan en behandling har utviklet seg over tid. Den inneholder både statusfelter
og metadata som beskriver siste kjente tilstand.

## Hvor data kommer fra

Radene bygges i statistikkappen når den mottar en `StoppetBehandling` fra behandlingsflyt via
REST. Den innkommende behandlingshendelsen brukes til å sette feltene i historikkraden.

Når en behandling har flere hendelser, kan tabellen også få flere historikkrader som viser
utviklingen steg for steg.

## Kolonner

| Kolonne                                    | Type           | Nullable | Beskrivelse                               |
| ------------------------------------------ | -------------- | -------- | ----------------------------------------- |
| `id`                                       | `bigserial`    | no       | Primærnøkkel                              |
| `behandling_id`                            | `int8`         | no       | Referanse til behandlingen                |
| `versjon_id`                               | `int8`         | no       | Versjonen raden ble lagret med            |
| `gjeldende`                                | `bool`         | no       | Viser hvilken rad som er gjeldende        |
| `oppdatert_tid`                            | `timestamp`    | no       | Når raden ble oppdatert i statistikkappen |
| `mottatt_tid`                              | `timestamp`    | no       | Når hendelsen ble mottatt                 |
| `status`                                   | `varchar(20)`  | no       | Behandlingens status                      |
| `siste_saksbehandler`                      | `varchar(100)` | yes      | Sist registrerte saksbehandler            |
| `gjeldende_avklaringsbehov`                | `varchar(50)`  | yes      | Avklaringsbehov som er aktivt nå          |
| `soknadsformat`                            | `varchar(50)`  | yes      | Søknadsformat                             |
| `venteaarsak`                              | `varchar(100)` | yes      | Venteårsak                                |
| `steggruppe`                               | `varchar(100)` | yes      | Steggruppe                                |
| `vedtak_tidspunkt`                         | `timestamp`    | yes      | Vedtakstidspunkt                          |
| `ansvarlig_beslutter`                      | `text`         | yes      | Ansvarlig beslutter                       |
| `retur_aarsak`                             | `text`         | yes      | Returårsak                                |
| `gjeldende_avklaringsbehov_status`         | `text`         | yes      | Status for aktivt avklaringsbehov         |
| `resultat`                                 | `text`         | yes      | Resultat                                  |
| `hendelsestidspunkt`                       | `timestamp`    | no       | Tidspunktet hendelsen skjedde             |
| `slettet`                                  | `bool`         | no       | Om raden er markert som slettet           |
| `utbetaling_id`                            | `text`         | yes      | Utbetalings-id                            |
| `relatert_behandling_referanse`            | `text`         | yes      | Referanse til relatert behandling         |
| `sist_loste_avklaringsbehov`               | `text`         | yes      | Siste avklaringsbehov som ble løst        |
| `sist_loste_avklaringsbehov_saksbehandler` | `text`         | yes      | Saksbehandler som løste behovet           |
| `sist_loste_avklaringsbehov_tidspunkt`     | `timestamp`    | yes      | Når behovet ble løst                      |

## Forskjellen på `gjeldende_avklaringsbehov` og `sist_loste_avklaringsbehov`

- `gjeldende_avklaringsbehov` er avklaringsbehovet saken står i akkurat nå.
- `sist_loste_avklaringsbehov` er det siste behovet som ble avsluttet eller løst.

Kort sagt:

- `gjeldende_avklaringsbehov` = hva saken trenger avklaring på nå
- `sist_loste_avklaringsbehov` = hva som nylig ble avklart

## Hvordan radene skrives

Statistikkappen oppdaterer historikken når den får nye behandlingshendelser. Når det finnes en ny
tilstand for samme behandling, markeres tidligere historikk som ikke-gjeldende og en ny rad settes
inn med den oppdaterte tilstanden.

## Nøkler og indekser

- Primærnøkkel: `behandling_historikk_pkey`
- Unik indeks: `unik_aktiv_historikk_per_id` på `behandling_id`
- Indeks: `idx_behandling_historikk_behandling_id`
- Indeks: `idx_behandling_historikk_slettet`
- Indeks: `idx_behandling_versjon_id`

## Fremmednøkler

- `behandling_id` -> `behandling.id`
- `versjon_id` -> `versjon.id`

# Kvalitetssjekker

Alle avsluttede behandlinger skal ha vedtak_tid.

```postgresql
select *
from saksstatistikk_gjeldende_hendelser
where vedtak_tid is null
  and behandling_status = 'AVSLUTTET'
```

`gjeldende = true` skal også være siste når sortert på hendelsestidspunkt, oppdatert_tid. Forventer
null treff.

```postgresql
with siste_sortert as (select *
                       from (select *,
                                    row_number()
                                    over (partition by behandling_id order by hendelsestidspunkt desc, oppdatert_tid desc, id desc) as rnk
                             from behandling_historikk bh
                             where slettet = false) x
                       where rnk = 1)
select *
from siste_sortert
where gjeldende = false
order by behandling_id desc
```

Tilfeller av race condition.

```postgresql
select *
from saksstatistikk_gjeldende_hendelser s
where vedtak_tid is null
  and behandling_status = 'IVERKSETTES'
  and behandlingmetode = 'AUTOMATISK'
  and not exists (select 1
                  from saksstatistikk_gjeldende_hendelser s2
                  where s2.behandling_status = 'AVSLUTTET'
                    and s.behandling_uuid = s2.behandling_uuid);
```

Kun ytelsesbehandlinger skal ha utbetaling_id (todo, fiks):

```postgresql
select *
from behandling_historikk bh
         join behandling b on bh.behandling_id = b.id
where utbetaling_id is not null
  and b.type not in ('Førstegangsbehandling', 'Revurdering')
```

Siste resultat skal være likt i behandling_historikk og saksstatistikk (fikset 40 stk ca 4/6-26):

```postgresql
with siste_melding as (select s.*,
                              row_number()
                              over (partition by behandling_uuid order by endret_tid desc) as rnk2
                       from saksstatistikk_gjeldende_hendelser s),
     siste_historikk as (select bh.*, br.referanse as behandling_uuid
                         from behandling b
                                  join public.behandling_historikk bh on b.id = bh.behandling_id
                                  join behandling_referanse br on b.referanse_id = br.id
                         where bh.gjeldende = true
                           and bh.status = 'AVSLUTTET')
select siste_historikk.resultat, siste_melding.behandling_resultat, *
from siste_melding
         join siste_historikk on siste_historikk.behandling_uuid = siste_melding.behandling_uuid
         left join (select behandling_id, array_agg(rp.rettighetstype)
                    from rettighetstype r
                             join rettighetstypeperioder rp on r.id = rp.rettighetstype_id
                    group by behandling_id) rps on rps.behandling_id = siste_historikk.behandling_id
where behandling_type != 'KLAGE'
  and behandling_type != 'SVARFRAANDREINSTANS'
  and behandling_type != 'AKTIVITETSPLIKT'
  and siste_historikk.resultat != (case
                                       when siste_melding.behandling_resultat like 'AAP_%'
                                           then 'INNVILGET'
                                       else behandling_resultat end)
  and siste_melding.rnk2 = 1;
```

Siste status skal være likt i behandling_historikk og saksstatistikk:

```postgresql
with siste_melding as (select s.*,
                              row_number()
                              over (partition by behandling_uuid order by endret_tid desc) as rnk2
                       from saksstatistikk_gjeldende_hendelser s),
     siste_historikk as (select bh.*, br.referanse as behandling_uuid
                         from behandling b
                                  join public.behandling_historikk bh on b.id = bh.behandling_id
                                  join behandling_referanse br on b.referanse_id = br.id
                         where bh.gjeldende = true
                           and bh.status = 'AVSLUTTET')
select siste_historikk.resultat, siste_melding.behandling_resultat, *
from siste_melding
         join siste_historikk on siste_historikk.behandling_uuid = siste_melding.behandling_uuid
where behandling_type != 'KLAGE'
  and behandling_type != 'SVARFRAANDREINSTANS'
  and siste_historikk.status != behandling_status
  and siste_melding.rnk2 = 1;
```

Alle førstegangsbehandlinger skal ha resultat:

```postgresql
select behandling_type, count(*)
from saksstatistikk_gjeldende_hendelser
where (lower(behandling_resultat) = 'udefinert'
    or behandling_resultat is null)
  and behandling_status = 'AVSLUTTET'
group by behandling_type
```

Inkonsistent i `behandling_historikk` vs `saksstatistikk_gjeldende_hendelser`. Vil tidvis være
inkonsistent, siden oppdatering skjer i jobber.

```postgresql
with siste_melding as (select s.*,
                              row_number()
                              over (partition by behandling_uuid order by endret_tid desc) as rnk2
                       from saksstatistikk_gjeldende_hendelser s)
select *
from siste_melding
where behandling_status != 'AVSLUTTET'
  and behandling_type != 'KLAGE'
  and behandling_type != 'SVARFRAANDREINSTANS'
  and exists (select 1
              from behandling b
                       join public.behandling_historikk bh on b.id = bh.behandling_id
                       join behandling_referanse br on b.referanse_id = br.id
              where bh.gjeldende = true
                and bh.status = 'AVSLUTTET'
                and br.referanse = siste_melding.behandling_uuid)
  and siste_melding.rnk2 = 1;
```

## Soda v4 Contracts

De første sjekkene er implementert i `.nais/soda-contracts/` og deployes sammen
med BigQuery-ressursene fra `.github/workflows/deploy_bigquery.yml`.

Implementasjonen dekker:

1. `view_gjeldende_hendelser_saksstatistikk` har rader og `behandling_uuid`.
2. Avsluttede behandlinger skal ha `vedtak_tid`.
3. Avsluttede behandlinger skal ha `behandling_resultat` som ikke er null eller
   `UDEFINERT`.
4. Automatisk `IVERKSETTES` uten `vedtak_tid` skal ha en avsluttet hendelse.
5. `utbetaling_id` skal bare finnes på `Førstegangsbehandling` og `Revurdering`
   i `view_behandlinger`.

Sjekker som gjelder ett felt er uttrykt som standard `missing`-sjekker med
`filter`. Sjekker som allerede er formulert som "forventer null treff", er
uttrykt som `failed_rows`, der spørringen returnerer radene som feiler.

Neste naturlige steg er å flytte de mer komplekse konsistenssjekkene mellom
`public_behandling_historikk` og `saksstatistikk` til `failed_rows` mot
BigQuery-tabellene i `datastream_hendelser` og viewene i
`saksstatistikk`/`ytelsestatistikk`.

## Nyttige spørringer

Alle mulige status-sekvenser, gruppert

```postgresql
with x as (WITH filtered_rows AS (SELECT behandling_id,
                                         hendelsestidspunkt,
                                         oppdatert_tid,
                                         id,
                                         status
                                  FROM behandling_historikk
                                  WHERE slettet = false),
                status_changes AS (SELECT behandling_id,
                                          hendelsestidspunkt,
                                          oppdatert_tid,
                                          id,
                                          status,
                                          LAG(status)
                                          OVER (PARTITION BY behandling_id ORDER BY hendelsestidspunkt, oppdatert_tid, id) as prev_status
                                   FROM filtered_rows)
           SELECT behandling_id,
                  STRING_AGG(status, ' → '
                             ORDER BY hendelsestidspunkt, oppdatert_tid, id) as status_sequence
           FROM status_changes
           WHERE status != prev_status
              OR prev_status IS NULL
           GROUP BY behandling_id
           ORDER BY behandling_id)
select status_sequence, count(*)
from x
group by status_sequence
order by count(*) desc;
```

Finn eksempler på ugyldige (fikset):

```postgresql
with x as (WITH filtered_rows AS (SELECT behandling_id,
                                         hendelsestidspunkt,
                                         oppdatert_tid,
                                         id,
                                         status
                                  FROM behandling_historikk
                                  WHERE slettet = false),
                status_changes AS (SELECT behandling_id,
                                          hendelsestidspunkt,
                                          oppdatert_tid,
                                          id,
                                          status,
                                          LAG(status)
                                          OVER (PARTITION BY behandling_id ORDER BY hendelsestidspunkt, oppdatert_tid, id) as prev_status
                                   FROM filtered_rows)
           SELECT behandling_id,
                  STRING_AGG(status, ' → '
                             ORDER BY hendelsestidspunkt, oppdatert_tid, id) as status_sequence
           FROM status_changes
           WHERE status != prev_status
              OR prev_status IS NULL
           GROUP BY behandling_id
           ORDER BY behandling_id)
select *
from x
where status_sequence in ('UTREDES → IVERKSETTES → AVSLUTTET → IVERKSETTES',
                          'UTREDES → IVERKSETTES → AVSLUTTET → IVERKSETTES → AVSLUTTET',
                          'UTREDES → IVERKSETTES → AVSLUTTET → IVERKSETTES → AVSLUTTET → IVERKSETTES → AVSLUTTET',
                          'UTREDES → IVERKSETTES → UTREDES → AVSLUTTET',
    --'IVERKSETTES',
                          'UTREDES → AVSLUTTET → UTREDES',
                          'UTREDES → IVERKSETTES → UTREDES → IVERKSETTES → UTREDES → IVERKSETTES → AVSLUTTET',
                          'UTREDES → IVERKSETTES → AVSLUTTET → IVERKSETTES',
                          'UTREDES → IVERKSETTES → UTREDES → AVSLUTTET',
                          'UTREDES → IVERKSETTES → UTREDES → IVERKSETTES → AVSLUTTET')
```

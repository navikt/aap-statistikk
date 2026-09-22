# Runbook

## Manglende diagnose

Kontekst: https://nav-it.slack.com/archives/C07NKPFFELT/p1772445387956509

I saker som hadde blitt stoppet eller hadde perioder uten rett, ble det noen ganger ikke sendt diagnose til statistikk. I tillegg lagret vi ikke diagnose for studenter.

For å unngå hull i ble det i de tilfellene vi hadde data etterfylt manuelt (siden det var snakk om kun ca 12 saker). Her er en insert som ble brukt:

```sql
insert
into diagnose (behandling_id, kodeverk, diagnosekode, bidiagnoser)
select beh_id, kodeverk, diagnosekode, bidiagnoser
from (with per_sak as (select sak_id,
                              array_agg(b.id)                              as behandlinger,
                              (jsonb_agg(
                               jsonb_build_object('kodeverk', kodeverk, 'diagnosekode',
                                                  diagnosekode,
                                                  'bidiagnoser', bidiagnoser))
                               filter ( where kodeverk is not null )) -> 0 as diagnoser
                       from behandling b
                                left join diagnose d on b.id = d.behandling_id
                       where (b.sak_id in (select id
                                           from sak
                                           where saksnummer in (
                                                                '4M1WWJK',
                                                                '4LDZA0W',
                                                                '4LZ33F4',
                                                                '4MPZVYo',
                                                                '4LVPZUo',
                                                                '4LSCWA8',
                                                                '4NS5RU8',
                                                                '4ML1ZN4',
                                                                '4LM5ZLC',
                                                                '4N70VV4',
                                                                '4LVE7GW',
                                                                '4N3XFio',
                                                                '4LE4MXC'
                                               )))
                       group by sak_id)
      select beh_id,
             diagnoser ->> 'kodeverk'     as kodeverk,
             diagnoser ->> 'diagnosekode' as diagnosekode,
             case
                 when diagnoser -> 'bidiagnoser' is null then null::text[]
                 else array(select jsonb_array_elements_text(diagnoser -> 'bidiagnoser'))
                 end                      as bidiagnoser
      from per_sak
               cross join lateral unnest(per_sak.behandlinger) as u(beh_id)
      where beh_id not in (select b.id
                           from behandling b
                                    join sak s on b.sak_id = s.id
                                    join diagnose d on b.id = d.behandling_id
                           where s.saksnummer in (
                                                  '4M1WWJK',
                                                  '4LDZA0W',
                                                  '4LZ33F4',
                                                  '4MPZVYo',
                                                  '4LVPZUo',
                                                  '4LSCWA8',
                                                  '4NS5RU8',
                                                  '4ML1ZN4',
                                                  '4LM5ZLC',
                                                  '4N70VV4',
                                                  '4LVE7GW',
                                                  '4N3XFio',
                                                  '4LE4MXC'
                               )
                           order by sak_id, b.id)
        and diagnoser ->> 'kodeverk' is not null) s
```

## Krasj pga manglende enhet

Jobber til saksstatistikk krasjer om den ikke klarer å utlede enhet. Noen ganger skjer dette pga treghet i oppgave, og rekjøring burde fikse problemet.

Andre ganger klarer den ikke utlede faktisk enhet, og da burde det debugges.

Eksempel-stacktrace:

```
java.lang.IllegalStateException: Enhet mangler fortsatt etter 3 forsøk for behandling BehandlingId(id=79911), avklaringsbehov=AVKLAR_LOVVALG_MEDLEMSKAP(kode='5017').
	at no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobbUtfører.utførJobb(LagreSakinfoTilBigQueryJobb.kt:122)
```


Nå vil jeg undersøke `oppgave_hendelser`-tabellen i statistikk-appen for å se om vi har noen oppgaver med dette avklaringsbehovet.

Først finne behandlingsreferanse og saksnummer:

```sql
select *
from behandling b
         join behandling_referanse br on b.referanse_id = br.id
join sak s on b.sak_id = s.id
where b.id = 79911;
```

Finn oppgaver:
```sql
select *
from oppgave_hendelser
where behandling_referanse = 'd3c342c9-8952-4490-bbb7-c637dd4d773b'
```

Her var det ingen! Så da åpnet jeg Paw Patrol og fant ut at det var endel jobber på denne som krasjet i behandlingsflyt. Jeg rekjørte dem, og rekjørte deretter i statistikk. Da løste dette seg. 

## Finne eldste åpne behandlinger

BigQuery-spørring for å finne de `n` elste behandlingene.

```sql
WITH
  siste_melding AS (
    SELECT
      *,
      row_number()
        OVER (PARTITION BY behandling_uuid ORDER BY endretTid DESC) AS siste
    FROM `aap-prod-9adc.saksstatistikk.view_gjeldende_hendelser_saksstatistikk`
  )
SELECT *
FROM siste_melding
WHERE siste = 1 AND behandling_status != 'AVSLUTTET' and behandling_status != 'IVERKSETTES'
ORDER BY endretTid ASC
limit 50
```

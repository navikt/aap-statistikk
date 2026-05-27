select *
from jobb
where id = 5082070;

select *
from behandling b
         join behandling_referanse br on b.referanse_id = br.id
where b.id = 30952

select *
from oppgave_hendelser
where behandling_referanse = '4b7cff00-0ce0-489e-9f3a-998be6033889';

select *
from jobb
where sak_id = 9422;

select *
from behandling b
         join behandling_historikk bh on b.id = bh.behandling_id
where b.id = 30952;

select *
from saksstatistikk_gjeldende_hendelser
where behandling_uuid = '4b7cff00-0ce0-489e-9f3a-998be6033889'

---


select distinct behandling_uuid
from saksstatistikk_gjeldende_hendelser
where ansvarlig_enhet_kode is null

select ansvarlig_enhet_kode, behandlingmetode, *
from saksstatistikk_gjeldende_hendelser
where behandling_uuid in (select distinct behandling_uuid
                          from saksstatistikk_gjeldende_hendelser
                          where ansvarlig_enhet_kode is null);

insert
into saksstatistikk (fagsystem_navn, behandling_uuid, saksnummer, relatert_behandling_uuid,
                     relatert_fagsystem, behandling_type, aktor_id, teknisk_tid,
                     registrert_tid, endret_tid, mottatt_tid, vedtak_tid,
                     ferdigbehandlet_tid, versjon, avsender, opprettet_av,
                     ansvarlig_beslutter, soknadsformat, saksbehandler, behandlingmetode,
                     behandling_status, behandling_aarsak, behandling_resultat,
                     resultat_begrunnelse, ansvarlig_enhet_kode, sak_ytelse)
select fagsystem_navn,
       behandling_uuid,
       saksnummer,
       relatert_behandling_uuid,
       relatert_fagsystem,
       behandling_type,
       aktor_id,
       now(),
       registrert_tid,
       endret_tid,
       mottatt_tid,
       vedtak_tid,
       ferdigbehandlet_tid,
       versjon,
       avsender,
       opprettet_av,
       ansvarlig_beslutter,
       soknadsformat,
       saksbehandler,
       behandlingmetode,
       behandling_status,
       behandling_aarsak,
       behandling_resultat,
       resultat_begrunnelse,
       ny_enhet,
       sak_ytelse
from (values ('eb1c1a3b-d9d5-456c-9a73-f68595b41279'::uuid, '0326', 'MANUELL'),
             ('eb1c1a3b-d9d5-456c-9a73-f68595b41279'::uuid, '0326', 'MANUELL'),
             ('19e64043-a385-4e31-b07b-4d7cca493dfd'::uuid, '4491', 'MANUELL'),
             ('25c33b2e-6146-4087-b371-e3c8889d4672', '0427', 'MANUELL'),
             ('2c50843e-fae6-4e29-9162-00dbfe42d915', '4491', 'MANUELL'),
             ('314613d2-00b4-41ba-8d55-e5f3def9469e', '0904', 'MANUELL'),
             ('314613d2-00b4-41ba-8d55-e5f3def9469e', '1000', 'KVALITETSSIKRING'),
             ('3f7765f7-07d5-40cb-83df-2ba5ae612f9e', '4491', 'FATTE_VEDTAK'),
             ('400f21aa-7bc3-48e7-814f-d4fb46f51018', '5701', 'MANUELL'),
             ('2673d702-bdba-4031-939e-1f8a8bb3ffd5', '0300', 'KVALITETSSIKRING'),
             ('47d9342a-98a3-4de3-b33e-2737922851eb', '0300', 'KVALITETSSIKRING'),
             ('38e5e358-f3fb-4d1e-a8b7-79196a273708', '0300', 'KVALITETSSIKRING'),
             ('b030af68-f505-4315-b301-620cb1a25d35', '1000', 'KVALITETSSIKRING'),
             ('4b701b34-5cf6-4546-89e7-310f87e791c8', '1520', 'MANUELL'),
             ('4b701b34-5cf6-4546-89e7-310f87e791c8', '1500',
              'KVALITETSSIKRING'),
             ('4c96b1c1-3fe0-466f-8c34-1c3ecbe90f2f', '1149',
              'MANUELL'),
             ('6a2afb88-258b-4f45-b933-6e5962239c6a', '0501',
              'MANUELL'),
             ('6b12560b-d5d8-462b-992a-7ded26dffd39', '4491',
              'MANUELL'),
             ('6b727b4b-cae6-468d-be9f-ad4b8c2a9b5f', '0326',
              'MANUELL'),
             ('8446cc50-6a96-4c1f-a88b-55cac23bd66a', '0237',
              'MANUELL'),
             ('88159748-c61d-475b-9ac6-32d52612838c', '0230',
              'MANUELL'),
             ('8c76561d-c589-4a25-9644-6ff4810885b4', '4491',
              'FATTE_VEDTAK'),
             ('92f096e2-34ed-4fa8-972d-c36627bc37df', '0491',
              'MANUELL'),
             ('9970d5ab-021d-495a-973f-3e9131d77021', '4491',
              'MANUELL'),
             ('99e20c53-ebeb-4bf4-9431-4edf34d68884', '4491',
              'MANUELL'),
             ('9c817a9f-dfa2-42d4-84bd-83ceea3d1a40', '4491',
              'MANUELL'),
             ('b4b11aee-8fdd-44c0-a861-f94372a0dfff', '4491',
              'MANUELL'),
             ('b5e43f81-bfa8-41d1-bb69-a3aa197e1b40', '4491',
              'FATTE_VEDTAK'),
             ('b65d520f-2f43-47d9-92bb-00de45c50371', '4491',
              'MANUELL'),
             ('b65d520f-2f43-47d9-92bb-00de45c50371', '4491',
              'FATTE_VEDTAK'),
             ('c0e77a70-060e-49a4-9b42-8794599172cf', '4491',
              'FATTE_VEDTAK'),
             ('cf9a321d-af1c-4c47-b487-1e4cfa55c7ea', '4491',
              'MANUELL'),
             ('d89374ef-65d5-4043-9410-8e52ec5d70a3', '4491',
              'MANUELL'),
             ('df6b693a-46d0-485f-9f23-c69a27970e0f', '0604', 'MANUELL'),
             ('df6b693a-46d0-485f-9f23-c69a27970e0f', '0600',
              'KVALITETSSIKRING'),
             ('e78e9008-874d-4c3b-9c70-2d83a1b40971', '0326',
              'MANUELL')) as data(buid, ny_enhet, metode)
         cross join lateral (
    select *
    from saksstatistikk_gjeldende_hendelser ss
    where ss.behandling_uuid = data.buid
      and ss.ansvarlig_enhet_kode is null
      and behandlingmetode = metode
    order by endret_tid desc, teknisk_tid desc
    limit 1)
returning id;
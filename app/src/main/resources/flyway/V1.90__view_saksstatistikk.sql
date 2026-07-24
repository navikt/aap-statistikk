create view saksstatistikk_gjeldende_hendelser as
(
with siste_hendelse as (select s.*,
                               row_number()
                               over (partition by behandling_uuid, endret_tid order by teknisk_tid desc) as rnk
                        from saksstatistikk s)
select *
from siste_hendelse
where rnk = 1
    );
package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.person.Person

class OppgaveRepository(private val dbConnection: DBConnection) {
    fun lagreOppgave(oppgave: Oppgave): Long {
        require(oppgave.enhet.id != null)

        val reservasjonId = if (oppgave.reservasjon != null) {
            val settInnReservasjonSql = """
    INSERT INTO reservasjon (reservert_av, opprettet_tid)
    VALUES (?, ?)""";

            dbConnection.executeReturnKey(settInnReservasjonSql) {
                setParams {
                    setLong(1, oppgave.reservasjon.reservertAv.id)
                    setLocalDateTime(2, oppgave.reservasjon.reservasjonOpprettet)
                }
            }
        } else null

        val sql = """
            insert into oppgave (person_id, behandling_id, enhet_id, status, opprettet_tidspunkt,
                                 reservasjon_id)
            values (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return dbConnection.executeReturnKey(sql) {
            setParams {
                setLong(1, oppgave.person?.id)
                setLong(2, oppgave.forBehandling)
                setLong(3, oppgave.enhet.id)
                setEnumName(4, oppgave.status)
                setLocalDateTime(5, oppgave.opprettetTidspunkt)
                setLong(6, reservasjonId)
            }
        }
    }

    fun hentOppgaverForEnhet(enhet: Enhet): List<Oppgave> {
        val sql = """
            select o.id                  as o_id,
                   o.person_id           as o_person_id,
                   o.behandling_id       as o_behandling_id,
                   o.enhet_id            as o_enhet_id,
                   o.status              as o_status,
                   o.opprettet_tidspunkt as o_opprettet_tidspunkt,
                   o.reservasjon_id      as o_reservasjon_id,
                   e.id                  as e_id,
                   e.kode                as e_kode,
                   p.id                  as p_id,
                   p.ident               as p_ident,
                   r.reservert_av        as r_reservert_av,
                   r.opprettet_tid       as r_opprettet_tid,
                   s.id                  as s_id,
                   s.nav_ident           as s_nav_ident
            from oppgave o
                     join enhet e on o.enhet_id = e.id
                     left join person p on o.person_id = p.id
                     left join reservasjon r on r.id = o.reservasjon_id
                     left join saksbehandler s on s.id = r.reservert_av
            where e.kode = ?
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams {
                setString(1, enhet.kode)
            }
            setRowMapper {
                oppgaveMapper(it)
            }
        }
    }

    private fun oppgaveMapper(it: Row) = Oppgave(
        enhet = Enhet(it.getLongOrNull("e_id"), it.getString("e_kode")),
        person = it.getLongOrNull("p_id")?.let { personId ->
            Person(
                id = personId,
                ident = it.getString("p_ident")
            )
        },
        status = it.getEnum("o_status"),
        opprettetTidspunkt = it.getLocalDateTime("o_opprettet_tidspunkt"),
        forBehandling = it.getLongOrNull("o_behandling_id"),
        hendelser = listOf() // TODO
    )

    fun hentOppgaverForBehandling(behandlingId: BehandlingId): List<Oppgave> {
        val sql = """
            select o.id                  as o_id,
                   o.person_id           as o_person_id,
                   o.behandling_id       as o_behandling_id,
                   o.enhet_id            as o_enhet_id,
                   o.status              as o_status,
                   o.opprettet_tidspunkt as o_opprettet_tidspunkt,
                   o.reservasjon_id      as o_reservasjon_id,
                   e.id                  as e_id,
                   e.kode                as e_kode,
                   p.id                  as p_id,
                   p.ident               as p_ident,
                   r.reservert_av        as r_reservert_av,
                   r.opprettet_tid       as r_opprettet_tid,
                   s.id                  as s_id,
                   s.nav_ident           as s_nav_ident
            from oppgave o
                     join enhet e on o.enhet_id = e.id
                     join person p on o.person_id = p.id
                     left join reservasjon r on r.id = o.reservasjon_id
                     left join saksbehandler s on s.id = r.reservert_av
            where behandling_id = ?
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams {
                setLong(1, behandlingId)
            }
            setRowMapper {
                Oppgave(
                    enhet = Enhet(it.getLongOrNull("e_id"), it.getString("e_kode")),
                    person = it.getLongOrNull("p_id")?.let { personId ->
                        Person(
                            id = personId,
                            ident = it.getString("p_ident")
                        )
                    },
                    status = it.getEnum("o_status"),
                    opprettetTidspunkt = it.getLocalDateTime("o_opprettet_tidspunkt"),
                    forBehandling = it.getLong("o_behandling_id"),
                    reservasjon = it.getLongOrNull("o_reservasjon_id")?.let { reservasjon ->
                        Reservasjon(
                            reservertAv = Saksbehandler(
                                id = it.getLong("r_reservert_av"),
                                ident = it.getString("s_nav_ident")
                            ),
                            reservasjonOpprettet = it.getLocalDateTime("r_opprettet_tid")
                        )
                    },
                    hendelser = listOf() // TODO
                )
            }
        }
    }
}
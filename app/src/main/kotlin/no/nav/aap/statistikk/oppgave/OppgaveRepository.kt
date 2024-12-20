package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.person.Person

class OppgaveRepository(private val dbConnection: DBConnection) {
    fun lagreOppgave(oppgave: Oppgave): Long {
        require(oppgave.enhet.id != null)

        val behandlingsReferanseId = oppgave.behandlingReferanse?.let { behandling ->
            val sqlVersjon = """WITH ny_ref AS (
    INSERT INTO behandling_referanse (referanse)
        VALUES (?)
        ON CONFLICT DO NOTHING
        RETURNING id)
SELECT COALESCE(
               (SELECT id FROM ny_ref),
               (SELECT id FROM behandling_referanse WHERE behandling_referanse.referanse = ?)
       ) AS id;"""

            dbConnection.queryFirst(sqlVersjon) {
                setParams {
                    setUUID(1, oppgave.behandlingReferanse.referanse)
                    setUUID(2, oppgave.behandlingReferanse.referanse)
                }
                setRowMapper { row -> row.getLong("id") }
            }
        }


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
            insert into oppgave (person_id, behandling_referanse_id, enhet_id, status, opprettet_tidspunkt,
                                 reservasjon_id, identifikator)
            values (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return dbConnection.executeReturnKey(sql) {
            setParams {
                setLong(1, oppgave.person?.id)
                setLong(2, behandlingsReferanseId)
                setLong(3, oppgave.enhet.id)
                setEnumName(4, oppgave.status)
                setLocalDateTime(5, oppgave.opprettetTidspunkt)
                setLong(6, reservasjonId)
                setLong(7, oppgave.identifikator)
            }
        }
    }

    fun oppdaterOppgave(oppgave: Oppgave) {
        require(oppgave.id != null)
        // Sjekk reservasjon-status
        if (oppgave.reservasjon == null) {
            val setNullSql = """
                update oppgave set reservasjon_id = null where id = ?
            """.trimIndent()

            dbConnection.execute(setNullSql) {
                setParams { setLong(1, oppgave.id) }
            }

            val sql = """
                delete from reservasjon where id in (select reservasjon_id from oppgave where id = ?)
            """.trimIndent()

            dbConnection.execute(sql) {
                setParams {
                    setLong(1, oppgave.id)
                }
            }
        } else {
            // Først fjern evnt eksisterende reservasjoner
            dbConnection.execute("delete from reservasjon where id in (select reservasjon_id from oppgave where id = ?)") {
                setParams {
                    setLong(1, oppgave.id)
                }
            }

            // Sett inn på nytt
            val sql = """
                insert into reservasjon (reservert_av, opprettet_tid) values (?, ?)
            """.trimIndent()

            val reservasjonId = dbConnection.executeReturnKey(sql) {
                setParams {
                    setLong(1, oppgave.reservasjon.reservertAv.id)
                    setLocalDateTime(2, oppgave.reservasjon.reservasjonOpprettet)
                }
            }

            // Oppdater referanse
            dbConnection.execute("update oppgave set reservasjon_id = ? where id = ?") {
                setParams {
                    setLong(1, reservasjonId)
                    setLong(2, oppgave.id)
                }
            }
        }

        // Oppdater enhetstilknytning
        dbConnection.execute("update oppgave set enhet_id = ? where id = ?") {
            setParams {
                setLong(1, oppgave.enhet.id)
                setLong(2, oppgave.id)
            }
        }

        val sql = """
            update oppgave
            set status = ?
            where id = ?
        """.trimIndent()

        dbConnection.execute(sql) {
            setParams {
                setEnumName(1, oppgave.status)
                setLong(2, oppgave.id)
            }
        }
    }

    fun hentOppgaverForEnhet(enhet: Enhet): List<Oppgave> {
        val sql = """
            select o.id                      as o_id,
                   o.identifikator           as o_identifikator,
                   o.avklaringsbehov         as o_avklaringsbehov,
                   o.person_id               as o_person_id,
                   o.behandling_referanse_id as o_behandling_referanse_id,
                   o.enhet_id                as o_enhet_id,
                   o.status                  as o_status,
                   o.opprettet_tidspunkt     as o_opprettet_tidspunkt,
                   o.reservasjon_id          as o_reservasjon_id,
                   br.referanse              as o_behandling_referanse,
                   e.id                      as e_id,
                   e.kode                    as e_kode,
                   p.id                      as p_id,
                   p.ident                   as p_ident,
                   r.reservert_av            as r_reservert_av,
                   r.opprettet_tid           as r_opprettet_tid,
                   s.id                      as s_id,
                   s.nav_ident               as s_nav_ident
            from oppgave o
                     join enhet e on o.enhet_id = e.id
                     left join behandling_referanse br on o.behandling_referanse_id = br.id
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
        id = it.getLongOrNull("o_id"),
        enhet = Enhet(it.getLongOrNull("e_id"), it.getString("e_kode")),
        person = it.getLongOrNull("p_id")?.let { personId ->
            Person(
                id = personId,
                ident = it.getString("p_ident")
            )
        },
        status = it.getEnum("o_status"),
        opprettetTidspunkt = it.getLocalDateTime("o_opprettet_tidspunkt"),
        behandlingReferanse = it.getLongOrNull("o_behandling_referanse_id")?.let { id ->
            BehandlingReferanse(id = id, referanse = it.getUUID("o_behandling_referanse"))
        },
        reservasjon = it.getLongOrNull("o_reservasjon_id")?.let { reservasjon ->
            Reservasjon(
                reservertAv = Saksbehandler(
                    id = it.getLong("r_reservert_av"),
                    ident = it.getString("s_nav_ident")
                ),
                reservasjonOpprettet = it.getLocalDateTime("r_opprettet_tid")
            )
        },
        identifikator = it.getLong("o_identifikator"),
        avklaringsbehov = it.getString("o_avklaringsbehov"),
        hendelser = listOf() // TODO
    )

    fun hentOppgave(identifikator: Long): Oppgave? {
        val sql = """
            select o.id                      as o_id,
                   o.identifikator           as o_identifikator,
                   o.avklaringsbehov         as o_avklaringsbehov,
                   o.person_id               as o_person_id,
                   o.behandling_referanse_id as o_behandling_referanse_id,
                   o.enhet_id                as o_enhet_id,
                   o.status                  as o_status,
                   o.opprettet_tidspunkt     as o_opprettet_tidspunkt,
                   o.reservasjon_id          as o_reservasjon_id,
                   br.referanse              as o_behandling_referanse,
                   e.id                      as e_id,
                   e.kode                    as e_kode,
                   p.id                      as p_id,
                   p.ident                   as p_ident,
                   r.reservert_av            as r_reservert_av,
                   r.opprettet_tid           as r_opprettet_tid,
                   s.id                      as s_id,
                   s.nav_ident               as s_nav_ident
            from oppgave o
                     join enhet e on o.enhet_id = e.id
                     left join behandling_referanse br on o.behandling_referanse_id = br.id
                     left join person p on o.person_id = p.id
                     left join reservasjon r on r.id = o.reservasjon_id
                     left join saksbehandler s on s.id = r.reservert_av
            where o.identifikator = ?
        """.trimIndent()

        return dbConnection.queryFirstOrNull(sql) {
            setParams {
                setLong(1, identifikator)
            }
            setRowMapper {
                oppgaveMapper(it)
            }
        }
    }

    fun hentOppgaverForBehandling(behandlingId: BehandlingId): List<Oppgave> {
        val sql = """
            select o.id                      as o_id,
                   o.identifikator           as o_identifikator,
                   o.avklaringsbehov         as o_avklaringsbehov,
                   o.person_id               as o_person_id,
                   o.behandling_referanse_id as o_behandling_referanse_id,
                   o.enhet_id                as o_enhet_id,
                   o.status                  as o_status,
                   o.opprettet_tidspunkt     as o_opprettet_tidspunkt,
                   o.reservasjon_id          as o_reservasjon_id,
                   br.referanse              as o_behandling_referanse,
                   e.id                      as e_id,
                   e.kode                    as e_kode,
                   p.id                      as p_id,
                   p.ident                   as p_ident,
                   r.reservert_av            as r_reservert_av,
                   r.opprettet_tid           as r_opprettet_tid,
                   s.id                      as s_id,
                   s.nav_ident               as s_nav_ident
            from oppgave o
                     join enhet e on o.enhet_id = e.id
                     join person p on o.person_id = p.id
                     join behandling b on b.id = o.behandling_referanse_id 
                     left join behandling_referanse br on o.behandling_referanse_id = br.id
                     left join reservasjon r on r.id = o.reservasjon_id
                     left join saksbehandler s on s.id = r.reservert_av
            where b.id = ?
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams {
                setLong(1, behandlingId)
            }
            setRowMapper {
                oppgaveMapper(it)
            }
        }
    }
}
package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import java.util.UUID

typealias BehandlingId = Long

class BehandlingRepository(private val dbConnection: DBConnection) : IBehandlingRepository {
    override fun lagre(behandling: Behandling): BehandlingId {
        return dbConnection.executeReturnKey("""
INSERT INTO behandling (sak_id, referanse, type, opprettet_tid)
VALUES (?, ?, ?, ?)""") {
            setParams {
                setLong(1, behandling.sak.id!!)
                setUUID(2, behandling.referanse)
                setString(3, behandling.typeBehandling.toString())
                setLocalDateTime(4, behandling.opprettetTid)
            }
        }
    }

    override fun hent(referanse: UUID): Behandling? {
        return dbConnection.queryFirstOrNull(
            """SELECT b.id            as b_id,
       b.referanse     as b_referanse,
       b.type          as b_type,
       b.opprettet_tid as b_opprettet_tid,
       s.id            as s_id,
       s.saksnummer    as s_saksnummer,
       p.ident         as p_ident,
       p.id            as p_id
FROM behandling b
         JOIN sak s on b.sak_id = s.id
         JOIN person p on p.id = s.person_id
WHERE b.referanse = ?"""
        ) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                Behandling(
                    id = it.getLong("b_id"),
                    referanse = it.getUUID("b_referanse"),
                    sak = Sak(
                        id = it.getLong("s_id"),
                        saksnummer = it.getString("s_saksnummer"),
                        person = Person(
                            ident = it.getString("p_ident"),
                            id = it.getLong("p_id"),
                        )
                    ),
                    typeBehandling = it.getString("b_type").let { TypeBehandling.valueOf(it) },
                    opprettetTid = it.getLocalDateTime("b_opprettet_tid")
                )
            }
        }
    }
}
package no.nav.aap.statistikk.meldekort

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.type.Periode
import no.nav.aap.statistikk.behandling.BehandlingId

class MeldekortRepository(private val dbConnection: DBConnection) {
    fun lagre(behandlingId: BehandlingId, meldekort: List<Meldekort>) {
        meldekort.forEach {
            lagreEnkeltMeldekort(behandlingId, it).let { meldekortId ->
                lagreArbeidIPeriode(meldekortId, it.arbeidIPeriodeDTO)
            }
        }
    }

    private fun lagreEnkeltMeldekort(behandlingId: BehandlingId, meldekort: Meldekort): Long {
        return dbConnection.executeReturnKey(
            """INSERT INTO meldekort (behandling_id, journalpost_id) VALUES (?,?)""".trimIndent()
        ) {
            setParams {
                setLong(1, behandlingId.id)
                setString(2, meldekort.journalpostId)
            }
        }
    }

    private fun lagreArbeidIPeriode(meldekortId: Long, arbeid: List<no.nav.aap.statistikk.meldekort.ArbeidIPerioder>) {
        dbConnection.executeBatch(
            """INSERT INTO arbeid_i_periode (meldekort_id, periode, timerArbeidet) VALUES (?,?::daterange,?)""".trimIndent(),
            arbeid
        ) {
            setParams {
                setLong(1, meldekortId)
                setPeriode(2, Periode(it.periodeFom, it.periodeTom))
                setBigDecimal(3, it.timerArbeidet)
            }
        }
    }

    fun hentMeldekortperioder(
        behandlingId: BehandlingId
    ): List<Meldekort> {
        return dbConnection.queryList(
            """SELECT * 
               FROM meldekort
               WHERE behandling_id = ?""".trimIndent()
        ) {
            setParams {
                setLong(1, behandlingId.id)
            }
            setRowMapper {
                Meldekort(
                    journalpostId = it.getString("journalpost_id"),
                    arbeidIPeriodeDTO = hentArbeidIPerioder(it.getLong("id"))
                )
            }
        }
    }

    private fun hentArbeidIPerioder(
        meldekortId: Long
    ): List<ArbeidIPerioder> {
        return dbConnection.queryList(
            """SELECT periode, timerArbeidet 
               FROM arbeid_i_periode 
               WHERE meldekort_id = ?""".trimIndent()
        ) {
            setParams {
                setLong(1, meldekortId)
            }
            setRowMapper {
                ArbeidIPerioder(
                    periodeFom = it.getPeriode("periode").fom,
                    periodeTom = it.getPeriode("periode").tom,
                    timerArbeidet = it.getBigDecimal("timerArbeidet")
                )
            }
        }
    }
}
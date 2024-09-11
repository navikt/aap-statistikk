package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*

private val logger = LoggerFactory.getLogger(TilkjentYtelseRepository::class.java)

interface ITilkjentYtelseRepository {
    fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Long
    fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse?
}

class TilkjentYtelseRepository(
    private val dbConnection: DBConnection
) : ITilkjentYtelseRepository {
    override fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Long {

        val nøkkel =
            dbConnection.executeReturnKey("INSERT INTO TILKJENT_YTELSE (behandling_id) VALUES (?)") {
                setParams {
                    setInt(1, hentBehandlingId(tilkjentYtelse.behandlingsReferanse, dbConnection))
                }
            }

        val sql =
            "INSERT INTO TILKJENT_YTELSE_PERIODE (FRA_DATO, TIL_DATO, DAGSATS, GRADERING, TILKJENT_YTELSE_ID) VALUES (?, ?, ?, ?, ?)"
        tilkjentYtelse.perioder.forEach { periode ->
            dbConnection.execute(sql) {
                setParams {
                    setLocalDate(1, periode.fraDato)
                    setLocalDate(2, periode.tilDato)
                    setBigDecimal(3, BigDecimal.valueOf(periode.dagsats))
                    setBigDecimal(4, BigDecimal.valueOf(periode.gradering))
                    setLong(5, nøkkel)
                }
            }
        }

        logger.info("Lagret tilkjent ytelse med ID: $nøkkel.")

        return nøkkel
    }

    private fun hentBehandlingId(behandlingReferanse: UUID, connection: DBConnection): Int {
        val sql = "SELECT id FROM behandling WHERE referanse = ?"

        return connection.queryFirst<Int>(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
            }
            setRowMapper {
                it.getInt("id")
            }
        }
    }

    override fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse? {
        val perioderTriple = dbConnection.queryList<Triple<TilkjentYtelsePeriode, UUID, String>>(
            """SELECT *
FROM tilkjent_ytelse_periode
         left join tilkjent_ytelse
                   on tilkjent_ytelse.id = tilkjent_ytelse_periode.tilkjent_ytelse_id
         left join behandling on behandling.id = tilkjent_ytelse.behandling_id
         left join sak on sak.id = behandling.sak_id
WHERE tilkjent_ytelse.id = ?"""
        ) {
            setParams { setInt(1, tilkjentYtelseId) }
            setRowMapper { row ->
                val fraDato = row.getLocalDate("fra_dato")
                val tilDato = row.getLocalDate("til_dato")
                val dagsats = row.getBigDecimal("dagsats").toDouble()
                val gradering = row.getBigDecimal("gradering").toDouble()

                Triple(
                    TilkjentYtelsePeriode(
                        fraDato = fraDato,
                        tilDato = tilDato,
                        dagsats = dagsats,
                        gradering = gradering
                    ), row.getUUID("referanse"), row.getString("saksnummer")
                )
            }
        }

        val saksnummer = perioderTriple.first().third
        val behandlingsReferanse = perioderTriple.first().second

        return TilkjentYtelse(saksnummer, behandlingsReferanse, perioderTriple.map { it.first })
    }
}
package no.nav.aap.statistikk.tilkjentytelse.repository

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelsePeriode
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

private val logger = LoggerFactory.getLogger(TilkjentYtelseRepository::class.java)

class TilkjentYtelseRepository(
    private val dbConnection: DBConnection
) : ITilkjentYtelseRepository {
    companion object : RepositoryFactory<ITilkjentYtelseRepository> {
        override fun konstruer(connection: DBConnection): ITilkjentYtelseRepository {
            return TilkjentYtelseRepository(connection)
        }
    }

    override fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Long {

        val nøkkel =
            dbConnection.executeReturnKey("INSERT INTO TILKJENT_YTELSE (behandling_id, opprettet_tidspunkt) VALUES (?, ?)") {
                setParams {
                    setInt(1, hentBehandlingId(tilkjentYtelse.behandlingsReferanse, dbConnection))
                    setLocalDateTime(2, LocalDateTime.now())
                }
            }

        val sql =
            """INSERT INTO TILKJENT_YTELSE_PERIODE (FRA_DATO, TIL_DATO, DAGSATS, GRADERING, TILKJENT_YTELSE_ID,
                                     REDUSERT_DAGSATS, ANTALL_BARN, BARNETILLEGG_SATS, BARNETILLEGG, utbetalingsdato)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        tilkjentYtelse.perioder.forEach { periode ->
            dbConnection.execute(sql) {
                setParams {
                    setLocalDate(1, periode.fraDato)
                    setLocalDate(2, periode.tilDato)
                    setBigDecimal(3, BigDecimal.valueOf(periode.dagsats))
                    setBigDecimal(4, BigDecimal.valueOf(periode.gradering))
                    setLong(5, nøkkel)
                    setDouble(6, periode.redusertDagsats)
                    setInt(7, periode.antallBarn)
                    setDouble(8, periode.barnetilleggSats)
                    setDouble(9, periode.barnetillegg)
                    setLocalDate(10, periode.utbetalingsdato)
                }
            }
        }

        logger.info("Lagret tilkjent ytelse med ID: $nøkkel.")

        return nøkkel
    }

    private fun hentBehandlingId(behandlingReferanse: UUID, connection: DBConnection): Int {
        val sql = """SELECT b.id
FROM behandling b
         join behandling_referanse br on b.referanse_id = br.id
WHERE br.referanse = ?"""

        return connection.queryFirst(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
            }
            setRowMapper {
                it.getInt("id")
            }
        }
    }

    override fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse {
        val perioderTriple = dbConnection.queryList(
            """SELECT *
FROM tilkjent_ytelse_periode
         left join tilkjent_ytelse
                   on tilkjent_ytelse.id = tilkjent_ytelse_periode.tilkjent_ytelse_id
         left join behandling on behandling.id = tilkjent_ytelse.behandling_id
         left join behandling_referanse br on br.id = behandling.referanse_id
         left join sak on sak.id = behandling.sak_id
WHERE tilkjent_ytelse.id = ?"""
        ) {
            setParams { setInt(1, tilkjentYtelseId) }
            setRowMapper(mapTilkjentTriple())
        }

        val saksnummer = perioderTriple.first().third
        val behandlingsReferanse = perioderTriple.first().second

        return TilkjentYtelse(
            saksnummer.let(::Saksnummer),
            behandlingsReferanse,
            perioderTriple.map { it.first })
    }

    override fun hentForBehandling(behandlingId: UUID): TilkjentYtelse? {
        val perioderTriple = dbConnection.queryList(
            """SELECT *
FROM tilkjent_ytelse_periode
         left join tilkjent_ytelse
                   on tilkjent_ytelse.id = tilkjent_ytelse_periode.tilkjent_ytelse_id
         left join behandling on behandling.id = tilkjent_ytelse.behandling_id
         left join behandling_referanse br on br.id = behandling.referanse_id
         left join sak on sak.id = behandling.sak_id
WHERE br.referanse = ?"""
        ) {
            setParams { setUUID(1, behandlingId) }
            setRowMapper(mapTilkjentTriple())
        }

        if (perioderTriple.isEmpty()) {
            return null
        }

        val saksnummer = perioderTriple.first().third
        val behandlingsReferanse = perioderTriple.first().second

        return TilkjentYtelse(
            saksnummer.let(::Saksnummer),
            behandlingsReferanse,
            perioderTriple.map { it.first })
    }

    private fun mapTilkjentTriple(): (Row) -> Triple<TilkjentYtelsePeriode, UUID, String> = { row ->
        val fraDato = row.getLocalDate("fra_dato")
        val tilDato = row.getLocalDate("til_dato")
        val dagsats = row.getBigDecimal("dagsats").toDouble()
        val gradering = row.getBigDecimal("gradering").toDouble()
        val redusertDagsats = row.getDouble("redusert_dagsats")
        val antallBarn = row.getInt("antall_barn")
        val barnetilleggSats = row.getDouble("barnetillegg_sats")
        val barnetillegg = row.getDouble("barnetillegg")
        // Fallback for gammel data.
        val utbetalingsdato = row.getLocalDateOrNull("utbetalingsdato") ?: tilDato.plusDays(1)

        Triple(
            TilkjentYtelsePeriode(
                fraDato = fraDato,
                tilDato = tilDato,
                dagsats = dagsats,
                gradering = gradering,
                redusertDagsats = redusertDagsats,
                antallBarn = antallBarn,
                barnetilleggSats = barnetilleggSats,
                barnetillegg = barnetillegg,
                utbetalingsdato = utbetalingsdato
            ), row.getUUID("referanse"), row.getString("saksnummer")
        )
    }
}
package no.nav.aap.statistikk.bigquery

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.ConnectionJobbSpesifikasjon
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import java.time.LocalDate

class RekjorSakstatistikkJobbUtfører(
    private val bigQueryKvitteringRepository: BigQueryKvitteringRepository,
    private val leggTilJobbCallback: (BehandlingId) -> Unit,
) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val fraDato = input.parameter("fraDato").let(LocalDate::parse)
        val tilDato = input.parameter("tilDato").let(LocalDate::parse)

        bigQueryKvitteringRepository.hentOpplastedeMeldingerFraOgTil(fraDato, tilDato).forEach {
            leggTilJobbCallback(it)
        }
    }
}

class RekjorSakstatistikkJobb(private val motorJobbAppender: JobbAppender) : ConnectionJobbSpesifikasjon {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return RekjorSakstatistikkJobbUtfører(
            bigQueryKvitteringRepository = BigQueryKvitteringRepository(connection),
            leggTilJobbCallback = { behandlingId ->
                motorJobbAppender.leggTilLagreSakTilBigQueryJobb(connection, behandlingId)
            }
        )
    }

    override val type: String
        get() = "rekjorSakstatistikkJobb"
    override val navn: String
        get() = "statistikk.rekjorSakstatistikkJobb"
    override val beskrivelse: String
        get() = "Skal trigge rekjøring av behandlinger i en gitt dato-range."
}
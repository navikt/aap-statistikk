package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProviderJobbSpesifikasjon
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import java.util.*

class LagreAvsluttetBehandlingTilBigQueryJobbUtfører(private val ytelsesStatistikkTilBigQuery: YtelsesStatistikkTilBigQuery) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val behandlingReferanse = input.payload<UUID>()
        ytelsesStatistikkTilBigQuery.lagre(behandlingReferanse)
    }
}

class LagreAvsluttetBehandlingTilBigQueryJobb(
    private val bqYtelseRepository: IBQYtelsesstatistikkRepository,
) : ProviderJobbSpesifikasjon {

    override fun konstruer(repositoryProvider: RepositoryProvider): JobbUtfører {
        return LagreAvsluttetBehandlingTilBigQueryJobbUtfører(
            YtelsesStatistikkTilBigQuery.konstruer(
                bqYtelseRepository,
                repositoryProvider
            )
        )
    }

    override val retries: Int = 1

    override val type: String = "statistikk.lagreAvsluttetBehandlingTilBigQueryJobb"

    override val navn: String = "lagreAvsluttetBehandlingTilBigQuery"

    override val beskrivelse: String = "Lagrer avsluttet behandling til BigQuery (til Team Spenn)."
}
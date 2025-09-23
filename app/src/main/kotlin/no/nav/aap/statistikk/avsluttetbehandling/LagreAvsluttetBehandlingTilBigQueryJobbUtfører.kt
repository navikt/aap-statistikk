package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import java.util.*

class LagreAvsluttetBehandlingTilBigQueryJobbUtfører(private val ytelsesStatistikkTilBigQuery: YtelsesStatistikkTilBigQuery) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val behandlingReferanse = input.payload<UUID>()
        ytelsesStatistikkTilBigQuery.lagre(behandlingReferanse)
    }
}

class LagreAvsluttetBehandlingTilBigQueryJobb(
    private val ytelsesStatistikkTilBigQuery: (DBConnection) -> YtelsesStatistikkTilBigQuery,
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return LagreAvsluttetBehandlingTilBigQueryJobbUtfører(
            ytelsesStatistikkTilBigQuery(connection)
        )
    }

    override fun type(): String {
        return "statistikk.lagreAvsluttetBehandlingTilBigQueryJobb"
    }

    override fun navn(): String {
        return "lagreAvsluttetBehandlingTilBigQuery"
    }

    override fun beskrivelse(): String {
        return "Lagrer avsluttet behandling til BigQuery (til Team Spenn)."
    }

    override fun retries(): Int {
        return 1
    }
}
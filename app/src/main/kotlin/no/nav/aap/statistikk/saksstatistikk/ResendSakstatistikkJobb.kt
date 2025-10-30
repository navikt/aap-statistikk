package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.postgresRepositoryRegistry
import org.slf4j.LoggerFactory

class ResendSakstatistikkJobbUtfører(
    val sakStatikkService: SaksStatistikkService,
    val sakstatistikkRepository: SakstatistikkRepository
) : JobbUtfører {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val behandlingId = input.payload<Long>().let(::BehandlingId)
        log.info("Resender sakstatistikk for behandling med id $behandlingId.")

        val alleHendelser = sakStatikkService.alleHendelserPåBehandling(behandlingId)
            .map { it.copy(erResending = true) }
        sakstatistikkRepository.lagreFlere(alleHendelser)
        log.info("Lagret ${alleHendelser.size} hendelser i sakssakstatistikk-tabell.")
    }
}

class ResendSakstatistikkJobb(
    private val sakStatistikkService: (DBConnection) -> SaksStatistikkService,
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return ResendSakstatistikkJobbUtfører(
            sakStatikkService = sakStatistikkService(connection),
            sakstatistikkRepository = postgresRepositoryRegistry.provider(connection).provide()
        )
    }

    override fun type(): String {
        return "statistikk.resendSakstatistikk"
    }

    override fun navn(): String {
        return "Resend saksstatikk"
    }

    override fun beskrivelse(): String {
        return "Resend saksstatikk"
    }

    override val retries: Int
        get() = 1
}
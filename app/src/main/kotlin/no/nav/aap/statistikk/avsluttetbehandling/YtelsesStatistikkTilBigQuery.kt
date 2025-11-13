package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.behandling.DiagnoseRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

class YtelsesStatistikkTilBigQuery(
    private val bqRepository: IBQYtelsesstatistikkRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val diagnoseRepository: DiagnoseRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    companion object {
        fun konstruer(
            bqRepository: IBQYtelsesstatistikkRepository,
            repositoryProvider: RepositoryProvider
        ): YtelsesStatistikkTilBigQuery {
            return YtelsesStatistikkTilBigQuery(
                bqRepository = bqRepository,
                behandlingRepository = repositoryProvider.provide(),
                rettighetstypeperiodeRepository = repositoryProvider.provide(),
                diagnoseRepository = repositoryProvider.provide(),
            )
        }
    }

    fun lagre(behandlingReferanse: UUID) {
        val behandling =
            requireNotNull(behandlingRepository.hent(behandlingReferanse)) { "Prøver å lagre en ikke-eksisterende behandling med referanse $behandlingReferanse" }
        val rettighetstypeperioder = rettighetstypeperiodeRepository.hent(behandling.referanse)
        val diagnoser = diagnoseRepository.hentForBehandling(behandling.referanse)

        bqRepository.start()
        bqRepository.lagre(
            BQYtelseBehandling(
                saksnummer = behandling.sak.saksnummer,
                referanse = behandling.referanse,
                utbetalingId = behandling.utbetalingId(),
                brukerFnr = behandling.sak.person.ident,
                resultat = behandling.resultat(),
                behandlingsType = behandling.typeBehandling,
                datoOpprettet = behandling.opprettetTid,
                datoAvsluttet = behandling.avsluttetTid(),
                kodeverk = diagnoser?.kodeverk,
                diagnosekode = diagnoser?.diagnosekode,
                bidiagnoser = diagnoser?.bidiagnoser,
                rettighetsPerioder = rettighetstypeperioder,
                radEndret = LocalDateTime.now(clock),
                vurderingsbehov = behandling.årsaker.map { it.name }
            )
        )
        bqRepository.commit()
    }

}
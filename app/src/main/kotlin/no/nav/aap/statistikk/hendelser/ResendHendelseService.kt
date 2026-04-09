package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.Saksnummer
import org.slf4j.LoggerFactory

class ResendHendelseService(
    private val sakService: SakService,
    private val personService: PersonService,
    private val behandlingRepository: IBehandlingRepository,
    private val behandlingService: BehandlingService,
    private val opprettRekjørSakstatistikkCallback: (BehandlingId) -> Unit,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun prosesserNyHistorikkHendelse(hendelse: StoppetBehandling) {
        val person = personService.hentEllerLagrePerson(hendelse.ident)
        val saksnummer = hendelse.saksnummer.let(::Saksnummer)

        val sak =
            sakService.hentEllerSettInnSak(person, saksnummer, hendelse.sakStatus.tilDomene())
        val behandling = behandlingService.konstruerBehandling(hendelse, sak)

        val behandlingMedHistorikk =
            ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        check(behandling.behandlingStatus() == behandlingMedHistorikk.behandlingStatus())
        { "Behandlingstatus er ikke lik behandling med historikk-status. Behandlingstatus: ${behandling.behandlingStatus()}, behandling med historikk-status: ${behandlingMedHistorikk.behandlingStatus()}. Saksnummer: ${hendelse.saksnummer}. Behandling: ${hendelse.behandlingReferanse}" }

        val behandlingId =
            checkNotNull(behandlingService.hentEllerLagreBehandling(hendelse, sak).id)

        val behandlingMedId = behandlingMedHistorikk.copy(id = behandlingId)

        behandlingRepository.invaliderOgLagreNyHistorikk(behandlingMedId)

        logger.info("Starter jobb for rekjøring av saksstatistikk for behandling med id ${behandlingMedId.id}.")

        opprettRekjørSakstatistikkCallback(behandlingId)
    }
}
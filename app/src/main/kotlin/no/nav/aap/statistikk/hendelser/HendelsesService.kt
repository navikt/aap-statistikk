package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.jobber.appender.HendelsePublisher
import no.nav.aap.statistikk.jobber.appender.StatistikkHendelse
import no.nav.aap.statistikk.meldekort.IMeldekortRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.saksstatistikk.Konstanter
import org.slf4j.LoggerFactory

class HendelsesService(
    private val sakService: SakService,
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val personService: PersonService,
    private val behandlingService: BehandlingService,
    private val meldekortRepository: IMeldekortRepository,
    private val hendelsePublisher: HendelsePublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = personService.hentEllerLagrePerson(hendelse.ident)
        val saksnummer = hendelse.saksnummer.let(::Saksnummer)

        val sak =
            sakService.hentEllerSettInnSak(person, saksnummer, hendelse.sakStatus.tilDomene())

        val behandlingId = behandlingService.hentEllerLagreBehandling(hendelse, sak).id()

        log.info("Mottok ${hendelse.nyeMeldekort.size} nye meldekort for behandling ${hendelse.behandlingReferanse}.")
        if (hendelse.nyeMeldekort.isNotEmpty()) {
            meldekortRepository.lagre(behandlingId, hendelse.nyeMeldekort.tilDomene())
        }

        if (hendelse.behandlingStatus == Status.AVSLUTTET) {
            val avsluttetBehandling =
                requireNotNull(hendelse.avsluttetBehandling) { "Om behandlingen er avsluttet, så må avsluttetBehandling være ikke-null." }

            // Oppfølgingsbehandling er ikke relatert til en ytelse, så dette kan ignoreres.
            if (hendelse.behandlingType.tilDomene() in listOf(
                    no.nav.aap.statistikk.behandling.TypeBehandling.Revurdering,
                    no.nav.aap.statistikk.behandling.TypeBehandling.Førstegangsbehandling
                )
            ) {
                avsluttetBehandlingService.lagre(
                    avsluttetBehandling.tilDomene(
                        saksnummer,
                        hendelse.behandlingReferanse,
                    )
                )
            }
        }

        if (hendelse.behandlingType.tilDomene() in Konstanter.interessanteBehandlingstyper) {
            hendelsePublisher.publiser(StatistikkHendelse.SakstatistikkSkalLagres(behandlingId))
        }

        PrometheusProvider.prometheus.hendelseLagret().increment()
        log.info("Hendelse behandlet. Saksnr: ${hendelse.saksnummer}")
    }
}

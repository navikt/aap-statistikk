package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.MeldekortDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Vurderingsbehov.*
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.meldekort.ArbeidIPerioder
import no.nav.aap.statistikk.meldekort.IMeldekortRepository
import no.nav.aap.statistikk.meldekort.Meldekort
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.verdityper.dokument.Kanal
import org.slf4j.LoggerFactory
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus

class HendelsesService(
    private val sakService: SakService,
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val personService: PersonService,
    private val behandlingService: BehandlingService,
    private val meldekortRepository: IMeldekortRepository,
    private val opprettBigQueryLagringSakStatistikkCallback: (BehandlingId) -> Unit,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun konstruer(
            avsluttetBehandlingService: AvsluttetBehandlingService,
            jobbAppender: JobbAppender,
            repositoryProvider: RepositoryProvider
        ): HendelsesService {
            return HendelsesService(
                sakService = SakService(repositoryProvider),
                personService = PersonService(repositoryProvider),
                avsluttetBehandlingService = avsluttetBehandlingService,
                behandlingService = BehandlingService(repositoryProvider.provide()),
                meldekortRepository = repositoryProvider.provide(),
                opprettBigQueryLagringSakStatistikkCallback = {
                    LoggerFactory.getLogger(HendelsesService::class.java)
                        .info("Legger til lagretilsaksstatistikkjobb. BehandlingId: $it")
                    jobbAppender.leggTilLagreSakTilBigQueryJobb(
                        repositoryProvider,
                        it,
                        // Veldig hacky! Dette er for at jobben som kjører etter melidng fra
                        // oppgave-appen skal få tid til å oppdatere enhet-tabellen før denne kjører.
                        delayInSeconds = System.getenv("HACKY_DELAY")?.toLong() ?: 0L
                    )
                }
            )
        }
    }

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
            // TODO: legg denne i en jobb
            val avsluttetBehandling =
                requireNotNull(hendelse.avsluttetBehandling) { "Om behandlingen er avsluttet, så må avsluttetBehandling være ikke-null." }

            // Oppfølgingsbehandling er ikke relatert til en ytelse, så dette kan ignoreres.
            if (hendelse.behandlingType !in listOf(no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling)) {
                avsluttetBehandlingService.lagre(
                    avsluttetBehandling.tilDomene(
                        saksnummer,
                        hendelse.behandlingReferanse,
                    )
                )
            }
        }

        if (hendelse.behandlingType !in listOf(no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling)) {
            opprettBigQueryLagringSakStatistikkCallback(behandlingId)
        }

        PrometheusProvider.prometheus.hendelseLagret().increment()
        log.info("Hendelse behandlet. Saksnr: ${hendelse.saksnummer}")
    }

    private fun List<MeldekortDTO>.tilDomene(): List<Meldekort> {
        return this.map { meldekort ->
            Meldekort(
                journalpostId = meldekort.journalpostId,
                arbeidIPeriodeDTO = meldekort.arbeidIPeriode.map {
                    ArbeidIPerioder(
                        periodeFom = it.periodeFom,
                        periodeTom = it.periodeTom,
                        timerArbeidet = it.timerArbeidet
                    )
                }
            )
        }
    }
}

internal fun SakStatus.tilDomene(): no.nav.aap.statistikk.sak.SakStatus {
    return when (this) {
        SakStatus.OPPRETTET -> no.nav.aap.statistikk.sak.SakStatus.OPPRETTET
        SakStatus.UTREDES -> no.nav.aap.statistikk.sak.SakStatus.UTREDES
        SakStatus.LØPENDE -> no.nav.aap.statistikk.sak.SakStatus.LØPENDE
        SakStatus.AVSLUTTET -> no.nav.aap.statistikk.sak.SakStatus.AVSLUTTET
    }
}

fun no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.tilDomene(): TypeBehandling =
    when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Førstegangsbehandling -> TypeBehandling.Førstegangsbehandling
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Revurdering -> TypeBehandling.Revurdering
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Tilbakekreving -> TypeBehandling.Tilbakekreving
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Klage -> TypeBehandling.Klage
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.SvarFraAndreinstans -> TypeBehandling.SvarFraAndreinstans
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling -> TypeBehandling.Oppfølgingsbehandling
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt -> TypeBehandling.Aktivitetsplikt
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt11_9 -> TypeBehandling.Aktivitetsplikt11_9
    }

fun Status.tilDomene(): BehandlingStatus =
    when (this) {
        Status.OPPRETTET -> BehandlingStatus.OPPRETTET
        Status.UTREDES -> BehandlingStatus.UTREDES
        Status.IVERKSETTES -> BehandlingStatus.IVERKSETTES
        Status.AVSLUTTET -> BehandlingStatus.AVSLUTTET
    }

fun Kanal.tilDomene(): SøknadsFormat {
    return when (this) {
        Kanal.DIGITAL -> SøknadsFormat.DIGITAL
        Kanal.PAPIR -> SøknadsFormat.PAPIR
    }
}

fun Vurderingsbehov.tilDomene(): no.nav.aap.statistikk.behandling.Vurderingsbehov {
    return when (this) {
        Vurderingsbehov.SØKNAD -> SØKNAD
        Vurderingsbehov.AKTIVITETSMELDING -> AKTIVITETSMELDING
        Vurderingsbehov.MELDEKORT -> MELDEKORT
        Vurderingsbehov.FRITAK_MELDEPLIKT -> MELDEKORT
        Vurderingsbehov.LEGEERKLÆRING -> LEGEERKLÆRING
        Vurderingsbehov.AVVIST_LEGEERKLÆRING -> AVVIST_LEGEERKLÆRING
        Vurderingsbehov.DIALOGMELDING -> DIALOGMELDING
        Vurderingsbehov.G_REGULERING -> G_REGULERING
        Vurderingsbehov.REVURDER_MEDLEMSKAP -> REVURDER_MEDLEMSSKAP
        Vurderingsbehov.REVURDER_YRKESSKADE -> REVURDER_YRKESSKADE
        Vurderingsbehov.REVURDER_BEREGNING -> REVURDER_BEREGNING
        Vurderingsbehov.REVURDER_LOVVALG -> REVURDER_LOVVALG
        Vurderingsbehov.KLAGE -> KLAGE
        Vurderingsbehov.REVURDER_SAMORDNING -> REVURDER_SAMORDNING
        Vurderingsbehov.LOVVALG_OG_MEDLEMSKAP -> LOVVALG_OG_MEDLEMSKAP
        Vurderingsbehov.FORUTGAENDE_MEDLEMSKAP -> FORUTGAENDE_MEDLEMSKAP
        Vurderingsbehov.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND -> SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND
        Vurderingsbehov.BARNETILLEGG -> BARNETILLEGG
        Vurderingsbehov.INSTITUSJONSOPPHOLD -> INSTITUSJONSOPPHOLD
        Vurderingsbehov.SAMORDNING_OG_AVREGNING -> SAMORDNING_OG_AVREGNING
        Vurderingsbehov.REFUSJONSKRAV -> REFUSJONSKRAV
        Vurderingsbehov.UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT -> UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT
        Vurderingsbehov.SØKNAD_TRUKKET -> SØKNAD_TRUKKET
        Vurderingsbehov.VURDER_RETTIGHETSPERIODE -> VURDER_RETTIGHETSPERIODE
        Vurderingsbehov.REVURDER_MANUELL_INNTEKT -> REVURDER_MANUELL_INNTEKT
        Vurderingsbehov.KLAGE_TRUKKET -> KLAGE_TRUKKET
        Vurderingsbehov.MOTTATT_KABAL_HENDELSE -> MOTTATT_KABAL_HENDELSE
        Vurderingsbehov.OPPFØLGINGSOPPGAVE -> OPPFØLGINGSOPPGAVE
        Vurderingsbehov.HELHETLIG_VURDERING -> HELHETLIG_VURDERING
        Vurderingsbehov.REVURDER_MELDEPLIKT_RIMELIG_GRUNN -> REVURDER_MELDEPLIKT_RIMELIG_GRUNN
        Vurderingsbehov.AKTIVITETSPLIKT_11_7 -> AKTIVITETSPLIKT_11_7
        Vurderingsbehov.AKTIVITETSPLIKT_11_9 -> AKTIVITETSPLIKT_11_9
        Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT -> EFFEKTUER_AKTIVITETSPLIKT
        Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT_11_9 -> EFFEKTUER_AKTIVITETSPLIKT_11_9
        Vurderingsbehov.OVERGANG_UFORE -> OVERGANG_UFORE
        Vurderingsbehov.OVERGANG_ARBEID -> OVERGANG_ARBEID
        Vurderingsbehov.REVURDERING_AVBRUTT -> REVURDERING_AVBRUTT
        Vurderingsbehov.DØDSFALL_BRUKER -> DØDSFALL_BRUKER
        Vurderingsbehov.DØDSFALL_BARN -> DØDSFALL_BARN
        Vurderingsbehov.OPPHOLDSKRAV -> OPPHOLDSKRAV
        Vurderingsbehov.REVURDER_STUDENT -> REVURDER_STUDENT
        Vurderingsbehov.REVURDER_SAMORDNING_ANDRE_FOLKETRYGDYTELSER -> REVURDER_SAMORDNING_ANDRE_FOLKETRYGDYTELSER
        Vurderingsbehov.REVURDER_SAMORDNING_UFØRE -> REVURDER_SAMORDNING_UFØRE
        Vurderingsbehov.REVURDER_SAMORDNING_ANDRE_STATLIGE_YTELSER -> REVURDER_SAMORDNING_ANDRE_STATLIGE_YTELSER
        Vurderingsbehov.REVURDER_SAMORDNING_ARBEIDSGIVER -> REVURDER_SAMORDNING_ARBEIDSGIVER
        Vurderingsbehov.REVURDER_SAMORDNING_TJENESTEPENSJON -> REVURDER_SAMORDNING_TJENESTEPENSJON
    }
}

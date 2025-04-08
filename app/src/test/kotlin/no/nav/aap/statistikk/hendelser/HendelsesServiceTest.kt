package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.checkUnnecessaryStub
import io.mockk.mockk
import io.mockk.verify
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Førstegangsbehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Revurdering
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.ÅrsakTilBehandling
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.YtelsesStatistikkTilBigQuery
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.nyBehandlingOpprettet
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

class HendelsesServiceTest {
    @Test
    fun `sette inn med relatert behanding`() {
        val bqRepositoryYtelse = FakeBQYtelseRepository()

        val currentInstant = Instant.now()
        val clock = Clock.fixed(currentInstant, ZoneId.of("Europe/Oslo"))
        val behandlingRepository = FakeBehandlingRepository()
        val simpleMeterRegistry = SimpleMeterRegistry()
        val hendelseLagretCounter = simpleMeterRegistry.hendelseLagret()
        val sakRepository = FakeSakRepository()
        val skjermingService = SkjermingService(FakePdlClient(emptyMap()))

        val opprettBigQueryLagringCallback = mockk<(BehandlingId) -> Unit>(relaxed = true)

        val rettighetstypeperiodeRepository = FakeRettighetsTypeRepository()
        val diagnoseRepository = FakeDiagnoseRepository()
        val hendelsesService = konstruerHendelsesService(
            sakRepository,
            diagnoseRepository,
            behandlingRepository,
            skjermingService,
            simpleMeterRegistry,
            rettighetstypeperiodeRepository,
            bqRepositoryYtelse,
            opprettBigQueryLagringCallback,
            clock
        )

        val sak = Sak(
            saksnummer = "ABCDE",
            person = Person("123"),
            sakStatus = SakStatus.LØPENDE,
            sistOppdatert = LocalDateTime.now()
        )
        sakRepository.settInnSak(sak)

        val relatertUUID = UUID.randomUUID()
        behandlingRepository.opprettBehandling(
            Behandling(
                referanse = relatertUUID,
                sak = sak,
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                status = BehandlingStatus.AVSLUTTET,
                opprettetTid = LocalDateTime.now().minusWeeks(4),
                mottattTid = LocalDateTime.now().minusWeeks(4).truncatedTo(ChronoUnit.SECONDS),
                versjon = Versjon(
                    verdi = "1111"
                ),
                søknadsformat = SøknadsFormat.PAPIR,
            )
        )

        val behandlingReferanse = UUID.randomUUID()
        hendelsesService.prosesserNyHendelse(
            StoppetBehandling(
                saksnummer = "1234",
                behandlingReferanse = behandlingReferanse,
                behandlingOpprettetTidspunkt = LocalDateTime.now(clock),
                behandlingStatus = Status.OPPRETTET,
                behandlingType = Revurdering,
                ident = "234",
                versjon = "dsad",
                avklaringsbehov = listOf(
                    AvklaringsbehovHendelseDto(
                        avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf()
                    )
                ),
                mottattTid = LocalDateTime.now().minusDays(1),
                sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.OPPRETTET,
                hendelsesTidspunkt = LocalDateTime.now(),
                relatertBehandling = relatertUUID,
                årsakTilBehandling = listOf(ÅrsakTilBehandling.SØKNAD)
            )
        )

        val uthentet = behandlingRepository.hent(behandlingReferanse)
        assertThat(uthentet).isNotNull()

        verify { opprettBigQueryLagringCallback(uthentet!!.id!!) }

        assertThat(hendelseLagretCounter.count()).isEqualTo(1.0)
        assertThat(
            simpleMeterRegistry.nyBehandlingOpprettet(TypeBehandling.Førstegangsbehandling).count()
        ).isEqualTo(0.0) // Fordi behandlingen allerede eksisterer

        checkUnnecessaryStub(opprettBigQueryLagringCallback)
    }

    private fun konstruerHendelsesService(
        sakRepository: FakeSakRepository,
        diagnoseRepository: FakeDiagnoseRepository,
        behandlingRepository: FakeBehandlingRepository,
        skjermingService: SkjermingService,
        simpleMeterRegistry: SimpleMeterRegistry,
        rettighetstypeperiodeRepository: FakeRettighetsTypeRepository,
        bqRepositoryYtelse: FakeBQYtelseRepository,
        opprettBigQueryLagringCallback: (BehandlingId) -> Unit,
        clock: Clock
    ): HendelsesService {
        val vilkårsresultatRepository = FakeVilkårsResultatRepository()
        return HendelsesService(
            sakRepository = sakRepository,
            avsluttetBehandlingService = AvsluttetBehandlingService(
                tilkjentYtelseRepository = FakeTilkjentYtelseRepository(),
                beregningsgrunnlagRepository = FakeBeregningsgrunnlagRepository(),
                vilkårsResultatRepository = vilkårsresultatRepository,
                diagnoseRepository = diagnoseRepository,
                behandlingRepository = behandlingRepository,
                skjermingService = skjermingService,
                meterRegistry = simpleMeterRegistry,
                rettighetstypeperiodeRepository = rettighetstypeperiodeRepository,
                ytelsesStatistikkTilBigQuery = YtelsesStatistikkTilBigQuery(
                    bqRepositoryYtelse,
                    rettighetstypeperiodeRepository,
                    diagnoseRepository,
                    vilkårsresultatRepository = vilkårsresultatRepository
                )
            ),
            personService = PersonService(FakePersonRepository()),
            behandlingRepository = behandlingRepository,
            meterRegistry = simpleMeterRegistry,
            opprettBigQueryLagringCallback = opprettBigQueryLagringCallback,
            clock = clock
        )
    }

    @Test
    fun `teller opprettet behandling`() {
        val bqRepositoryYtelse = FakeBQYtelseRepository()

        val currentInstant = Instant.now()
        val clock = Clock.fixed(currentInstant, ZoneId.of("Europe/Oslo"))
        val behandlingRepository = FakeBehandlingRepository()
        val simpleMeterRegistry = SimpleMeterRegistry()
        val hendelseLagretCounter = simpleMeterRegistry.hendelseLagret()
        val sakRepository = FakeSakRepository()
        val skjermingService = SkjermingService(FakePdlClient(emptyMap()))

        val rettighetstypeperiodeRepository = FakeRettighetsTypeRepository()
        val hendelsesService = konstruerHendelsesService(
            sakRepository,
            FakeDiagnoseRepository(),
            behandlingRepository,
            skjermingService,
            simpleMeterRegistry,
            rettighetstypeperiodeRepository,
            bqRepositoryYtelse,
            { MockJobbAppender() },
            clock
        )

        hendelsesService.prosesserNyHendelse(
            StoppetBehandling(
                saksnummer = "1234",
                behandlingReferanse = UUID.randomUUID(),
                behandlingOpprettetTidspunkt = LocalDateTime.now(clock),
                behandlingStatus = Status.OPPRETTET,
                behandlingType = Førstegangsbehandling,
                ident = "234",
                versjon = "dsad",
                avklaringsbehov = listOf(
                    AvklaringsbehovHendelseDto(
                        avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf()
                    )
                ),
                mottattTid = LocalDateTime.now().minusDays(1),
                sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.OPPRETTET,
                hendelsesTidspunkt = LocalDateTime.now(),
                årsakTilBehandling = listOf(ÅrsakTilBehandling.SØKNAD)
            )
        )

        assertThat(hendelseLagretCounter.count()).isEqualTo(1.0)
        assertThat(
            simpleMeterRegistry.nyBehandlingOpprettet(TypeBehandling.Førstegangsbehandling).count()
        ).isEqualTo(1.0) // Fordi behandlingen allerede eksisterer
    }
}
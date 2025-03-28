package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Førstegangsbehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Revurdering
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.ÅrsakTilBehandling
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
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
        val bqRepositorySak = FakeBQSakRepository()

        val currentInstant = Instant.now()
        val clock = Clock.fixed(currentInstant, ZoneId.of("Europe/Oslo"))
        val behandlingRepository = FakeBehandlingRepository()
        val simpleMeterRegistry = SimpleMeterRegistry()
        val hendelseLagretCounter = simpleMeterRegistry.hendelseLagret()
        val sakRepository = FakeSakRepository()
        val skjermingService = SkjermingService(FakePdlClient(emptyMap()))

        val hendelsesService = HendelsesService(
            sakRepository = sakRepository,
            behandlingRepository = behandlingRepository,
            avsluttetBehandlingService = AvsluttetBehandlingService(
                tilkjentYtelseRepository = FakeTilkjentYtelseRepository(),
                beregningsgrunnlagRepository = FakeBeregningsgrunnlagRepository(),
                vilkårsResultatRepository = FakeVilkårsResultatRepository(),
                diagnoseRepository = FakeDiagnoseRepository(),
                bqRepository = bqRepositoryYtelse,
                behandlingRepository = behandlingRepository,
                skjermingService = skjermingService,
                meterRegistry = simpleMeterRegistry,
                rettighetstypeperiodeRepository = FakeRettighetsTypeRepository()
            ),
            clock = clock,
            meterRegistry = simpleMeterRegistry,
            personService = PersonService(FakePersonRepository()),
            sakStatistikkService = SaksStatistikkService(
                behandlingRepository = behandlingRepository,
                bigQueryKvitteringRepository = FakeBigQueryKvitteringRepository(),
                bigQueryRepository = bqRepositorySak,
                skjermingService = skjermingService,
                clock = clock
            )
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


        hendelsesService.prosesserNyHendelse(
            StoppetBehandling(
                saksnummer = "1234",
                behandlingReferanse = UUID.randomUUID(),
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

        assertThat(behandlingRepository.hent(relatertUUID)).isNotNull()
        assertThat(bqRepositorySak.saker).hasSize(1)
        assertThat(bqRepositorySak.saker.first().saksnummer).isEqualTo("1234")
        assertThat(bqRepositorySak.saker.first().relatertFagsystem).isEqualTo("Kelvin")
        assertThat(bqRepositorySak.saker.first().relatertBehandlingUUID).isEqualTo(relatertUUID.toString())
        assertThat(hendelseLagretCounter.count()).isEqualTo(1.0)
        assertThat(
            simpleMeterRegistry.nyBehandlingOpprettet(TypeBehandling.Førstegangsbehandling).count()
        ).isEqualTo(0.0) // Fordi behandlingen allerede eksisterer
    }

    @Test
    fun `teller opprettet behandling`() {
        val bqRepositoryYtelse = FakeBQYtelseRepository()
        val bqRepositorySak = FakeBQSakRepository()

        val currentInstant = Instant.now()
        val clock = Clock.fixed(currentInstant, ZoneId.of("Europe/Oslo"))
        val behandlingRepository = FakeBehandlingRepository()
        val simpleMeterRegistry = SimpleMeterRegistry()
        val hendelseLagretCounter = simpleMeterRegistry.hendelseLagret()
        val sakRepository = FakeSakRepository()
        val skjermingService = SkjermingService(FakePdlClient(emptyMap()))

        val hendelsesService = HendelsesService(
            sakRepository = sakRepository,
            personService = PersonService(FakePersonRepository()),
            behandlingRepository = behandlingRepository,
            avsluttetBehandlingService = AvsluttetBehandlingService(
                tilkjentYtelseRepository = FakeTilkjentYtelseRepository(),
                beregningsgrunnlagRepository = FakeBeregningsgrunnlagRepository(),
                vilkårsResultatRepository = FakeVilkårsResultatRepository(),
                diagnoseRepository = FakeDiagnoseRepository(),
                bqRepository = bqRepositoryYtelse,
                behandlingRepository = behandlingRepository,
                skjermingService = skjermingService,
                meterRegistry = simpleMeterRegistry,
                rettighetstypeperiodeRepository = FakeRettighetsTypeRepository(),
            ),
            clock = clock,
            meterRegistry = simpleMeterRegistry,
            sakStatistikkService = SaksStatistikkService(
                behandlingRepository = behandlingRepository,
                bigQueryKvitteringRepository = FakeBigQueryKvitteringRepository(),
                bigQueryRepository = bqRepositorySak,
                skjermingService = skjermingService,
                clock = clock
            )
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


    @Test
    fun `hendelses-service lagrer i bigquery med korrekt tidspunkt`() {
        val bqYtelserRepository = FakeBQYtelseRepository()
        val bqSakRepository = FakeBQSakRepository()

        val currentInstant = Instant.now()
        val clock = Clock.fixed(currentInstant, ZoneId.of("Europe/Oslo"))
        val behandlingRepository = FakeBehandlingRepository()
        val simpleMeterRegistry = SimpleMeterRegistry()
        val hendelseLagretCounter = simpleMeterRegistry.hendelseLagret()
        val skjermingService = SkjermingService(FakePdlClient(emptyMap()))

        val hendelsesService = HendelsesService(
            sakRepository = FakeSakRepository(),
            personService = PersonService(FakePersonRepository()),
            behandlingRepository = behandlingRepository,
            avsluttetBehandlingService = AvsluttetBehandlingService(
                tilkjentYtelseRepository = FakeTilkjentYtelseRepository(),
                beregningsgrunnlagRepository = FakeBeregningsgrunnlagRepository(),
                vilkårsResultatRepository = FakeVilkårsResultatRepository(),
                diagnoseRepository = FakeDiagnoseRepository(),
                bqRepository = bqYtelserRepository,
                behandlingRepository = behandlingRepository,
                skjermingService = skjermingService,
                meterRegistry = simpleMeterRegistry,
                rettighetstypeperiodeRepository = FakeRettighetsTypeRepository()
            ),
            clock = clock,
            meterRegistry = simpleMeterRegistry,
            sakStatistikkService = SaksStatistikkService(
                behandlingRepository = behandlingRepository,
                bigQueryKvitteringRepository = FakeBigQueryKvitteringRepository(),
                bigQueryRepository = bqSakRepository,
                skjermingService = skjermingService,
                clock = clock
            )
        )


        hendelsesService.prosesserNyHendelse(
            StoppetBehandling(
                saksnummer = "1234",
                behandlingReferanse = UUID.randomUUID(),
                behandlingOpprettetTidspunkt = LocalDateTime.now(clock),
                behandlingStatus = Status.OPPRETTET,
                behandlingType = Revurdering,
                ident = "234",
                versjon = "dsad",
                avklaringsbehov = listOf(avklaringsbehovHendelse()),
                mottattTid = LocalDateTime.now().minusDays(1),
                sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.OPPRETTET,
                hendelsesTidspunkt = LocalDateTime.now(),
                årsakTilBehandling = listOf(
                    ÅrsakTilBehandling.SØKNAD,
                    ÅrsakTilBehandling.G_REGULERING
                )
            )
        )

        assertThat(bqSakRepository.saker).hasSize(1)
        assertThat(bqSakRepository.saker.first().saksnummer).isEqualTo("1234")
        assertThat(bqSakRepository.saker.first().tekniskTid).isEqualTo(
            LocalDateTime.now(clock)
        )
        assertThat(bqSakRepository.saker.first().behandlingÅrsak).isEqualTo("SØKNAD,G_REGULERING")
        assertThat(hendelseLagretCounter.count()).isEqualTo(1.0)
    }
}
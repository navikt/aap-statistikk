package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.oppgave.EnhetReservasjonOgTidspunkt
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.FakeOppgaveHendelseRepository
import no.nav.aap.statistikk.testutils.FakePdlGateway
import no.nav.aap.statistikk.testutils.FakeRettighetsTypeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus

class BQBehandlingMapperTest {

    private val fixedClock = Clock.fixed(
        LocalDateTime.of(2024, 1, 15, 12, 0).atZone(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault()
    )

    private val skjermingService = SkjermingService(FakePdlGateway())

    private class FakeOppgaveRepository : no.nav.aap.statistikk.oppgave.OppgaveRepository {
        private val oppgaver =
            mutableMapOf<BehandlingId, MutableList<no.nav.aap.statistikk.oppgave.Oppgave>>()

        fun addOppgave(behandlingId: BehandlingId, oppgave: no.nav.aap.statistikk.oppgave.Oppgave) {
            oppgaver.getOrPut(behandlingId) { mutableListOf() }.add(oppgave)
        }

        override fun lagreOppgave(oppgave: no.nav.aap.statistikk.oppgave.Oppgave) = 0L
        override fun oppdaterOppgave(oppgave: no.nav.aap.statistikk.oppgave.Oppgave) {}
        override fun hentOppgaverForEnhet(enhet: no.nav.aap.statistikk.enhet.Enhet) =
            emptyList<no.nav.aap.statistikk.oppgave.Oppgave>()

        override fun hentOppgave(identifikator: Long) = null
        override fun hentOppgaverForBehandling(behandlingId: BehandlingId) =
            oppgaver[behandlingId] ?: emptyList()
    }

    private fun lagBehandling(
        referanse: UUID = UUID.randomUUID(),
        gjeldendeAvklaringsbehov: String? = null,
        sisteLøsteAvklaringsbehov: String? = null,
        sisteSaksbehandlerSomLøstebehov: String? = null,
        hendelser: List<BehandlingHendelse>
    ): Behandling {
        val person = Person(ident = "12345678901", id = 1L)
        val sak = Sak(
            id = 1L,
            person = person,
            saksnummer = Saksnummer("SAK123"),
            sakStatus = SakStatus.UTREDES,
            sistOppdatert = LocalDateTime.of(2024, 1, 1, 10, 0)
        )

        return Behandling(
            id = BehandlingId(1),
            referanse = referanse,
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.UTREDES,
            opprettetTid = LocalDateTime.of(2024, 1, 1, 10, 0),
            mottattTid = LocalDateTime.of(2024, 1, 1, 9, 0),
            versjon = Versjon("1.0"),
            søknadsformat = SøknadsFormat.DIGITAL,
            gjeldendeAvklaringsBehov = gjeldendeAvklaringsbehov,
            sisteLøsteAvklaringsbehov = sisteLøsteAvklaringsbehov,
            sisteSaksbehandlerSomLøstebehov = sisteSaksbehandlerSomLøstebehov,
            hendelser = hendelser,
            årsaker = listOf(Vurderingsbehov.SØKNAD)
        )
    }

    private fun lagBehandlingHendelse(
        tidspunkt: LocalDateTime = LocalDateTime.of(2024, 1, 1, 10, 0),
        hendelsesTidspunkt: LocalDateTime = tidspunkt,
        avklaringsBehov: String? = null,
        sisteLøsteAvklaringsbehov: String? = null,
        sisteSaksbehandlerSomLøstebehov: String? = null,
        saksbehandler: Saksbehandler? = null,
        avklaringsbehovStatus: AvklaringsbehovStatus? = null
    ): BehandlingHendelse {
        return BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = hendelsesTidspunkt,
            avklaringsBehov = avklaringsBehov,
            sisteLøsteAvklaringsbehov = sisteLøsteAvklaringsbehov,
            sisteSaksbehandlerSomLøstebehov = sisteSaksbehandlerSomLøstebehov,
            saksbehandler = saksbehandler,
            avklaringsbehovStatus = avklaringsbehovStatus,
            versjon = Versjon("1.0"),
            status = BehandlingStatus.UTREDES,
            mottattTid = LocalDateTime.of(2024, 1, 1, 9, 0),
            søknadsformat = SøknadsFormat.DIGITAL,
            relatertBehandlingReferanse = null,
            steggruppe = null,
            venteÅrsak = null,
            returÅrsak = null,
            ansvarligBeslutter = null,
            vedtakstidspunkt = null,
            resultat = null
        )
    }

    @Test
    fun `saksbehandler skal være null når oppgave ikke er reservert ennå`() {
        val behandlingRef = UUID.randomUUID()
        val hendelse = lagBehandlingHendelse(
            avklaringsBehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid"
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            hendelser = listOf(hendelse)
        )

        val behandlingService = BehandlingService(
            behandlingRepository = FakeBehandlingRepository(),
            skjermingService = skjermingService
        )

        val mapper = BQBehandlingMapper(
            behandlingService = behandlingService,
            rettighetstypeperiodeRepository = FakeRettighetsTypeRepository(),
            oppgaveRepository = FakeOppgaveRepository(),
            sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
            clock = fixedClock
        )

        val result =
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false).last()

        assertThat(result.saksbehandler)
            .describedAs("Saksbehandler should be null when no oppgave is reserved yet")
            .isNull()
        assertThat(result.behandlingMetode).isEqualTo(BehandlingMetode.KVALITETSSIKRING)
        assertThat(result.ansvarligEnhetKode).isNull()
    }

    @Test
    fun `saksbehandler skal være fra oppgave når oppgave er reservert`() {
        val behandlingRef = UUID.randomUUID()
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 14, 0)
        val hendelse = lagBehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            avklaringsBehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid"
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            hendelser = listOf(hendelse)
        )

        val oppgaveRepo = FakeOppgaveHendelseRepository()
        oppgaveRepo.addEnhetReservasjon(
            behandlingRef,
            Definisjon.KVALITETSSIKRING.kode.name,
            listOf(
                EnhetReservasjonOgTidspunkt(
                    enhet = "0400",
                    reservertAv = "Kvaliguy",
                    tidspunkt = tidspunkt
                )
            )
        )

        val oppgaveRepository = FakeOppgaveRepository()
        oppgaveRepository.addOppgave(
            behandling.id(),
            no.nav.aap.statistikk.oppgave.Oppgave(
                identifikator = 123L,
                avklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
                enhet = no.nav.aap.statistikk.enhet.Enhet(0L, "0400"),
                person = null,
                status = no.nav.aap.statistikk.oppgave.Oppgavestatus.OPPRETTET,
                opprettetTidspunkt = tidspunkt,
                behandlingReferanse = no.nav.aap.statistikk.oppgave.BehandlingReferanse(
                    id = null,
                    referanse = behandlingRef
                ),
                hendelser = listOf(
                    OppgaveHendelse(
                        hendelse = no.nav.aap.statistikk.oppgave.HendelseType.RESERVERT,
                        oppgaveId = 123L,
                        mottattTidspunkt = tidspunkt,
                        sendtTid = tidspunkt,
                        enhet = "0400",
                        avklaringsbehovKode = Definisjon.KVALITETSSIKRING.kode.name,
                        status = no.nav.aap.statistikk.oppgave.Oppgavestatus.OPPRETTET,
                        reservertAv = "Kvaliguy",
                        reservertTidspunkt = tidspunkt,
                        opprettetTidspunkt = tidspunkt,
                        endretAv = "Kvaliguy",
                        endretTidspunkt = tidspunkt,
                        versjon = 1L
                    )
                )
            )
        )

        val behandlingService = BehandlingService(
            behandlingRepository = FakeBehandlingRepository(),
            skjermingService = skjermingService
        )

        val mapper = BQBehandlingMapper(
            behandlingService = behandlingService,
            rettighetstypeperiodeRepository = FakeRettighetsTypeRepository(),
            oppgaveRepository = oppgaveRepository,
            sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
            clock = fixedClock
        )

        val result =
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false).last()

        assertThat(result.saksbehandler).isEqualTo("Kvaliguy")
        assertThat(result.ansvarligEnhetKode).isEqualTo("0400")
    }

    @Test
    fun `skal ikke bruke saksbehandler fra forrige avklaringsbehov`() {
        val behandlingRef = UUID.randomUUID()
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 14, 0)

        val hendelse1 = lagBehandlingHendelse(
            tidspunkt = tidspunkt.minusHours(2),
            avklaringsBehov = Definisjon.AVKLAR_SYKDOM.kode.name,
            saksbehandler = Saksbehandler("Kompanjong Korrodheid")
        )

        val hendelse2 = lagBehandlingHendelse(
            tidspunkt = tidspunkt,
            avklaringsBehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            saksbehandler = Saksbehandler("Kompanjong Korrodheid")
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            hendelser = listOf(hendelse1, hendelse2)
        )

        val oppgaveRepo = FakeOppgaveHendelseRepository()
        oppgaveRepo.addEnhetReservasjon(
            behandlingRef,
            Definisjon.AVKLAR_SYKDOM.kode.name,
            listOf(
                EnhetReservasjonOgTidspunkt(
                    enhet = "0401",
                    reservertAv = "Kompanjong Korrodheid",
                    tidspunkt = tidspunkt.minusHours(2)
                )
            )
        )

        val behandlingService = BehandlingService(
            behandlingRepository = FakeBehandlingRepository(),
            skjermingService = skjermingService
        )

        val mapper = BQBehandlingMapper(
            behandlingService = behandlingService,
            rettighetstypeperiodeRepository = FakeRettighetsTypeRepository(),
            oppgaveRepository = FakeOppgaveRepository(),
            sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
            clock = fixedClock
        )

        val result =
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false).last()

        assertThat(result.saksbehandler)
            .describedAs("Should not use saksbehandler from previous avklaringsbehov")
            .isNull()
        assertThat(result.behandlingMetode).isEqualTo(BehandlingMetode.KVALITETSSIKRING)
    }

    @Test
    fun `saksbehandler skal være null når ikke oppgave brukes`() {
        val behandlingRef = UUID.randomUUID()
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 14, 0)
        val hendelse = lagBehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            avklaringsBehov = Definisjon.FATTE_VEDTAK.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kvaliguy",
            avklaringsbehovStatus = AvklaringsbehovStatus.OPPRETTET
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.FATTE_VEDTAK.kode.name,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
            sisteSaksbehandlerSomLøstebehov = "Kvaliguy",
            hendelser = listOf(hendelse)
        )

        val behandlingService = BehandlingService(
            behandlingRepository = FakeBehandlingRepository(),
            skjermingService = skjermingService
        )

        val mapper = BQBehandlingMapper(
            behandlingService = behandlingService,
            rettighetstypeperiodeRepository = FakeRettighetsTypeRepository(),
            oppgaveRepository = FakeOppgaveRepository(),
            sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
            clock = fixedClock
        )

        // Execute
        val result =
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false).last()

        // Assert: Saksbehandler skal være null når det ikke finnes oppgave-data
        assertThat(result.saksbehandler)
            .describedAs("Should be null when no oppgave data available")
            .isNull()
        assertThat(result.behandlingMetode).isEqualTo(BehandlingMetode.FATTE_VEDTAK)
    }
}

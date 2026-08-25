package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.oppgave.EnhetReservasjonOgTidspunkt
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.oppgave.BehandlingReferanse
import no.nav.aap.statistikk.oppgave.HendelseType
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.OppgaveRepository
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.fakes.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.fakes.FakeOppgaveHendelseRepository
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import no.nav.aap.statistikk.testutils.fakes.FakeRettighetsTypeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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

    private class FakeOppgaveRepository : OppgaveRepository {
        private val oppgaver =
            mutableMapOf<BehandlingId, MutableList<Oppgave>>()

        fun addOppgave(behandlingId: BehandlingId, oppgave: Oppgave) {
            oppgaver.getOrPut(behandlingId) { mutableListOf() }.add(oppgave)
        }

        override fun lagreOppgave(oppgave: Oppgave) = 0L
        override fun oppdaterOppgave(oppgave: Oppgave) = Unit
        override fun hentOppgaverForEnhet(enhet: Enhet) =
            emptyList<Oppgave>()

        override fun hentOppgave(identifikator: Long) = null
        override fun hentOppgaverForBehandling(behandlingId: BehandlingId) =
            oppgaver[behandlingId] ?: emptyList()
    }

    private fun lagBehandling(
        referanse: UUID = UUID.randomUUID(),
        gjeldendeAvklaringsbehov: Definisjon? = null,
        sisteLøsteAvklaringsbehov: Definisjon? = null,
        sisteSaksbehandlerSomLøstebehov: String? = null,
        hendelser: List<BehandlingHendelse>
    ): Behandling {
        val person = Person(ident = no.nav.aap.statistikk.person.Ident("12345678901"), id = 1L)
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
        avklaringsBehov: Definisjon? = null,
        sisteLøsteAvklaringsbehov: Definisjon? = null,
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
            avklaringsBehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid"
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
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
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false)

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
            avklaringsBehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid"
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
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
            Oppgave(
                identifikator = 123L,
                avklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
                enhet = Enhet(0L, "0400"),
                person = null,
                status = Oppgavestatus.OPPRETTET,
                opprettetTidspunkt = tidspunkt,
                behandlingReferanse = BehandlingReferanse(
                    id = null,
                    referanse = behandlingRef
                ),
                hendelser = listOf(
                    OppgaveHendelse(
                        hendelse = HendelseType.RESERVERT,
                        oppgaveId = 123L,
                        mottattTidspunkt = tidspunkt,
                        sendtTid = tidspunkt,
                        enhet = "0400",
                        avklaringsbehovKode = Definisjon.KVALITETSSIKRING.kode.name,
                        status = Oppgavestatus.OPPRETTET,
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
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false)

        assertThat(result.saksbehandler).isEqualTo("Kvaliguy")
        assertThat(result.ansvarligEnhetKode).isEqualTo("0400")
    }

    @Test
    fun `skal ikke bruke saksbehandler fra forrige avklaringsbehov`() {
        val behandlingRef = UUID.randomUUID()
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 14, 0)

        val hendelse1 = lagBehandlingHendelse(
            tidspunkt = tidspunkt.minusHours(2),
            avklaringsBehov = Definisjon.AVKLAR_SYKDOM,
            saksbehandler = Saksbehandler("Kompanjong Korrodheid")
        )

        val hendelse2 = lagBehandlingHendelse(
            tidspunkt = tidspunkt,
            avklaringsBehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            saksbehandler = Saksbehandler("Kompanjong Korrodheid")
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
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
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false)

        assertThat(result.saksbehandler)
            .describedAs("Should not use saksbehandler from previous avklaringsbehov")
            .isNull()
        assertThat(result.behandlingMetode).isEqualTo(BehandlingMetode.KVALITETSSIKRING)
    }

    @Test
    fun `ansvarligEnhet skal ikke være null for UTREDES når oppgaven er lukket etter cutoff-tidspunkt`() {
        // Reproduserer produksjonsfeil: behandling er UTREDES med avklaringsbehov 5026 (AVKLAR_SYKDOM).
        // Oppgaven ble OPPRETTET og LUKKET – men LUKKET skjedde etter at cutoff-jobben kjørte.
        // Event sourcing ser LUKKET-hendelsen og nuller ut enhet. Fallback i ansvarligEnhet() skal
        // hente enhet direkte fra oppgave-tabellen og returnere riktig enhet.
        val behandlingRef = UUID.randomUUID()
        val cutoffTidspunkt = LocalDateTime.of(2024, 1, 10, 12, 0)
        val lukketEtterCutoff = cutoffTidspunkt.plusMinutes(42)

        val hendelse = lagBehandlingHendelse(
            tidspunkt = cutoffTidspunkt,
            avklaringsBehov = Definisjon.AVKLAR_SYKDOM,
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            hendelser = listOf(hendelse)
        )

        val oppgaveRepository = FakeOppgaveRepository()
        oppgaveRepository.addOppgave(
            behandling.id(),
            Oppgave(
                identifikator = 1L,
                avklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
                enhet = Enhet(0L, "0216"),
                person = null,
                status = Oppgavestatus.AVSLUTTET,
                opprettetTidspunkt = cutoffTidspunkt,
                behandlingReferanse = BehandlingReferanse(
                    id = null,
                    referanse = behandlingRef
                ),
                hendelser = listOf(
                    OppgaveHendelse(
                        hendelse = HendelseType.OPPRETTET,
                        oppgaveId = 1L,
                        mottattTidspunkt = cutoffTidspunkt,
                        sendtTid = cutoffTidspunkt,
                        enhet = "0216",
                        avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM.kode.name,
                        status = Oppgavestatus.OPPRETTET,
                        opprettetTidspunkt = cutoffTidspunkt,
                        endretTidspunkt = cutoffTidspunkt,
                        versjon = 1L
                    ),
                    OppgaveHendelse(
                        hendelse = HendelseType.LUKKET,
                        oppgaveId = 1L,
                        mottattTidspunkt = lukketEtterCutoff,
                        sendtTid = lukketEtterCutoff,
                        enhet = "0216",
                        avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM.kode.name,
                        status = Oppgavestatus.AVSLUTTET,
                        opprettetTidspunkt = cutoffTidspunkt,
                        endretTidspunkt = lukketEtterCutoff,
                        versjon = 2L
                    )
                )
            )
        )

        val mapper = BQBehandlingMapper(
            behandlingService = BehandlingService(
                behandlingRepository = FakeBehandlingRepository(),
                skjermingService = skjermingService
            ),
            rettighetstypeperiodeRepository = FakeRettighetsTypeRepository(),
            oppgaveRepository = oppgaveRepository,
            sakstatistikkEventSourcing = SakstatistikkEventSourcing(),
            clock = fixedClock
        )

        val result = mapper.bqBehandlingForBehandling(behandling, erSkjermet = false)

        assertThat(result.ansvarligEnhetKode)
            .describedAs("Enhet skal ikke være null selv om oppgaven ble lukket etter cutoff-tidspunktet")
            .isEqualTo("0216")
    }

    @Test
    fun `advarsel utløses når oppgave-systemet selv-reserverer nyåpnet kvalitetssikring til forrige saksbehandler`() {
        // Reproduserer produksjons-"feil" for ekte sak: idet forrige avklaringsbehov
        // (f.eks. AVKLAR_SYKDOM) lukkes av LOKAL_SAKSBEHANDLER, sender Oppgave-systemet et
        // RESERVERT-event for det nyåpnede KVALITETSSIKRING-avklaringsbehovet med
        // reservertAv=LOKAL_SAKSBEHANDLER, FØR det korrigeres (AVRESERVERT/reassignment) kort tid etter.
        // Mapperen bygger snapshot ut fra behandling (som kan være "fryst" på cutoff-tidspunkt)
        // + ALLE oppgave-hendelser (uten cutoff) og bruker kun det siste, sammenslåtte snapshotet.
        // Dette testet viser at MAPPEREN korrekt (og etter design) reflekterer denne transiente
        // tilstanden når den kalles akkurat i dette vinduet – dvs. dette er IKKE en
        // cutoff/rekkefølge-bug i aap-statistikk, men en ekte, om enn kortvarig, oppstrøms
        // selv-reservering fra Oppgave-systemet som blir fanget opp og lagret som den er.
        val behandlingRef = UUID.randomUUID()
        val forrigeStegLukket = LocalDateTime.of(2024, 1, 10, 8, 0)
        val ksÅpnetOgSelvReservert = forrigeStegLukket.plusMinutes(1)

        val hendelse1 = lagBehandlingHendelse(
            tidspunkt = forrigeStegLukket,
            avklaringsBehov = Definisjon.AVKLAR_SYKDOM,
            saksbehandler = Saksbehandler("LOKAL_SAKSBEHANDLER")
        )

        val hendelse2 = lagBehandlingHendelse(
            tidspunkt = ksÅpnetOgSelvReservert,
            avklaringsBehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "LOKAL_SAKSBEHANDLER",
            avklaringsbehovStatus = AvklaringsbehovStatus.OPPRETTET
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "LOKAL_SAKSBEHANDLER",
            hendelser = listOf(hendelse1, hendelse2)
        )

        val oppgaveRepository = FakeOppgaveRepository()
        oppgaveRepository.addOppgave(
            behandling.id(),
            Oppgave(
                identifikator = 999L,
                avklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
                enhet = Enhet(0L, "0400"),
                person = null,
                status = Oppgavestatus.OPPRETTET,
                opprettetTidspunkt = ksÅpnetOgSelvReservert,
                behandlingReferanse = BehandlingReferanse(
                    id = null,
                    referanse = behandlingRef
                ),
                hendelser = listOf(
                    OppgaveHendelse(
                        hendelse = HendelseType.RESERVERT,
                        oppgaveId = 999L,
                        mottattTidspunkt = ksÅpnetOgSelvReservert,
                        sendtTid = ksÅpnetOgSelvReservert,
                        enhet = "0400",
                        avklaringsbehovKode = Definisjon.KVALITETSSIKRING.kode.name,
                        status = Oppgavestatus.OPPRETTET,
                        reservertAv = "LOKAL_SAKSBEHANDLER",
                        reservertTidspunkt = ksÅpnetOgSelvReservert,
                        opprettetTidspunkt = ksÅpnetOgSelvReservert,
                        endretAv = "LOKAL_SAKSBEHANDLER",
                        endretTidspunkt = ksÅpnetOgSelvReservert,
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

        val result = mapper.bqBehandlingForBehandling(behandling, erSkjermet = false)

        // Dette er selve "bugen": saksbehandler gjenbrukes fra forrige behandlingmetode
        // fordi Oppgave-systemet (kortvarig) selv-reserverte den nye KS-oppgaven til samme person.
        assertThat(result.saksbehandler)
            .describedAs(
                "Viser den transiente selv-reserveringen fra oppgave-systemet: samme saksbehandler " +
                        "som nettopp løste forrige avklaringsbehov blir stående som saksbehandler på " +
                        "det nyåpnede kvalitetssikrings-steget, inntil en senere AVRESERVERT/omfordeling korrigerer det."
            )
            .isEqualTo("LOKAL_SAKSBEHANDLER")
        assertThat(result.behandlingMetode).isEqualTo(BehandlingMetode.KVALITETSSIKRING)
    }

    @Test
    fun `saksbehandler skal være null når ikke oppgave brukes`() {
        val behandlingRef = UUID.randomUUID()
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 14, 0)
        val hendelse = lagBehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            avklaringsBehov = Definisjon.FATTE_VEDTAK,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = "Kvaliguy",
            avklaringsbehovStatus = AvklaringsbehovStatus.OPPRETTET
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.FATTE_VEDTAK,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
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
            mapper.bqBehandlingForBehandling(behandling, erSkjermet = false)

        // Assert: Saksbehandler skal være null når det ikke finnes oppgave-data
        assertThat(result.saksbehandler)
            .describedAs("Should be null when no oppgave data available")
            .isNull()
        assertThat(result.behandlingMetode).isEqualTo(BehandlingMetode.FATTE_VEDTAK)
    }

    @ParameterizedTest
    @MethodSource("venteÅrsakOgReturstatusKombinasjoner")
    fun `behandlingStatus skal aldri overstige 100 tegn for noen kombinasjon av venteAarsak og returstatus`(
        venteÅrsak: ÅrsakTilSattPåVent,
        returStatus: AvklaringsbehovStatus?
    ) {
        // BigQuery-mottaket tåler ikke mer enn 100 tegn for dimensjons-/kodeverksfelt som behandling_status.
        val behandling = lagBehandling(hendelser = emptyList())
            .copy(venteÅrsak = venteÅrsak.name, gjeldendeAvklaringsbehovStatus = returStatus)

        val resultat = BQBehandlingMapper.behandlingStatus(behandling)

        assertThat(resultat.length)
            .describedAs(
                "behandlingStatus for venteÅrsak=$venteÅrsak, returStatus=$returStatus " +
                        "skal være maks 100 tegn, var: $resultat"
            )
            .isLessThan(100)
    }

    companion object {
        @JvmStatic
        fun venteÅrsakOgReturstatusKombinasjoner(): List<Arguments> {
            // Full kryssliste av alle ÅrsakTilSattPåVent- og alle AvklaringsbehovStatus-verdier (inkl. null),
            // ikke bare de statusene som faktisk regnes som "returnert" i dag.
            val alleStatuser: List<AvklaringsbehovStatus?> = listOf(null) + AvklaringsbehovStatus.entries
            return ÅrsakTilSattPåVent.entries.flatMap { venteÅrsak ->
                alleStatuser.map { returStatus -> Arguments.of(venteÅrsak, returStatus) }
            }
        }
    }
}

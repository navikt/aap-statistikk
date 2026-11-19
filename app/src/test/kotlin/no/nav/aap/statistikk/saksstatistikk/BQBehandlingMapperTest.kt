package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.avsluttetbehandling.RettighetsType
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.oppgave.BehandlingReferanse
import no.nav.aap.statistikk.oppgave.HendelseType
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.oppgave.tilOppgave
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus

class BQBehandlingMapperTest {

    private val mapper = BQBehandlingMapper

    private fun grunnlag(behandling: Behandling, oppgaver: List<Oppgave> = emptyList(), erSkjermet: Boolean = false) =
        SaksstatistikkGrunnlag(
            behandling = behandling,
            erSkjermet = erSkjermet,
            relatertBehandlingUUID = null,
            oppgaver = oppgaver,
            rettighetstyper = emptyList(),
            tekniskTid = LocalDateTime.of(2024, 1, 15, 12, 0),
        )

    private fun lagBehandling(
        referanse: UUID = UUID.randomUUID(),
        gjeldendeAvklaringsbehov: Definisjon? = null,
        sisteLøsteAvklaringsbehov: Definisjon? = null,
        sisteSaksbehandlerSomLøstebehov: String? = null,
        hendelser: List<BehandlingHendelse>,
    ): Behandling {
        val person = Person(ident = no.nav.aap.statistikk.person.Ident("12345678901"), id = 1L)
        val sak = Sak(
            id = 1L,
            person = person,
            saksnummer = Saksnummer("SAK123"),
            sakStatus = SakStatus.UTREDES,
            sistOppdatert = LocalDateTime.of(2024, 1, 1, 10, 0),
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
            årsaker = listOf(Vurderingsbehov.SØKNAD),
        )
    }

    private fun lagBehandlingHendelse(
        tidspunkt: LocalDateTime = LocalDateTime.of(2024, 1, 1, 10, 0),
        hendelsesTidspunkt: LocalDateTime = tidspunkt,
        avklaringsBehov: Definisjon? = null,
        sisteLøsteAvklaringsbehov: Definisjon? = null,
        sisteSaksbehandlerSomLøstebehov: String? = null,
        saksbehandler: Saksbehandler? = null,
        avklaringsbehovStatus: AvklaringsbehovStatus? = null,
    ): BehandlingHendelse = BehandlingHendelse(
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
        resultat = null,
    )

    @Test
    fun `innvilget resultat uten rettighetsperioder gir generell AAP-kode`() {
        assertThat(BQBehandlingMapper.behandlingResultat(ResultatKode.INNVILGET, emptyList()))
            .isEqualTo("AAP")
    }

    @Test
    fun `innvilget resultat bruker første periode i innsendt rekkefølge`() {
        val februar = RettighetstypePeriode(
            LocalDate.of(2024, 2, 1),
            LocalDate.of(2024, 2, 29),
            RettighetsType.STUDENT,
        )
        val januar = RettighetstypePeriode(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 31),
            RettighetsType.BISTANDSBEHOV,
        )

        assertThat(BQBehandlingMapper.behandlingResultat(ResultatKode.INNVILGET, listOf(februar, januar)))
            .isEqualTo("AAP_STUDENT")
        assertThat(BQBehandlingMapper.behandlingResultat(ResultatKode.INNVILGET, listOf(januar, februar)))
            .isEqualTo("AAP_BISTANDSBEHOV")
    }

    @Test
    fun `rettighetsperioder erstatter ikke manglende resultat`() {
        val periode = RettighetstypePeriode(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 31),
            RettighetsType.BISTANDSBEHOV,
        )

        assertThat(BQBehandlingMapper.behandlingResultat(null, listOf(periode)))
            .isEqualTo("UDEFINERT")
    }

    @ParameterizedTest
    @CsvSource("OPPRETTET,", "UTREDES,", "IVERKSETTES,AAP", "AVSLUTTET,AAP")
    fun `behandlingsstatus avgjør om resultatet publiseres`(status: BehandlingStatus, forventetResultat: String?) {
        val behandling = lagBehandling(
            hendelser = listOf(lagBehandlingHendelse().copy(status = status, resultat = ResultatKode.INNVILGET)),
        )
        assertThat(mapper.bqBehandlingForBehandling(grunnlag(behandling)).behandlingResultat)
            .isEqualTo(forventetResultat)
    }

    @ParameterizedTest
    @CsvSource("AVSLAG,AVSLAG", ",UDEFINERT")
    fun `publisert resultat kommer fra siste hendelse uten fallback`(
        sisteResultat: ResultatKode?,
        forventetResultat: String,
    ) {
        val innvilget = lagBehandlingHendelse().copy(
            status = BehandlingStatus.AVSLUTTET,
            resultat = ResultatKode.INNVILGET,
        )
        val behandling = lagBehandling(
            hendelser = listOf(
                innvilget,
                innvilget.copy(
                    hendelsesTidspunkt = innvilget.hendelsesTidspunkt.plusMinutes(1),
                    resultat = sisteResultat,
                ),
            ),
        ).copy(resultat = ResultatKode.INNVILGET)

        assertThat(mapper.bqBehandlingForBehandling(grunnlag(behandling)).behandlingResultat)
            .isEqualTo(forventetResultat)
    }

    @Test
    fun `skjermet behandling skjuler enhet saksbehandler og beslutter`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(avklaringsBehov = Definisjon.FATTE_VEDTAK)
                    .copy(ansvarligBeslutter = "Beslutter"),
            ),
        )

        val resultat = mapper.bqBehandlingForBehandling(grunnlag(behandling, erSkjermet = true))

        assertThat(resultat.ansvarligEnhetKode).isEqualTo("-5")
        assertThat(resultat.saksbehandler).isEqualTo("-5")
        assertThat(resultat.ansvarligBeslutter).isEqualTo("-5")
    }

    @Test
    fun `saksbehandler skal være null når oppgave ikke er reservert ennå`() {
        val behandlingRef = UUID.randomUUID()
        val hendelse = lagBehandlingHendelse(
            avklaringsBehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            hendelser = listOf(hendelse),
        )

        val result =
            mapper.bqBehandlingForBehandling(grunnlag(behandling))

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
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            hendelser = listOf(hendelse),
        )

        val oppgaver = listOf(
            Oppgave(
                identifikator = 123L,
                avklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
                enhet = Enhet(0L, "0400"),
                person = null,
                status = Oppgavestatus.OPPRETTET,
                opprettetTidspunkt = tidspunkt,
                behandlingReferanse = BehandlingReferanse(
                    id = null,
                    referanse = behandlingRef,
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
                        versjon = 1L,
                    ),
                ),
            ),
        )

        val result =
            mapper.bqBehandlingForBehandling(grunnlag(behandling, oppgaver))

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
            saksbehandler = Saksbehandler("Kompanjong Korrodheid"),
        )

        val hendelse2 = lagBehandlingHendelse(
            tidspunkt = tidspunkt,
            avklaringsBehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            saksbehandler = Saksbehandler("Kompanjong Korrodheid"),
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "Kompanjong Korrodheid",
            hendelser = listOf(hendelse1, hendelse2),
        )

        val result =
            mapper.bqBehandlingForBehandling(grunnlag(behandling))

        assertThat(result.saksbehandler)
            .describedAs("Should not use saksbehandler from previous avklaringsbehov")
            .isNull()
        assertThat(result.behandlingMetode).isEqualTo(BehandlingMetode.KVALITETSSIKRING)
    }

    @Test
    fun `mellom behov brukes historisk enhet fra sist løste behov uten reservasjonen`() {
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 12, 0)
        val opprettet = oppgavehendelseFørOvergang(tidspunkt.minusMinutes(2))
        val lukket = opprettet.copy(
            hendelse = HendelseType.LUKKET,
            status = Oppgavestatus.AVSLUTTET,
            sendtTid = tidspunkt,
            mottattTidspunkt = tidspunkt.plusSeconds(5),
            reservertAv = null,
            reservertTidspunkt = null,
        )
        val fremtidig = opprettet.copy(
            hendelse = HendelseType.OPPDATERT,
            sendtTid = tidspunkt.plusMinutes(1),
            mottattTidspunkt = tidspunkt.plusMinutes(1),
            enhet = "9999",
        )
        val forsinket = opprettet.copy(
            sendtTid = tidspunkt.minusMinutes(1),
            mottattTidspunkt = tidspunkt.plusMinutes(2),
            enhet = "4500",
        )

        val resultat = mapMellomBehov(listOf(opprettet, lukket, fremtidig, forsinket))

        assertThat(resultat.ansvarligEnhetKode).isEqualTo("4491")
        assertThat(resultat.saksbehandler).isNull()
        assertThat(resultat.behandlingMetode).isEqualTo(BehandlingMetode.FATTE_VEDTAK)
        assertThat(resultat.endretTid).isEqualTo(tidspunkt)
    }

    @Test
    fun `mellom behov kreves historiske oppgavehendelser for sist løste behov`() {
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 12, 0)
        val fremtidig = oppgavehendelseFørOvergang(tidspunkt.plusSeconds(1))
        val annetBehov = oppgavehendelseFørOvergang(tidspunkt.minusMinutes(1))
            .copy(avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM.kode.name)

        assertThat(mapMellomBehov(emptyList()).ansvarligEnhetKode).isNull()
        assertThat(mapMellomBehov(listOf(fremtidig, annetBehov)).ansvarligEnhetKode).isNull()
        assertThat(
            mapMellomBehov(
                listOf(oppgavehendelseFørOvergang(tidspunkt.minusMinutes(1))),
                gjeldendeBehov = Definisjon.AVKLAR_SYKDOM,
            ).ansvarligEnhetKode,
        ).isNull()
    }

    @Test
    fun `oppgavejobber bruker eksplisitt cutoff også etter siste behandlingshendelse`() {
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 12, 0)
        val opprettet = oppgavehendelseFørOvergang(tidspunkt.plusSeconds(30))
        val fremtidig = opprettet.copy(
            sendtTid = tidspunkt.plusMinutes(2),
            mottattTidspunkt = tidspunkt.plusMinutes(2),
            enhet = "9999",
        )

        val resultat = mapMellomBehov(
            listOf(opprettet, fremtidig),
            cutoffTidspunkt = opprettet.sendtTid,
        )

        assertThat(resultat.ansvarligEnhetKode).isEqualTo("4491")
        assertThat(resultat.endretTid).isEqualTo(opprettet.sendtTid)
    }

    @Test
    fun `bruker oppgavedata fra valgt tidspunkt, eller nyeste data når tidspunkt ikke er angitt`() {
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 12, 0)
        val behandling = lagBehandling(
            gjeldendeAvklaringsbehov = Definisjon.FATTE_VEDTAK,
            hendelser = listOf(
                lagBehandlingHendelse(tidspunkt = tidspunkt, avklaringsBehov = Definisjon.FATTE_VEDTAK),
            ),
        )
        val opprettet = oppgavehendelseFørOvergang(tidspunkt)
        val reservert = opprettet.copy(
            hendelse = HendelseType.RESERVERT,
            sendtTid = tidspunkt.plusMinutes(2),
            mottattTidspunkt = tidspunkt.plusMinutes(2),
            enhet = "9999",
            reservertAv = "Ny beslutter",
            reservertTidspunkt = tidspunkt.plusMinutes(2),
        )
        val oppgaver = listOf(
            listOf(opprettet, reservert).tilOppgave().copy(
                behandlingReferanse = BehandlingReferanse(referanse = behandling.referanse),
            ),
        )
        val resultat = mapper.bqBehandlingForBehandling(grunnlag(behandling, oppgaver))
        val historisk = mapper.bqBehandlingForBehandling(
            grunnlag = grunnlag(behandling, oppgaver),
            cutoffTidspunkt = tidspunkt,
        )

        assertThat(resultat.endretTid).isEqualTo(reservert.sendtTid)
        assertThat(resultat.ansvarligEnhetKode).isEqualTo("9999")
        assertThat(resultat.saksbehandler).isEqualTo("Ny beslutter")
        assertThat(historisk.endretTid).isEqualTo(tidspunkt)
        assertThat(historisk.ansvarligEnhetKode).isEqualTo("4491")
        assertThat(historisk.saksbehandler).isEqualTo("Beslutter")
    }

    private fun oppgavehendelseFørOvergang(tidspunkt: LocalDateTime) = OppgaveHendelse(
        hendelse = HendelseType.OPPRETTET,
        oppgaveId = 1L,
        mottattTidspunkt = tidspunkt,
        sendtTid = tidspunkt,
        enhet = "4491",
        avklaringsbehovKode = Definisjon.FATTE_VEDTAK.kode.name,
        status = Oppgavestatus.OPPRETTET,
        reservertAv = "Beslutter",
        reservertTidspunkt = tidspunkt,
        opprettetTidspunkt = tidspunkt,
        versjon = 1L,
    )

    private fun mapMellomBehov(
        oppgavehendelser: List<OppgaveHendelse>,
        gjeldendeBehov: Definisjon? = null,
        cutoffTidspunkt: LocalDateTime = LocalDateTime.of(2024, 1, 10, 12, 0),
    ): BQBehandling {
        val tidspunkt = LocalDateTime.of(2024, 1, 10, 12, 0)
        val behandling = lagBehandling(hendelser = emptyList())
            .leggTilHendelse(
                lagBehandlingHendelse(
                    tidspunkt = tidspunkt.minusMinutes(3),
                    avklaringsBehov = Definisjon.FATTE_VEDTAK,
                ),
            ).leggTilHendelse(
                lagBehandlingHendelse(
                    tidspunkt = tidspunkt,
                    avklaringsBehov = gjeldendeBehov,
                    sisteLøsteAvklaringsbehov = Definisjon.FATTE_VEDTAK,
                    sisteSaksbehandlerSomLøstebehov = "Beslutter",
                ),
            )
        val oppgaver = if (oppgavehendelser.isNotEmpty()) {
            listOf(
                oppgavehendelser.tilOppgave().copy(
                    behandlingReferanse = BehandlingReferanse(referanse = behandling.referanse),
                ),
            )
        } else {
            emptyList()
        }
        return mapper.bqBehandlingForBehandling(
            grunnlag = grunnlag(behandling, oppgaver),
            cutoffTidspunkt = cutoffTidspunkt,
        )
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
            hendelser = listOf(hendelse),
        )

        val oppgaver = listOf(
            Oppgave(
                identifikator = 1L,
                avklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
                enhet = Enhet(0L, "0216"),
                person = null,
                status = Oppgavestatus.AVSLUTTET,
                opprettetTidspunkt = cutoffTidspunkt,
                behandlingReferanse = BehandlingReferanse(
                    id = null,
                    referanse = behandlingRef,
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
                        versjon = 1L,
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
                        versjon = 2L,
                    ),
                ),
            ),
        )

        val result = mapper.bqBehandlingForBehandling(grunnlag(behandling, oppgaver))

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
            saksbehandler = Saksbehandler("LOKAL_SAKSBEHANDLER"),
        )

        val hendelse2 = lagBehandlingHendelse(
            tidspunkt = ksÅpnetOgSelvReservert,
            avklaringsBehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "LOKAL_SAKSBEHANDLER",
            avklaringsbehovStatus = AvklaringsbehovStatus.OPPRETTET,
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM,
            sisteSaksbehandlerSomLøstebehov = "LOKAL_SAKSBEHANDLER",
            hendelser = listOf(hendelse1, hendelse2),
        )

        val oppgaver = listOf(
            Oppgave(
                identifikator = 999L,
                avklaringsbehov = Definisjon.KVALITETSSIKRING.kode.name,
                enhet = Enhet(0L, "0400"),
                person = null,
                status = Oppgavestatus.OPPRETTET,
                opprettetTidspunkt = ksÅpnetOgSelvReservert,
                behandlingReferanse = BehandlingReferanse(
                    id = null,
                    referanse = behandlingRef,
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
                        versjon = 1L,
                    ),
                ),
            ),
        )

        val result = mapper.bqBehandlingForBehandling(grunnlag(behandling, oppgaver))

        // Dette er selve "bugen": saksbehandler gjenbrukes fra forrige behandlingmetode
        // fordi Oppgave-systemet (kortvarig) selv-reserverte den nye KS-oppgaven til samme person.
        assertThat(result.saksbehandler)
            .describedAs(
                "Viser den transiente selv-reserveringen fra oppgave-systemet: samme saksbehandler " +
                    "som nettopp løste forrige avklaringsbehov blir stående som saksbehandler på " +
                    "det nyåpnede kvalitetssikrings-steget, inntil en senere AVRESERVERT/omfordeling korrigerer det.",
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
            avklaringsbehovStatus = AvklaringsbehovStatus.OPPRETTET,
        )

        val behandling = lagBehandling(
            referanse = behandlingRef,
            gjeldendeAvklaringsbehov = Definisjon.FATTE_VEDTAK,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = "Kvaliguy",
            hendelser = listOf(hendelse),
        )

        // Execute
        val result =
            mapper.bqBehandlingForBehandling(grunnlag(behandling))

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
        returStatus: AvklaringsbehovStatus?,
    ) {
        // BigQuery-mottaket tåler ikke mer enn 100 tegn for dimensjons-/kodeverksfelt som behandling_status.
        val behandling = lagBehandling(hendelser = emptyList())
            .copy(venteÅrsak = venteÅrsak.name, gjeldendeAvklaringsbehovStatus = returStatus)

        val resultat = BQBehandlingMapper.behandlingStatus(behandling)

        assertThat(resultat.length)
            .describedAs(
                "behandlingStatus for venteÅrsak=$venteÅrsak, returStatus=$returStatus " +
                    "skal være maks 100 tegn, var: $resultat",
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

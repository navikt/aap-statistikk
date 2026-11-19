package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.util.UUID

class BehandlingTilstandTest {
    private val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val sak = Sak(
        saksnummer = Saksnummer("SAK123"),
        person = Person(ident = Ident("0")),
        sakStatus = SakStatus.UTREDES,
        sistOppdatert = tidspunkt,
    )

    @Test
    fun `behandling uten historikk bruker sine egne felt som tilstand`() {
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.IVERKSETTES,
            opprettetTid = tidspunkt.minusDays(1),
            mottattTid = tidspunkt.minusDays(1),
            versjon = Versjon("1.0"),
            søknadsformat = SøknadsFormat.PAPIR,
            ansvarligBeslutter = "Beslutter",
            vedtakstidspunkt = tidspunkt,
            sisteSaksbehandler = "Saksbehandler",
            resultat = ResultatKode.INNVILGET,
            gjeldendeAvklaringsBehov = Definisjon.FATTE_VEDTAK,
            gjeldendeAvklaringsbehovStatus = Status.SENDT_TILBAKE_FRA_BESLUTTER,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = "Kvalitetssikrer",
            sistLøsteAvklaringsbehovTidspunkt = tidspunkt.minusHours(1),
            gjeldendeStegGruppe = StegGruppe.BREV,
            venteÅrsak = "VENTER_PÅ_OPPLYSNINGER",
            returÅrsak = "ANNET",
            returÅrsakkoblinger = listOf(ReturÅrsakkobling(Definisjon.AVKLAR_SYKDOM, listOf("ANNET"))),
            relatertBehandlingReferanse = "RELATERT",
            hendelser = emptyList(),
        )

        assertThat(behandling.nåværendeTilstand()).isEqualTo(
            BehandlingTilstand(
                status = BehandlingStatus.IVERKSETTES,
                versjon = Versjon("1.0"),
                mottattTid = tidspunkt.minusDays(1),
                søknadsformat = SøknadsFormat.PAPIR,
                ansvarligBeslutter = "Beslutter",
                vedtakstidspunkt = tidspunkt,
                sisteSaksbehandler = "Saksbehandler",
                resultat = ResultatKode.INNVILGET,
                gjeldendeAvklaringsBehov = AvklaringsbehovKode("5099"),
                gjeldendeAvklaringsbehovStatus = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                sisteLøsteAvklaringsbehov = AvklaringsbehovKode("5097"),
                sisteSaksbehandlerSomLøstebehov = "Kvalitetssikrer",
                sistLøsteAvklaringsbehovTidspunkt = tidspunkt.minusHours(1),
                gjeldendeStegGruppe = Behandlingssteggruppe.BREV,
                venteÅrsak = "VENTER_PÅ_OPPLYSNINGER",
                returÅrsak = "ANNET",
                returÅrsakkoblinger = listOf(ReturÅrsakTilstand(AvklaringsbehovKode("5003"), listOf("ANNET"))),
                relatertBehandlingReferanse = "RELATERT",
            ),
        )
    }

    @Test
    fun `nåværende tilstand hentes fra behandlingen og ikke fra historikken`() {
        val hendelse = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("historikk"),
            mottattTid = tidspunkt.plusDays(1),
            søknadsformat = SøknadsFormat.DIGITAL,
            ansvarligBeslutter = "Historikkbeslutter",
            vedtakstidspunkt = tidspunkt.plusMinutes(1),
            resultat = null,
            avklaringsbehovStatus = null,
            relatertBehandlingReferanse = null,
        )
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = tidspunkt.minusDays(1),
            status = BehandlingStatus.IVERKSETTES,
            versjon = Versjon("behandling"),
            mottattTid = tidspunkt.minusDays(1),
            søknadsformat = SøknadsFormat.PAPIR,
            ansvarligBeslutter = "Behandlingens beslutter",
            vedtakstidspunkt = tidspunkt,
            resultat = ResultatKode.INNVILGET,
            hendelser = listOf(hendelse),
        )

        assertThat(behandling.nåværendeTilstand()).isEqualTo(
            BehandlingTilstand(
                status = BehandlingStatus.IVERKSETTES,
                versjon = Versjon("behandling"),
                mottattTid = tidspunkt.minusDays(1),
                søknadsformat = SøknadsFormat.PAPIR,
                ansvarligBeslutter = "Behandlingens beslutter",
                vedtakstidspunkt = tidspunkt,
                resultat = ResultatKode.INNVILGET,
            ),
        )
    }

    @Test
    fun `hendelsen konverteres til behandlingstilstand`() {
        val hendelse = BehandlingHendelse(
            tidspunkt = null,
            hendelsesTidspunkt = tidspunkt.plusDays(2),
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("historikk"),
            mottattTid = tidspunkt.plusDays(1),
            søknadsformat = SøknadsFormat.DIGITAL,
            ansvarligBeslutter = "Beslutter",
            vedtakstidspunkt = tidspunkt,
            saksbehandler = Saksbehandler(id = 123, ident = "Saksbehandler"),
            resultat = ResultatKode.INNVILGET,
            avklaringsBehov = Definisjon.FATTE_VEDTAK,
            avklaringsbehovStatus = Status.SENDT_TILBAKE_FRA_BESLUTTER,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = "Kvalitetssikrer",
            sistLøsteAvklaringsbehovTidspunkt = tidspunkt.minusHours(1),
            steggruppe = StegGruppe.BREV,
            venteÅrsak = "VENTER_PÅ_OPPLYSNINGER",
            returÅrsak = "ANNET",
            returÅrsakkoblinger = listOf(
                ReturÅrsakkobling(Definisjon.AVKLAR_SYKDOM, listOf("ANNET", "MANGLENDE_UTREDNING")),
                ReturÅrsakkobling(Definisjon.KVALITETSSIKRING, listOf("ANNET")),
            ),
            relatertBehandlingReferanse = "RELATERT",
        )

        assertThat(hendelse.tilTilstand()).isEqualTo(
            BehandlingTilstand(
                status = BehandlingStatus.UTREDES,
                versjon = Versjon("historikk"),
                mottattTid = tidspunkt.plusDays(1),
                søknadsformat = SøknadsFormat.DIGITAL,
                ansvarligBeslutter = "Beslutter",
                vedtakstidspunkt = tidspunkt,
                sisteSaksbehandler = "Saksbehandler",
                resultat = ResultatKode.INNVILGET,
                gjeldendeAvklaringsBehov = AvklaringsbehovKode("5099"),
                gjeldendeAvklaringsbehovStatus = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                sisteLøsteAvklaringsbehov = AvklaringsbehovKode("5097"),
                sisteSaksbehandlerSomLøstebehov = "Kvalitetssikrer",
                sistLøsteAvklaringsbehovTidspunkt = tidspunkt.minusHours(1),
                gjeldendeStegGruppe = Behandlingssteggruppe.BREV,
                venteÅrsak = "VENTER_PÅ_OPPLYSNINGER",
                returÅrsak = "ANNET",
                returÅrsakkoblinger = listOf(
                    ReturÅrsakTilstand(AvklaringsbehovKode("5003"), listOf("ANNET", "MANGLENDE_UTREDNING")),
                    ReturÅrsakTilstand(AvklaringsbehovKode("5097"), listOf("ANNET")),
                ),
                relatertBehandlingReferanse = "RELATERT",
            ),
        )
    }

    @Test
    fun `løst behov beholder saksbehandler selv om løsningstidspunkt mangler`() {
        val hendelse = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("1.0"),
            mottattTid = tidspunkt.minusDays(1),
            søknadsformat = SøknadsFormat.PAPIR,
            avklaringsbehovStatus = null,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = "Kvalitetssikrer",
            sistLøsteAvklaringsbehovTidspunkt = null,
            relatertBehandlingReferanse = null,
        )

        assertThat(hendelse.tilTilstand()).isEqualTo(
            BehandlingTilstand(
                status = BehandlingStatus.UTREDES,
                versjon = Versjon("1.0"),
                mottattTid = tidspunkt.minusDays(1),
                søknadsformat = SøknadsFormat.PAPIR,
                sisteLøsteAvklaringsbehov = AvklaringsbehovKode("5097"),
                sisteSaksbehandlerSomLøstebehov = "Kvalitetssikrer",
                sistLøsteAvklaringsbehovTidspunkt = null,
            ),
        )
    }

    @Test
    fun `manglende hendelsesverdier fylles ikke ut og uavhengige behovsfelt beholdes`() {
        val hendelse = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("1.0"),
            mottattTid = tidspunkt.minusDays(1),
            søknadsformat = SøknadsFormat.PAPIR,
            ansvarligBeslutter = null,
            vedtakstidspunkt = null,
            resultat = null,
            saksbehandler = null,
            avklaringsBehov = null,
            avklaringsbehovStatus = Status.SENDT_TILBAKE_FRA_BESLUTTER,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = null,
            sistLøsteAvklaringsbehovTidspunkt = null,
            steggruppe = null,
            venteÅrsak = null,
            returÅrsak = null,
            returÅrsakkoblinger = emptyList(),
            relatertBehandlingReferanse = null,
        )

        assertThat(hendelse.tilTilstand()).isEqualTo(
            BehandlingTilstand(
                status = BehandlingStatus.UTREDES,
                versjon = Versjon("1.0"),
                mottattTid = tidspunkt.minusDays(1),
                søknadsformat = SøknadsFormat.PAPIR,
                ansvarligBeslutter = null,
                vedtakstidspunkt = null,
                resultat = null,
                sisteSaksbehandler = null,
                gjeldendeAvklaringsBehov = null,
                gjeldendeAvklaringsbehovStatus = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                sisteLøsteAvklaringsbehov = AvklaringsbehovKode("5097"),
                sisteSaksbehandlerSomLøstebehov = null,
                sistLøsteAvklaringsbehovTidspunkt = null,
                gjeldendeStegGruppe = null,
                venteÅrsak = null,
                returÅrsak = null,
                returÅrsakkoblinger = emptyList(),
                relatertBehandlingReferanse = null,
            ),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `konvertering beholder blanke verdier fra begge representasjoner`(blank: String) {
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = tidspunkt.minusDays(1),
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("1.0"),
            mottattTid = tidspunkt.minusDays(1),
            søknadsformat = SøknadsFormat.PAPIR,
            ansvarligBeslutter = blank,
            sisteSaksbehandler = blank,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = blank,
            venteÅrsak = blank,
            returÅrsak = blank,
            relatertBehandlingReferanse = blank,
        )
        val hendelse = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("1.0"),
            mottattTid = tidspunkt.minusDays(1),
            søknadsformat = SøknadsFormat.PAPIR,
            avklaringsbehovStatus = null,
            ansvarligBeslutter = blank,
            saksbehandler = Saksbehandler(blank),
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING,
            sisteSaksbehandlerSomLøstebehov = blank,
            venteÅrsak = blank,
            returÅrsak = blank,
            relatertBehandlingReferanse = blank,
        )
        val forventet = BehandlingTilstand(
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("1.0"),
            mottattTid = tidspunkt.minusDays(1),
            søknadsformat = SøknadsFormat.PAPIR,
            ansvarligBeslutter = blank,
            sisteSaksbehandler = blank,
            sisteLøsteAvklaringsbehov = AvklaringsbehovKode("5097"),
            sisteSaksbehandlerSomLøstebehov = blank,
            venteÅrsak = blank,
            returÅrsak = blank,
            relatertBehandlingReferanse = blank,
        )

        assertThat(listOf(behandling.nåværendeTilstand(), hendelse.tilTilstand()))
            .containsExactly(forventet, forventet)
    }
}

package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun `ny hendelse erstatter hele tilstanden og beholder tidligere historikk`() {
        val nyTilstand = BehandlingTilstand(
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("ny"),
            mottattTid = tidspunkt.minusDays(1),
            søknadsformat = SøknadsFormat.PAPIR,
            ansvarligBeslutter = null,
            vedtakstidspunkt = null,
            sisteSaksbehandler = "Saksbehandler",
            resultat = ResultatKode.INNVILGET,
            gjeldendeAvklaringsBehov = Definisjon.FATTE_VEDTAK.tilAvklaringsbehovKode(),
            gjeldendeAvklaringsbehovStatus = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
            sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING.tilAvklaringsbehovKode(),
            sisteSaksbehandlerSomLøstebehov = "Kvalitetssikrer",
            sistLøsteAvklaringsbehovTidspunkt = tidspunkt.minusHours(1),
            gjeldendeStegGruppe = Behandlingssteggruppe.BREV,
            venteÅrsak = " ",
            returÅrsak = "ANNET",
            returÅrsakkoblinger = listOf(
                ReturÅrsakTilstand(Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(), listOf("ANNET")),
            ),
            relatertBehandlingReferanse = "RELATERT",
        )
        val første = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt,
            tilstand = BehandlingTilstand(
                status = BehandlingStatus.OPPRETTET,
                versjon = Versjon("første"),
                mottattTid = tidspunkt.minusDays(2),
                søknadsformat = SøknadsFormat.DIGITAL,
                ansvarligBeslutter = "Beslutter",
                vedtakstidspunkt = tidspunkt,
            ),
        )
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = tidspunkt,
            tilstand = første.tilstand,
            hendelser = listOf(første),
        )
        val nyHendelse = BehandlingHendelse(null, tidspunkt.plusMinutes(1), nyTilstand)

        assertThat(behandling.leggTilHendelse(nyHendelse)).isEqualTo(
            Behandling(
                referanse = behandling.referanse,
                sak = sak,
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                opprettetTid = tidspunkt,
                oppdatertTidspunkt = tidspunkt.plusMinutes(1),
                tilstand = nyTilstand,
                hendelser = listOf(første, nyHendelse),
            ),
        )
        assertThat(behandling.hendelser).containsExactly(første)
    }

    @Test
    fun `historikk kan ha løst behov uten saksbehandler men behandlingen krever begge`() {
        val tilstand = BehandlingTilstand(
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("1.0"),
            mottattTid = tidspunkt,
            søknadsformat = SøknadsFormat.DIGITAL,
        )
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = tidspunkt,
            tilstand = tilstand,
        )
        val hendelse = BehandlingHendelse(
            tidspunkt = null,
            hendelsesTidspunkt = tidspunkt,
            tilstand = tilstand.copy(
                sisteLøsteAvklaringsbehov = Definisjon.KVALITETSSIKRING.tilAvklaringsbehovKode(),
                sisteSaksbehandlerSomLøstebehov = null,
            ),
        )

        assertThrows<IllegalStateException> { behandling.leggTilHendelse(hendelse) }
    }
}

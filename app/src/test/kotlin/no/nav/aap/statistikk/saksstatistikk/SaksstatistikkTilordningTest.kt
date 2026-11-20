package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.BehandlingTilstand
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.tilAvklaringsbehovKode
import no.nav.aap.statistikk.behandling.ÅrsakTilSattPåVent
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.oppgave.Reservasjon
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime
import java.util.UUID

class SaksstatistikkTilordningTest {
    private val tidspunkt = LocalDateTime.of(2024, 1, 10, 12, 0)
    private val hendelse = BehandlingHendelse(
        tidspunkt = tidspunkt,
        hendelsesTidspunkt = tidspunkt,
        tilstand = BehandlingTilstand(
            gjeldendeAvklaringsBehov = Definisjon.FATTE_VEDTAK.tilAvklaringsbehovKode(),
            gjeldendeAvklaringsbehovStatus = null,
            versjon = Versjon("1"),
            status = BehandlingStatus.UTREDES,
            mottattTid = tidspunkt,
            søknadsformat = SøknadsFormat.DIGITAL,
            relatertBehandlingReferanse = null,
        ),
    )
    private val grunnbehandling = Behandling(
        referanse = UUID.randomUUID(),
        sak = Sak(
            saksnummer = Saksnummer("SAK123"),
            person = Person(Ident("0")),
            sistOppdatert = tidspunkt,
        ),
        typeBehandling = TypeBehandling.Førstegangsbehandling,
        opprettetTid = tidspunkt,
        hendelser = listOf(hendelse),
        tilstand = BehandlingTilstand(
            status = BehandlingStatus.UTREDES,
            mottattTid = tidspunkt,
            versjon = Versjon("1"),
            søknadsformat = SøknadsFormat.DIGITAL,
            gjeldendeAvklaringsBehov = Definisjon.FATTE_VEDTAK.tilAvklaringsbehovKode(),
            sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
            sisteSaksbehandlerSomLøstebehov = "Løste",
            sisteSaksbehandler = "Siste",
            venteÅrsak = ÅrsakTilSattPåVent.entries.first().name,
        ),
    )
    private val snapshot = SakstatistikkSnapshot(
        tidspunkt = tidspunkt,
        behandlingReferanse = grunnbehandling.referanse,
        status = BehandlingStatus.UTREDES.name,
        avklaringsbehov = Definisjon.FATTE_VEDTAK.kode.name,
        saksbehandler = null,
        enhet = null,
        kilde = Kilde.Behandling,
    )
    private val eldreOppgave = Oppgave(
        identifikator = 1L,
        avklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name,
        enhet = Enhet(kode = "4500"),
        person = null,
        status = Oppgavestatus.OPPRETTET,
        opprettetTidspunkt = tidspunkt.plusMinutes(1),
        reservasjon = Reservasjon(
            reservertAv = Saksbehandler("Tidligere"),
            reservasjonOpprettet = tidspunkt.plusMinutes(1),
        ),
        hendelser = emptyList(),
    )
    private val nyereOppgave = eldreOppgave.copy(
        identifikator = 2L,
        avklaringsbehov = Definisjon.FATTE_VEDTAK.kode.name,
        enhet = Enhet(kode = "9999"),
        opprettetTidspunkt = tidspunkt.plusMinutes(2),
        reservasjon = Reservasjon(
            reservertAv = Saksbehandler("Oppgave"),
            reservasjonOpprettet = tidspunkt.plusMinutes(2),
        ),
    )
    private val oppgaver = listOf(nyereOppgave, eldreOppgave)

    @ParameterizedTest
    @CsvSource(
        "IVERKSETTES,Siste,Løste,",
        "AVSLUTTET,Siste,Løste,Siste",
        "AVSLUTTET,,Løste,Løste",
        "AVSLUTTET,,,Oppgave",
    )
    fun `avsluttende statuser bruker nyeste oppgaveenhet og prioritert saksbehandler selv med cutoff`(
        status: BehandlingStatus,
        sisteSaksbehandler: String?,
        sisteSomLøsteBehov: String?,
        forventetSaksbehandler: String?,
    ) {
        val behandling = grunnbehandling.copy(
            hendelser = listOf(hendelse.copy(tilstand = hendelse.tilstand.copy(status = status))),
            tilstand = grunnbehandling.nåværendeTilstand().copy(
                sisteSaksbehandler = sisteSaksbehandler,
                sisteLøsteAvklaringsbehov = sisteSomLøsteBehov?.let {
                    Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode()
                },
                sisteSaksbehandlerSomLøstebehov = sisteSomLøsteBehov,
            ),
        )

        val resultat = utledSaksstatistikkTilordning(
            behandling,
            listOf(snapshot.copy(status = status.name)),
            oppgaver,
            tidspunkt,
        )

        assertThat(resultat).isEqualTo(SaksstatistikkTilordning("9999", forventetSaksbehandler))
    }

    @Test
    fun `ventende behandling bruker enheten for sist løste behov foran gjeldende behov`() {
        val behandling = grunnbehandling.copy(
            tilstand = grunnbehandling.nåværendeTilstand().copy(
                sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
                venteÅrsak = grunnbehandling.nåværendeTilstand().venteÅrsak,
            ),
        )

        val resultat = utledSaksstatistikkTilordning(behandling, listOf(snapshot), oppgaver, tidspunkt)

        assertThat(resultat).isEqualTo(SaksstatistikkTilordning(ansvarligEnhetKode = "4500", saksbehandler = null))
    }

    @Test
    fun `ventende behandling bruker nyeste oppgaveenhet når sist løste behov ikke har en oppgave`() {
        val behandling = grunnbehandling.copy(
            tilstand = grunnbehandling.nåværendeTilstand().copy(
                sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_HELSEINSTITUSJON.tilAvklaringsbehovKode(),
                venteÅrsak = grunnbehandling.nåværendeTilstand().venteÅrsak,
            ),
        )

        val resultat = utledSaksstatistikkTilordning(behandling, listOf(snapshot), oppgaver, tidspunkt)

        assertThat(resultat).isEqualTo(SaksstatistikkTilordning(ansvarligEnhetKode = "9999", saksbehandler = null))
    }

    @Test
    fun `behandling som ikke venter bruker enheten for gjeldende behov foran sist løste behov`() {
        val behandling = grunnbehandling.copy(
            tilstand = grunnbehandling.nåværendeTilstand().copy(
                sisteLøsteAvklaringsbehov = Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
                venteÅrsak = null,
            ),
        )

        val resultat = utledSaksstatistikkTilordning(behandling, listOf(snapshot), oppgaver, tidspunkt)

        assertThat(resultat).isEqualTo(SaksstatistikkTilordning(ansvarligEnhetKode = "9999", saksbehandler = null))
    }

    @Test
    fun `manuell behandling bruker snapshotenhet foran nyeste oppgaveenhet`() {
        val behov = Definisjon.AVKLAR_SYKDOM
        val behandling = grunnbehandling.copy(
            hendelser = listOf(
                hendelse.copy(
                    tilstand = hendelse.tilstand.copy(
                        status = BehandlingStatus.AVSLUTTET,
                        gjeldendeAvklaringsBehov = behov.tilAvklaringsbehovKode(),
                    ),
                ),
            ),
            tilstand = grunnbehandling.nåværendeTilstand().copy(
                gjeldendeAvklaringsBehov = behov.tilAvklaringsbehovKode(),
            ),
        )
        val valgtSnapshot = snapshot.copy(
            status = BehandlingStatus.AVSLUTTET.name,
            avklaringsbehov = behov.kode.name,
            enhet = "4500",
            saksbehandler = "Oppgave",
        )

        val resultat = utledSaksstatistikkTilordning(behandling, listOf(valgtSnapshot), oppgaver, tidspunkt)

        assertThat(resultat).isEqualTo(SaksstatistikkTilordning(ansvarligEnhetKode = "4500", saksbehandler = "Oppgave"))
    }

    @Test
    fun `automatisk behandling bruker automatisk enhet selv om snapshot har enhet`() {
        val behov = Definisjon.BESTILL_BREV
        val behandling = grunnbehandling.copy(
            hendelser = listOf(
                hendelse.copy(
                    tilstand = hendelse.tilstand.copy(
                        status = BehandlingStatus.AVSLUTTET,
                        gjeldendeAvklaringsBehov = behov.tilAvklaringsbehovKode(),
                    ),
                ),
            ),
            tilstand = grunnbehandling.nåværendeTilstand().copy(
                gjeldendeAvklaringsBehov = behov.tilAvklaringsbehovKode(),
            ),
        )
        val valgtSnapshot = snapshot.copy(
            status = BehandlingStatus.AVSLUTTET.name,
            avklaringsbehov = behov.kode.name,
            enhet = "4500",
            saksbehandler = "Oppgave",
        )

        val resultat = utledSaksstatistikkTilordning(behandling, listOf(valgtSnapshot), oppgaver, tidspunkt)

        assertThat(resultat).isEqualTo(
            SaksstatistikkTilordning(ansvarligEnhetKode = "KELVIN_AUTOMATISK", saksbehandler = "Oppgave"),
        )
    }
}

package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class BehandlingTest {

    @Test
    fun `prioriter årsaker til behandling`() {
        val flereÅrsaker = listOf(Vurderingsbehov.SØKNAD, Vurderingsbehov.MELDEKORT)

        assertThat(flereÅrsaker.prioriterÅrsaker()).isEqualTo(Vurderingsbehov.SØKNAD)
    }

    @Test
    fun `lag behandling-historikk fra behandling-objekt`() {
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = Sak(
                saksnummer = Saksnummer("123456789"),
                person = Person(ident = "12121"),
                sakStatus = SakStatus.OPPRETTET,
                sistOppdatert = LocalDateTime.now()
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = LocalDateTime.now(),
            oppdatertTidspunkt = LocalDateTime.now(),
            mottattTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.PAPIR,
            hendelser = listOf(
                behandlingHendelse(Definisjon.AVKLAR_SYKDOM),
                behandlingHendelse(Definisjon.AVKLAR_HELSEINSTITUSJON)
            )
        )

        val res = behandling.hendelsesHistorikk()

        assertThat(res.size).isEqualTo(2)
        assertThat(res.first().hendelser.size).isEqualTo(1)
        assertThat(res[1].hendelser.size).isEqualTo(2)
        assertThat(res[0].gjeldendeAvklaringsBehov?.definisjon).isEqualTo(Definisjon.AVKLAR_SYKDOM)
        assertThat(res[1].gjeldendeAvklaringsBehov?.definisjon).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON)
        assertThat(res[0].hendelser.first().avklaringsBehov?.definisjon).isEqualTo(Definisjon.AVKLAR_SYKDOM)
        assertThat(res[1].hendelser.first().avklaringsBehov?.definisjon).isEqualTo(Definisjon.AVKLAR_SYKDOM)
        assertThat(res[1].hendelser[1].avklaringsBehov?.definisjon).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON)
    }

    private fun behandlingHendelse(avklaringsbehov: Definisjon): BehandlingHendelse {
        return BehandlingHendelse(
            tidspunkt = LocalDateTime.now(),
            hendelsesTidspunkt = LocalDateTime.now(),
            avklaringsBehov = Avklaringsbehov(avklaringsbehov, null),
            avklaringsbehovStatus = Status.OPPRETTET,
            saksbehandler = Saksbehandler(ident = "Albert"),
            versjon = Versjon("323"),
            status = BehandlingStatus.OPPRETTET,
            ansvarligBeslutter = "Åberg",
            vedtakstidspunkt = LocalDateTime.now(),
            mottattTid = LocalDateTime.now(),
            søknadsformat = SøknadsFormat.DIGITAL
        )
    }
}
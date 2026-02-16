package no.nav.aap.statistikk.behandling

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
import java.util.UUID

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
                behandlingHendelse("SYKDOM"),
                behandlingHendelse("INSTITUSJON")
            )
        )

        val res = behandling.hendelsesHistorikk()

        assertThat(res.size).isEqualTo(2)
        assertThat(res.first().hendelser.size).isEqualTo(1)
        assertThat(res[1].hendelser.size).isEqualTo(2)
        assertThat(res[0].gjeldendeAvklaringsBehov).isEqualTo("SYKDOM")
        assertThat(res[1].gjeldendeAvklaringsBehov).isEqualTo("INSTITUSJON")
        assertThat(res[0].hendelser.first().avklaringsBehov).isEqualTo("SYKDOM")
        assertThat(res[1].hendelser.first().avklaringsBehov).isEqualTo("SYKDOM")
        assertThat(res[1].hendelser[1].avklaringsBehov).isEqualTo("INSTITUSJON")
    }

    @Test
    fun `påTidspunkt filtrerer hendelser til og med gitt tidspunkt`() {
        val t1 = LocalDateTime.of(2024, 1, 1, 10, 0)
        val t2 = LocalDateTime.of(2024, 1, 1, 11, 0)
        val t3 = LocalDateTime.of(2024, 1, 1, 12, 0)

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
            opprettetTid = t1,
            oppdatertTidspunkt = t1,
            mottattTid = t1,
            versjon = Versjon("v1"),
            søknadsformat = SøknadsFormat.DIGITAL,
            hendelser = listOf(
                behandlingHendelse("SYKDOM", t1, Versjon("v1"), BehandlingStatus.OPPRETTET),
                behandlingHendelse("INSTITUSJON", t2, Versjon("v2"), BehandlingStatus.UTREDES),
                behandlingHendelse("FATTE_VEDTAK", t3, Versjon("v3"), BehandlingStatus.IVERKSETTES),
            )
        )

        val snapshot = behandling.påTidspunkt(t2)

        assertThat(snapshot.hendelser).hasSize(2)
        assertThat(snapshot.oppdatertTidspunkt()).isEqualTo(t2)
        assertThat(snapshot.versjon()).isEqualTo("v2")
        assertThat(snapshot.behandlingStatus()).isEqualTo(BehandlingStatus.UTREDES)
        assertThat(snapshot.gjeldendeAvklaringsBehov).isEqualTo("INSTITUSJON")
    }

    @Test
    fun `påTidspunkt med tidspunkt etter siste hendelse returnerer alt`() {
        val t1 = LocalDateTime.of(2024, 1, 1, 10, 0)
        val t2 = LocalDateTime.of(2024, 1, 1, 11, 0)

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
            opprettetTid = t1,
            oppdatertTidspunkt = t1,
            mottattTid = t1,
            versjon = Versjon("v1"),
            søknadsformat = SøknadsFormat.DIGITAL,
            hendelser = listOf(
                behandlingHendelse("SYKDOM", t1, Versjon("v1"), BehandlingStatus.OPPRETTET),
                behandlingHendelse("INSTITUSJON", t2, Versjon("v2"), BehandlingStatus.UTREDES),
            )
        )

        val snapshot = behandling.påTidspunkt(t2.plusHours(1))

        assertThat(snapshot.hendelser).hasSize(2)
        assertThat(snapshot.oppdatertTidspunkt()).isEqualTo(t2)
    }

    private fun behandlingHendelse(
        avklaringsbehov: String,
        hendelsesTidspunkt: LocalDateTime,
        versjon: Versjon,
        status: BehandlingStatus
    ): BehandlingHendelse {
        return BehandlingHendelse(
            tidspunkt = hendelsesTidspunkt,
            hendelsesTidspunkt = hendelsesTidspunkt,
            avklaringsBehov = avklaringsbehov,
            avklaringsbehovStatus = Status.OPPRETTET,
            saksbehandler = Saksbehandler(ident = "Albert"),
            versjon = versjon,
            status = status,
            ansvarligBeslutter = "Åberg",
            vedtakstidspunkt = null,
            mottattTid = hendelsesTidspunkt,
            søknadsformat = SøknadsFormat.DIGITAL,
            relatertBehandlingReferanse = null
        )
    }

    private fun behandlingHendelse(avklaringsbehov: String): BehandlingHendelse {
        return BehandlingHendelse(
            tidspunkt = LocalDateTime.now(),
            hendelsesTidspunkt = LocalDateTime.now(),
            avklaringsBehov = avklaringsbehov,
            avklaringsbehovStatus = Status.OPPRETTET,
            saksbehandler = Saksbehandler(ident = "Albert"),
            versjon = Versjon("323"),
            status = BehandlingStatus.OPPRETTET,
            ansvarligBeslutter = "Åberg",
            vedtakstidspunkt = LocalDateTime.now(),
            mottattTid = LocalDateTime.now(),
            søknadsformat = SøknadsFormat.DIGITAL,
            relatertBehandlingReferanse = null
        )
    }
}
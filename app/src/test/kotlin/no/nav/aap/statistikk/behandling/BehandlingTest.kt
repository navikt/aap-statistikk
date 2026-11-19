package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode
import no.nav.aap.utbetaling.helved.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class BehandlingTest {

    @ParameterizedTest
    @MethodSource("historikkOgBehandlingsmetode")
    fun `behandlingsmetode følger siste behov og tidligere manuell behandling`(
        behov: List<Definisjon?>,
        forventet: BehandlingMetode,
    ) {
        val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
        val hendelse = behandlingHendelse(
            Definisjon.AVKLAR_SYKDOM,
            tidspunkt,
            Versjon("1"),
            BehandlingStatus.UTREDES,
        )
        val behandling = behandlingMedHendelser().copy(
            gjeldendeAvklaringsBehov = Definisjon.FATTE_VEDTAK,
            hendelser = behov.mapIndexed { indeks, definisjon ->
                hendelse.copy(
                    avklaringsBehov = definisjon,
                    hendelsesTidspunkt = tidspunkt.minusMinutes(indeks.toLong()),
                )
            },
        )

        assertThat(behandling.behandlingMetode()).isEqualTo(forventet)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `manglende historikkresultat stopper ikke utbetalingsid`(harTidligereAvslag: Boolean) {
        val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
        val avslått = behandlingHendelse(
            Definisjon.FATTE_VEDTAK,
            tidspunkt,
            Versjon("1"),
            BehandlingStatus.AVSLUTTET,
        ).copy(resultat = ResultatKode.AVSLAG)
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = Sak(
                saksnummer = Saksnummer("SAK123"),
                person = Person(ident = Ident("0")),
                sakStatus = SakStatus.OPPRETTET,
                sistOppdatert = tidspunkt,
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.AVSLUTTET,
            opprettetTid = tidspunkt,
            mottattTid = tidspunkt,
            versjon = Versjon("1"),
            søknadsformat = SøknadsFormat.DIGITAL,
            resultat = ResultatKode.AVSLAG,
            hendelser = if (harTidligereAvslag) {
                listOf(avslått, avslått.copy(hendelsesTidspunkt = tidspunkt.plusMinutes(1), resultat = null))
            } else {
                emptyList()
            },
        )

        assertThat(behandling.utbetalingId()).isEqualTo(behandling.referanse.toBase64())
    }

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
                person = Person(ident = Ident("12121")),
                sakStatus = SakStatus.OPPRETTET,
                sistOppdatert = LocalDateTime.now(),
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
                behandlingHendelse(Definisjon.AVKLAR_HELSEINSTITUSJON),
            ),
        )

        val res = behandling.hendelsesHistorikk()

        assertThat(res.size).isEqualTo(2)
        assertThat(res.first().hendelser.size).isEqualTo(1)
        assertThat(res[1].hendelser.size).isEqualTo(2)
        assertThat(res[0].gjeldendeAvklaringsBehov).isEqualTo(Definisjon.AVKLAR_SYKDOM)
        assertThat(res[1].gjeldendeAvklaringsBehov).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON)
        assertThat(res[0].hendelser.first().avklaringsBehov).isEqualTo(Definisjon.AVKLAR_SYKDOM)
        assertThat(res[1].hendelser.first().avklaringsBehov).isEqualTo(Definisjon.AVKLAR_SYKDOM)
        assertThat(res[1].hendelser[1].avklaringsBehov).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON)
    }

    @Test
    fun `tom hendelsesliste gir ingen historiske tilstander`() {
        assertThat(behandlingMedHendelser().hendelsesHistorikk()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `historikken beholder oppgitt rekkefølge også ved like eller synkende tidspunkter`(minutter: Long) {
        val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
        val første = behandlingHendelse(
            Definisjon.AVKLAR_SYKDOM,
            tidspunkt,
            Versjon("1"),
            BehandlingStatus.OPPRETTET,
        )
        val andre = første.copy(hendelsesTidspunkt = tidspunkt.plusMinutes(minutter), versjon = Versjon("2"))
        val behandling = behandlingMedHendelser(første, andre)

        val historikk = behandling.hendelsesHistorikk()

        assertThat(historikk.map { it.hendelser }).containsExactly(listOf(første), listOf(første, andre))
        assertThat(historikk.map { it.versjon }).containsExactly(Versjon("1"), Versjon("2"))
        assertThat(behandling.hendelser).containsExactly(første, andre)
    }

    @Test
    fun `nullverdier erstatter tidligere tilstand uten å endre tidligere historikk`() {
        val første = behandlingHendelse(
            Definisjon.AVKLAR_SYKDOM,
            LocalDateTime.of(2026, 11, 19, 10, 0),
            Versjon("1"),
            BehandlingStatus.UTREDES,
        ).copy(resultat = ResultatKode.INNVILGET)
        val andre = første.copy(
            hendelsesTidspunkt = første.hendelsesTidspunkt.plusMinutes(1),
            avklaringsBehov = null,
            avklaringsbehovStatus = null,
            saksbehandler = null,
            ansvarligBeslutter = null,
            resultat = null,
        )

        val behandling = behandlingMedHendelser(første, andre)
        val forventetFørste = behandling.copy(
            hendelser = listOf(første),
            status = BehandlingStatus.UTREDES,
            oppdatertTidspunkt = første.hendelsesTidspunkt,
            gjeldendeAvklaringsBehov = Definisjon.AVKLAR_SYKDOM,
            gjeldendeAvklaringsbehovStatus = Status.OPPRETTET,
            sisteSaksbehandler = "Albert",
            ansvarligBeslutter = "Åberg",
            resultat = ResultatKode.INNVILGET,
        )
        val forventetAndre = forventetFørste.copy(
            hendelser = listOf(første, andre),
            oppdatertTidspunkt = andre.hendelsesTidspunkt,
            gjeldendeAvklaringsBehov = null,
            gjeldendeAvklaringsbehovStatus = null,
            sisteSaksbehandler = null,
            ansvarligBeslutter = null,
            resultat = null,
        )

        val historikk = behandling.hendelsesHistorikk()

        assertThat(historikk).usingRecursiveComparison().isEqualTo(listOf(forventetFørste, forventetAndre))
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
                person = Person(ident = Ident("12121")),
                sakStatus = SakStatus.OPPRETTET,
                sistOppdatert = LocalDateTime.now(),
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = t1,
            oppdatertTidspunkt = t1,
            mottattTid = t1,
            versjon = Versjon("v1"),
            søknadsformat = SøknadsFormat.DIGITAL,
            hendelser = listOf(
                behandlingHendelse(
                    Definisjon.AVKLAR_SYKDOM,
                    t1,
                    Versjon("v1"),
                    BehandlingStatus.OPPRETTET,
                ),
                behandlingHendelse(
                    Definisjon.AVKLAR_HELSEINSTITUSJON,
                    t2,
                    Versjon("v2"),
                    BehandlingStatus.UTREDES,
                ),
                behandlingHendelse(
                    Definisjon.AVKLAR_OVERGANG_ARBEID,
                    t3,
                    Versjon("v3"),
                    BehandlingStatus.IVERKSETTES,
                ),
            ),
        )

        val snapshot = behandling.påTidspunkt(t2)

        assertThat(snapshot.hendelser).hasSize(2)
        assertThat(snapshot.oppdatertTidspunkt()).isEqualTo(t2)
        assertThat(snapshot.versjon()).isEqualTo("v2")
        assertThat(snapshot.behandlingStatus()).isEqualTo(BehandlingStatus.UTREDES)
        assertThat(snapshot.gjeldendeAvklaringsBehov).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON)
    }

    @Test
    fun `påTidspunkt med tidspunkt etter siste hendelse returnerer alt`() {
        val t1 = LocalDateTime.of(2024, 1, 1, 10, 0)
        val t2 = LocalDateTime.of(2024, 1, 1, 11, 0)

        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = Sak(
                saksnummer = Saksnummer("123456789"),
                person = Person(ident = Ident("12121")),
                sakStatus = SakStatus.OPPRETTET,
                sistOppdatert = LocalDateTime.now(),
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = t1,
            oppdatertTidspunkt = t1,
            mottattTid = t1,
            versjon = Versjon("v1"),
            søknadsformat = SøknadsFormat.DIGITAL,
            hendelser = listOf(
                behandlingHendelse(
                    Definisjon.AVKLAR_SYKDOM,
                    t1,
                    Versjon("v1"),
                    BehandlingStatus.OPPRETTET,
                ),
                behandlingHendelse(
                    Definisjon.AVKLAR_HELSEINSTITUSJON,
                    t2,
                    Versjon("v2"),
                    BehandlingStatus.UTREDES,
                ),
            ),
        )

        val snapshot = behandling.påTidspunkt(t2.plusHours(1))

        assertThat(snapshot.hendelser).hasSize(2)
        assertThat(snapshot.oppdatertTidspunkt()).isEqualTo(t2)
    }

    private fun behandlingMedHendelser(vararg hendelser: BehandlingHendelse): Behandling {
        val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
        return Behandling(
            referanse = UUID.randomUUID(),
            sak = Sak(
                saksnummer = Saksnummer("SAK123"),
                person = Person(ident = Ident("0")),
                sakStatus = SakStatus.OPPRETTET,
                sistOppdatert = tidspunkt,
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = tidspunkt,
            mottattTid = tidspunkt,
            versjon = Versjon("1"),
            søknadsformat = SøknadsFormat.DIGITAL,
            hendelser = hendelser.toList(),
        )
    }

    private fun behandlingHendelse(
        avklaringsbehov: Definisjon,
        hendelsesTidspunkt: LocalDateTime,
        versjon: Versjon,
        status: BehandlingStatus,
    ): BehandlingHendelse = BehandlingHendelse(
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
        relatertBehandlingReferanse = null,
    )

    private fun behandlingHendelse(avklaringsbehov: Definisjon): BehandlingHendelse = BehandlingHendelse(
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
        relatertBehandlingReferanse = null,
    )

    companion object {
        @JvmStatic
        fun historikkOgBehandlingsmetode(): List<Arguments> = listOf(
            Arguments.of(emptyList<Definisjon>(), BehandlingMetode.AUTOMATISK),
            Arguments.of(listOf(null, null), BehandlingMetode.AUTOMATISK),
            Arguments.of(listOf(Definisjon.BESTILL_BREV, null), BehandlingMetode.AUTOMATISK),
            Arguments.of(
                listOf(Definisjon.AVKLAR_SYKDOM, Definisjon.BESTILL_BREV, null),
                BehandlingMetode.MANUELL,
            ),
            Arguments.of(
                listOf(Definisjon.BESTILL_BREV, Definisjon.AVKLAR_SYKDOM, null),
                BehandlingMetode.MANUELL,
            ),
            Arguments.of(
                listOf(Definisjon.FATTE_VEDTAK, Definisjon.KVALITETSSIKRING, null, null),
                BehandlingMetode.KVALITETSSIKRING,
            ),
            Arguments.of(
                listOf(Definisjon.KVALITETSSIKRING, Definisjon.FATTE_VEDTAK, null),
                BehandlingMetode.FATTE_VEDTAK,
            ),
            Arguments.of(
                listOf(Definisjon.KVALITETSSIKRING, Definisjon.BESTILL_BREV, null),
                BehandlingMetode.MANUELL,
            ),
        )
    }
}

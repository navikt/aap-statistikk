package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode
import no.nav.aap.utbetaling.helved.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class BehandlingTest {

    private val tilstandstid = LocalDateTime.of(2026, 11, 19, 10, 0)

    @Test
    fun `siste hendelses vedtaksdata brukes selv om en annen hendelse har nyere tidspunkt`() {
        val grunnlag = behandlingMedMotstridendeTilstand()
        val siste = grunnlag.hendelser.last().copy(
            tilstand = grunnlag.hendelser.last().tilstand.copy(
                ansvarligBeslutter = "Siste beslutter",
                vedtakstidspunkt = tilstandstid.plusMinutes(1),
                resultat = ResultatKode.AVSLAG,
            ),
        )
        val behandling = grunnlag.copy(hendelser = listOf(grunnlag.hendelser.first(), siste))

        assertThat(lesTilstand(behandling)).isEqualTo(
            Tilstandslesing(
                status = BehandlingStatus.UTREDES,
                versjon = "siste",
                beslutter = "Siste beslutter",
                vedtakstidspunkt = tilstandstid.plusMinutes(1),
                resultat = ResultatKode.AVSLAG,
                oppdatertTidspunkt = tilstandstid.plusMinutes(2),
            ),
        )
    }

    @Test
    fun `null i siste hendelse bruker behandlingens vedtaksdata men ikke tidligere resultat`() {
        val grunnlag = behandlingMedMotstridendeTilstand()
        val siste = grunnlag.hendelser.last().copy(
            tilstand = grunnlag.hendelser.last().tilstand.copy(
                ansvarligBeslutter = null,
                vedtakstidspunkt = null,
                resultat = null,
            ),
        )
        val behandling = grunnlag.copy(hendelser = listOf(grunnlag.hendelser.first(), siste))

        assertThat(lesTilstand(behandling)).isEqualTo(
            Tilstandslesing(
                status = BehandlingStatus.UTREDES,
                versjon = "siste",
                beslutter = "Ytre beslutter",
                vedtakstidspunkt = tilstandstid,
                resultat = null,
                oppdatertTidspunkt = tilstandstid.plusMinutes(2),
            ),
        )
    }

    @Test
    fun `å legge til en hendelse fjerner ytre vedtaksverdier som ellers ville blitt brukt`() {
        val grunnlag = behandlingMedMotstridendeTilstand()
        val behandling = grunnlag.copy(hendelser = listOf(grunnlag.hendelser.first()))
            .leggTilHendelse(grunnlag.hendelser.last())

        assertThat(lesTilstand(behandling)).isEqualTo(
            Tilstandslesing(
                status = BehandlingStatus.UTREDES,
                versjon = "siste",
                beslutter = null,
                vedtakstidspunkt = null,
                resultat = null,
                oppdatertTidspunkt = tilstandstid.plusMinutes(2),
            ),
        )
    }

    @Test
    fun `cutoff før første hendelse beholder behandlingens tilstand uten historikkresultat`() {
        val behandling = behandlingMedMotstridendeTilstand()

        val snapshot = behandling.påTidspunkt(tilstandstid.minusMinutes(1))

        assertThat(snapshot).isEqualTo(behandling.copy(hendelser = emptyList()))
        assertThat(lesTilstand(snapshot)).isEqualTo(
            Tilstandslesing(
                status = BehandlingStatus.IVERKSETTES,
                versjon = "ytre",
                beslutter = "Ytre beslutter",
                vedtakstidspunkt = tilstandstid,
                resultat = null,
                oppdatertTidspunkt = tilstandstid.plusMinutes(3),
            ),
        )
    }

    @Test
    fun `cutoff for behandling uten historikk beholder tilstanden uten historikkresultat`() {
        val grunnlag = behandlingMedMotstridendeTilstand()
        val behandling = grunnlag.copy(hendelser = emptyList())

        val snapshot = behandling.påTidspunkt(tilstandstid.minusMinutes(1))

        assertThat(snapshot).isEqualTo(grunnlag.copy(hendelser = emptyList()))
        assertThat(lesTilstand(snapshot)).isEqualTo(
            Tilstandslesing(
                status = BehandlingStatus.IVERKSETTES,
                versjon = "ytre",
                beslutter = "Ytre beslutter",
                vedtakstidspunkt = tilstandstid,
                resultat = null,
                oppdatertTidspunkt = tilstandstid.plusMinutes(3),
            ),
        )
    }

    @Test
    fun `behandling under utredning får ikke utbetalingsid selv om siste hendelse er avsluttet`() {
        val grunnlag = behandlingMedMotstridendeTilstand()
        val behandling = grunnlag.copy(
            hendelser = listOf(
                grunnlag.hendelser.last().copy(
                    tilstand = grunnlag.hendelser.last().tilstand.copy(status = BehandlingStatus.AVSLUTTET),
                ),
            ),
            tilstand = grunnlag.nåværendeTilstand().copy(status = BehandlingStatus.UTREDES),
        )

        assertThat(behandling.utbetalingId()).isNull()
    }

    @Test
    fun `avsluttet behandling får utbetalingsid selv om siste hendelse er under utredning`() {
        val grunnlag = behandlingMedMotstridendeTilstand()
        val behandling = grunnlag.copy(
            hendelser = listOf(
                grunnlag.hendelser.last().copy(
                    tilstand = grunnlag.hendelser.last().tilstand.copy(status = BehandlingStatus.UTREDES),
                ),
            ),
            tilstand = grunnlag.nåværendeTilstand().copy(status = BehandlingStatus.AVSLUTTET),
        )

        assertThat(behandling.utbetalingId()).isEqualTo(behandling.referanse.toBase64())
    }

    @Test
    fun `manglende historikk og ytre tidspunkt gir feil fremfor å bruke opprettet tid`() {
        assertThatIllegalArgumentException()
            .isThrownBy { behandlingMedHendelser().oppdatertTidspunkt() }
            .withMessageContaining("oppdatertTidspunkt er ikke satt")
    }

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
        val grunnlagBehandling = behandlingMedHendelser()
        val behandling = grunnlagBehandling.copy(
            hendelser = behov.mapIndexed { indeks, definisjon ->
                hendelse.copy(
                    hendelsesTidspunkt = tidspunkt.minusMinutes(indeks.toLong()),
                    tilstand = hendelse.tilstand.copy(gjeldendeAvklaringsBehov = definisjon?.tilAvklaringsbehovKode()),
                )
            },
            tilstand = grunnlagBehandling.nåværendeTilstand().copy(
                gjeldendeAvklaringsBehov = Definisjon.FATTE_VEDTAK.tilAvklaringsbehovKode(),
            ),
        )

        assertThat(behandling.behandlingMetode()).isEqualTo(forventet)
    }

    @Test
    fun `avsluttet behandling uten historikk får utbetalingsid selv om tilstanden har avslag`() {
        val grunnlag = behandlingMedHendelser()
        val behandling = grunnlag.copy(
            tilstand = grunnlag.nåværendeTilstand().copy(
                status = BehandlingStatus.AVSLUTTET,
                resultat = ResultatKode.AVSLAG,
            ),
        )

        assertThat(behandling.utbetalingId()).isEqualTo(behandling.referanse.toBase64())
    }

    @Test
    fun `nullresultat i siste hendelse gir utbetalingsid selv om forrige hendelse har avslag`() {
        val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
        val grunnlagAvslått = behandlingHendelse(
            Definisjon.FATTE_VEDTAK,
            tidspunkt,
            Versjon("1"),
            BehandlingStatus.AVSLUTTET,
        )
        val avslått = grunnlagAvslått.copy(tilstand = grunnlagAvslått.tilstand.copy(resultat = ResultatKode.AVSLAG))
        val grunnlag = behandlingMedHendelser(
            avslått,
            avslått.copy(
                hendelsesTidspunkt = tidspunkt.plusMinutes(1),
                tilstand = avslått.tilstand.copy(resultat = null),
            ),
        )
        val behandling = grunnlag.copy(
            tilstand = grunnlag.nåværendeTilstand().copy(
                status = BehandlingStatus.AVSLUTTET,
                resultat = ResultatKode.AVSLAG,
            ),
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
                sistOppdatert = LocalDateTime.now(),
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = LocalDateTime.now(),
            oppdatertTidspunkt = LocalDateTime.now(),
            hendelser = listOf(
                behandlingHendelse(Definisjon.AVKLAR_SYKDOM),
                behandlingHendelse(Definisjon.AVKLAR_HELSEINSTITUSJON),
            ),
            tilstand = BehandlingTilstand(
                status = BehandlingStatus.OPPRETTET,
                mottattTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                versjon = Versjon(UUID.randomUUID().toString()),
                søknadsformat = SøknadsFormat.PAPIR,
            ),
        )

        val res = behandling.hendelsesHistorikk()

        assertThat(res.size).isEqualTo(2)
        assertThat(res.first().hendelser.size).isEqualTo(1)
        assertThat(res[1].hendelser.size).isEqualTo(2)
        assertThat(
            res[0].nåværendeTilstand().gjeldendeAvklaringsBehov,
        ).isEqualTo(Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode())
        assertThat(
            res[1].nåværendeTilstand().gjeldendeAvklaringsBehov,
        ).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON.tilAvklaringsbehovKode())
        assertThat(
            res[0].hendelser.first().tilstand.gjeldendeAvklaringsBehov,
        ).isEqualTo(Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode())
        assertThat(
            res[1].hendelser.first().tilstand.gjeldendeAvklaringsBehov,
        ).isEqualTo(Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode())
        assertThat(
            res[1].hendelser[1].tilstand.gjeldendeAvklaringsBehov,
        ).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON.tilAvklaringsbehovKode())
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
        val andre = første.copy(
            hendelsesTidspunkt = tidspunkt.plusMinutes(minutter),
            tilstand = første.tilstand.copy(versjon = Versjon("2")),
        )
        val behandling = behandlingMedHendelser(første, andre)

        val historikk = behandling.hendelsesHistorikk()

        assertThat(historikk.map { it.hendelser }).containsExactly(listOf(første), listOf(første, andre))
        assertThat(historikk.map { it.nåværendeTilstand().versjon }).containsExactly(Versjon("1"), Versjon("2"))
        assertThat(behandling.hendelser).containsExactly(første, andre)
    }

    @Test
    fun `nullverdier erstatter tidligere tilstand uten å endre tidligere historikk`() {
        val grunnlagFørste = behandlingHendelse(
            Definisjon.AVKLAR_SYKDOM,
            LocalDateTime.of(2026, 11, 19, 10, 0),
            Versjon("1"),
            BehandlingStatus.UTREDES,
        )
        val første = grunnlagFørste.copy(tilstand = grunnlagFørste.tilstand.copy(resultat = ResultatKode.INNVILGET))
        val andre = første.copy(
            hendelsesTidspunkt = første.hendelsesTidspunkt.plusMinutes(1),
            tilstand = første.tilstand.copy(
                gjeldendeAvklaringsBehov = null,
                gjeldendeAvklaringsbehovStatus = null,
                sisteSaksbehandler = null,
                ansvarligBeslutter = null,
                resultat = null,
            ),
        )

        val behandling = behandlingMedHendelser(første, andre)
        val forventetFørste = behandling.copy(
            hendelser = listOf(første),
            oppdatertTidspunkt = første.hendelsesTidspunkt,
            tilstand = behandling.nåværendeTilstand().copy(
                status = BehandlingStatus.UTREDES,
                gjeldendeAvklaringsBehov = Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
                gjeldendeAvklaringsbehovStatus = Status.OPPRETTET.tilDomene(),
                sisteSaksbehandler = "Albert",
                ansvarligBeslutter = "Åberg",
                resultat = ResultatKode.INNVILGET,
            ),
        )
        val forventetAndre = forventetFørste.copy(
            hendelser = listOf(første, andre),
            oppdatertTidspunkt = andre.hendelsesTidspunkt,
            tilstand = forventetFørste.nåværendeTilstand().copy(
                gjeldendeAvklaringsBehov = null,
                gjeldendeAvklaringsbehovStatus = null,
                sisteSaksbehandler = null,
                ansvarligBeslutter = null,
                resultat = null,
            ),
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
                sistOppdatert = LocalDateTime.now(),
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = t1,
            oppdatertTidspunkt = t1,
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
            tilstand = BehandlingTilstand(
                status = BehandlingStatus.OPPRETTET,
                mottattTid = t1,
                versjon = Versjon("v1"),
                søknadsformat = SøknadsFormat.DIGITAL,
            ),
        )

        val snapshot = behandling.påTidspunkt(t2)

        assertThat(snapshot.hendelser).hasSize(2)
        assertThat(snapshot.oppdatertTidspunkt()).isEqualTo(t2)
        assertThat(snapshot.versjon()).isEqualTo("v2")
        assertThat(snapshot.behandlingStatus()).isEqualTo(BehandlingStatus.UTREDES)
        assertThat(
            snapshot.nåværendeTilstand().gjeldendeAvklaringsBehov,
        ).isEqualTo(Definisjon.AVKLAR_HELSEINSTITUSJON.tilAvklaringsbehovKode())
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
                sistOppdatert = LocalDateTime.now(),
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = t1,
            oppdatertTidspunkt = t1,
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
            tilstand = BehandlingTilstand(
                status = BehandlingStatus.OPPRETTET,
                mottattTid = t1,
                versjon = Versjon("v1"),
                søknadsformat = SøknadsFormat.DIGITAL,
            ),
        )

        val snapshot = behandling.påTidspunkt(t2.plusHours(1))

        assertThat(snapshot.hendelser).hasSize(2)
        assertThat(snapshot.oppdatertTidspunkt()).isEqualTo(t2)
    }

    private fun behandlingMedMotstridendeTilstand(): Behandling {
        val grunnlagFørste = behandlingHendelse(
            Definisjon.AVKLAR_SYKDOM,
            tilstandstid.plusMinutes(2),
            Versjon("første"),
            BehandlingStatus.AVSLUTTET,
        )
        val første = grunnlagFørste.copy(
            tilstand = grunnlagFørste.tilstand.copy(
                ansvarligBeslutter = "Tidligere beslutter",
                vedtakstidspunkt = tilstandstid.minusMinutes(1),
                resultat = ResultatKode.INNVILGET,
            ),
        )
        val siste = første.copy(
            hendelsesTidspunkt = tilstandstid.plusMinutes(1),
            tilstand = første.tilstand.copy(
                versjon = Versjon("siste"),
                status = BehandlingStatus.UTREDES,
                ansvarligBeslutter = null,
                vedtakstidspunkt = null,
                resultat = null,
            ),
        )
        val grunnlag = behandlingMedHendelser(første, siste)
        return grunnlag.copy(
            oppdatertTidspunkt = tilstandstid.plusMinutes(3),
            tilstand = grunnlag.nåværendeTilstand().copy(
                status = BehandlingStatus.IVERKSETTES,
                versjon = Versjon("ytre"),
                ansvarligBeslutter = "Ytre beslutter",
                vedtakstidspunkt = tilstandstid,
                resultat = ResultatKode.AVSLAG,
            ),
        )
    }

    private fun lesTilstand(behandling: Behandling): Tilstandslesing = Tilstandslesing(
        status = behandling.behandlingStatus(),
        versjon = behandling.versjon(),
        beslutter = behandling.ansvarligBeslutter(),
        vedtakstidspunkt = behandling.vedtakstidspunkt(),
        resultat = behandling.resultatFraSisteHendelse(),
        oppdatertTidspunkt = behandling.oppdatertTidspunkt(),
    )

    private data class Tilstandslesing(
        val status: BehandlingStatus,
        val versjon: String,
        val beslutter: String?,
        val vedtakstidspunkt: LocalDateTime?,
        val resultat: ResultatKode?,
        val oppdatertTidspunkt: LocalDateTime,
    )

    private fun behandlingMedHendelser(vararg hendelser: BehandlingHendelse): Behandling {
        val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
        return Behandling(
            referanse = UUID.randomUUID(),
            sak = Sak(
                saksnummer = Saksnummer("SAK123"),
                person = Person(ident = Ident("0")),
                sistOppdatert = tidspunkt,
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = tidspunkt,
            hendelser = hendelser.toList(),
            tilstand = BehandlingTilstand(
                status = BehandlingStatus.OPPRETTET,
                mottattTid = tidspunkt,
                versjon = Versjon("1"),
                søknadsformat = SøknadsFormat.DIGITAL,
            ),
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
        tilstand = BehandlingTilstand(
            gjeldendeAvklaringsBehov = avklaringsbehov.tilAvklaringsbehovKode(),
            gjeldendeAvklaringsbehovStatus = Status.OPPRETTET.tilDomene(),
            sisteSaksbehandler = "Albert",
            versjon = versjon,
            status = status,
            ansvarligBeslutter = "Åberg",
            vedtakstidspunkt = null,
            mottattTid = hendelsesTidspunkt,
            søknadsformat = SøknadsFormat.DIGITAL,
            relatertBehandlingReferanse = null,
        ),
    )

    private fun behandlingHendelse(avklaringsbehov: Definisjon): BehandlingHendelse = BehandlingHendelse(
        tidspunkt = LocalDateTime.now(),
        hendelsesTidspunkt = LocalDateTime.now(),
        tilstand = BehandlingTilstand(
            gjeldendeAvklaringsBehov = avklaringsbehov.tilAvklaringsbehovKode(),
            gjeldendeAvklaringsbehovStatus = Status.OPPRETTET.tilDomene(),
            sisteSaksbehandler = "Albert",
            versjon = Versjon("323"),
            status = BehandlingStatus.OPPRETTET,
            ansvarligBeslutter = "Åberg",
            vedtakstidspunkt = LocalDateTime.now(),
            mottattTid = LocalDateTime.now(),
            søknadsformat = SøknadsFormat.DIGITAL,
            relatertBehandlingReferanse = null,
        ),
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

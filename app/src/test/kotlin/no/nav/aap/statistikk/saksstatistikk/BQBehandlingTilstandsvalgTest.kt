package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.BehandlingTilstand
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.tilDomene
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class BQBehandlingTilstandsvalgTest {
    private val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val referanse = UUID.randomUUID()

    @Test
    fun `returstatus leses fra behandlingen mens returbegrunnelsen leses fra siste hendelse`() {
        val grunnlagBehandling = behandling()
        val behandling = grunnlagBehandling.copy(
            hendelser = listOf(
                BehandlingHendelse(
                    tidspunkt = tidspunkt,
                    hendelsesTidspunkt = tidspunkt.plusMinutes(1),
                    tilstand = BehandlingTilstand(
                        gjeldendeAvklaringsbehovStatus = Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER.tilDomene(),
                        returÅrsak = "Historikkens returårsak",
                        versjon = Versjon("historikk"),
                        status = BehandlingStatus.UTREDES,
                        mottattTid = tidspunkt,
                        søknadsformat = SøknadsFormat.DIGITAL,
                        relatertBehandlingReferanse = null,
                    ),
                ),
            ),
            tilstand = grunnlagBehandling.nåværendeTilstand().copy(
                gjeldendeAvklaringsbehovStatus = Status.SENDT_TILBAKE_FRA_BESLUTTER.tilDomene(),
                returÅrsak = "Behandlingens returårsak",
            ),
        )
        val grunnlag = statistikkGrunnlag(behandling)

        assertThat(BQBehandlingMapper.bqBehandlingForBehandling(grunnlag)).isEqualTo(
            BQBehandling(
                behandlingUUID = referanse,
                ferdigbehandletTid = null,
                behandlingType = "FØRSTEGANGSBEHANDLING",
                aktorId = "0",
                saksnummer = "SAK123",
                tekniskTid = tidspunkt.plusDays(1),
                registrertTid = tidspunkt,
                endretTid = tidspunkt.plusMinutes(1),
                versjon = "historikk",
                mottattTid = tidspunkt.minusDays(1),
                opprettetAv = "Oppretter",
                ansvarligBeslutter = "Ytre beslutter",
                vedtakTid = tidspunkt.minusMinutes(5),
                søknadsFormat = SøknadsFormat.PAPIR,
                saksbehandler = null,
                behandlingMetode = BehandlingMetode.AUTOMATISK,
                behandlingStatus = "UNDER_BEHANDLING_SENDT_TILBAKE_FRA_BESLUTTER",
                behandlingÅrsak = "SØKNAD",
                behandlingResultat = null,
                resultatBegrunnelse = "Historikkens returårsak",
                ansvarligEnhetKode = "KELVIN_AUTOMATISK",
                sakYtelse = "AAP",
                erResending = false,
            ),
        )
    }

    @Test
    fun `publisert rad bruker siste hendelses vedtaksdata og sortert tidslinje`() {
        val første = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt.plusMinutes(2),
            tilstand = BehandlingTilstand(
                versjon = Versjon("første"),
                status = BehandlingStatus.AVSLUTTET,
                mottattTid = tidspunkt.minusHours(1),
                søknadsformat = SøknadsFormat.DIGITAL,
                ansvarligBeslutter = "Tidligere beslutter",
                vedtakstidspunkt = tidspunkt.minusMinutes(3),
                resultat = ResultatKode.INNVILGET,
            ),
        )
        val siste = første.copy(
            hendelsesTidspunkt = tidspunkt.plusMinutes(1),
            tilstand = første.tilstand.copy(
                versjon = Versjon("siste"),
                ansvarligBeslutter = "Siste beslutter",
                vedtakstidspunkt = tidspunkt.minusMinutes(1),
                resultat = ResultatKode.AVSLAG,
            ),
        )
        val nyeste = første.copy(
            hendelsesTidspunkt = tidspunkt.plusMinutes(4),
            tilstand = første.tilstand.copy(versjon = Versjon("nyeste")),
        )
        val grunnlag = statistikkGrunnlag(behandling().copy(hendelser = listOf(første, nyeste, siste)))

        assertThat(BQBehandlingMapper.bqBehandlingForBehandling(grunnlag)).isEqualTo(
            BQBehandling(
                behandlingUUID = referanse,
                ferdigbehandletTid = tidspunkt.plusMinutes(2),
                behandlingType = "FØRSTEGANGSBEHANDLING",
                aktorId = "0",
                saksnummer = "SAK123",
                tekniskTid = tidspunkt.plusDays(1),
                registrertTid = tidspunkt,
                endretTid = tidspunkt.plusMinutes(4),
                versjon = "siste",
                mottattTid = tidspunkt.minusDays(1),
                opprettetAv = "Oppretter",
                ansvarligBeslutter = "Siste beslutter",
                vedtakTid = tidspunkt.minusMinutes(1),
                søknadsFormat = SøknadsFormat.PAPIR,
                saksbehandler = "Ytre saksbehandler",
                behandlingMetode = BehandlingMetode.AUTOMATISK,
                behandlingStatus = "AVSLUTTET",
                behandlingÅrsak = "SØKNAD",
                behandlingResultat = "AVSLAG",
                resultatBegrunnelse = null,
                ansvarligEnhetKode = "KELVIN_AUTOMATISK",
                sakYtelse = "AAP",
                erResending = false,
            ),
        )
    }

    @Test
    fun `manglende vedtaksdata i siste hendelse bruker behandlingens vedtak men ikke tidligere resultat`() {
        val første = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt.plusMinutes(2),
            tilstand = BehandlingTilstand(
                versjon = Versjon("første"),
                status = BehandlingStatus.AVSLUTTET,
                mottattTid = tidspunkt.minusHours(1),
                søknadsformat = SøknadsFormat.DIGITAL,
                ansvarligBeslutter = "Tidligere beslutter",
                vedtakstidspunkt = tidspunkt.minusMinutes(3),
                resultat = ResultatKode.INNVILGET,
            ),
        )
        val siste = første.copy(
            hendelsesTidspunkt = tidspunkt.plusMinutes(1),
            tilstand = første.tilstand.copy(
                versjon = Versjon("siste"),
                ansvarligBeslutter = null,
                vedtakstidspunkt = null,
                resultat = null,
            ),
        )
        val nyeste = første.copy(
            hendelsesTidspunkt = tidspunkt.plusMinutes(4),
            tilstand = første.tilstand.copy(versjon = Versjon("nyeste")),
        )
        val grunnlag = statistikkGrunnlag(behandling().copy(hendelser = listOf(første, nyeste, siste)))

        assertThat(BQBehandlingMapper.bqBehandlingForBehandling(grunnlag)).isEqualTo(
            BQBehandling(
                behandlingUUID = referanse,
                ferdigbehandletTid = tidspunkt.plusMinutes(2),
                behandlingType = "FØRSTEGANGSBEHANDLING",
                aktorId = "0",
                saksnummer = "SAK123",
                tekniskTid = tidspunkt.plusDays(1),
                registrertTid = tidspunkt,
                endretTid = tidspunkt.plusMinutes(4),
                versjon = "siste",
                mottattTid = tidspunkt.minusDays(1),
                opprettetAv = "Oppretter",
                ansvarligBeslutter = "Ytre beslutter",
                vedtakTid = tidspunkt.minusMinutes(5),
                søknadsFormat = SøknadsFormat.PAPIR,
                saksbehandler = "Ytre saksbehandler",
                behandlingMetode = BehandlingMetode.AUTOMATISK,
                behandlingStatus = "AVSLUTTET",
                behandlingÅrsak = "SØKNAD",
                behandlingResultat = "UDEFINERT",
                resultatBegrunnelse = null,
                ansvarligEnhetKode = "KELVIN_AUTOMATISK",
                sakYtelse = "AAP",
                erResending = false,
            ),
        )
    }

    private fun statistikkGrunnlag(behandling: Behandling): SaksstatistikkGrunnlag = SaksstatistikkGrunnlag(
        behandling = behandling,
        erSkjermet = false,
        relatertBehandlingUUID = null,
        oppgaver = emptyList(),
        rettighetstyper = emptyList(),
        tekniskTid = tidspunkt.plusDays(1),
    )

    private fun behandling(): Behandling = Behandling(
        referanse = referanse,
        sak = Sak(
            saksnummer = Saksnummer("SAK123"),
            person = Person(ident = Ident("0")),
            sistOppdatert = tidspunkt,
        ),
        typeBehandling = TypeBehandling.Førstegangsbehandling,
        opprettetTid = tidspunkt,
        oppdatertTidspunkt = tidspunkt.plusMinutes(3),
        opprettetAv = "Oppretter",
        årsakTilOpprettelse = "SØKNAD",
        tilstand = BehandlingTilstand(
            status = BehandlingStatus.UTREDES,
            mottattTid = tidspunkt.minusDays(1),
            versjon = Versjon("ytre"),
            søknadsformat = SøknadsFormat.PAPIR,
            ansvarligBeslutter = "Ytre beslutter",
            vedtakstidspunkt = tidspunkt.minusMinutes(5),
            sisteSaksbehandler = "Ytre saksbehandler",
            resultat = ResultatKode.INNVILGET,
        ),
    )
}

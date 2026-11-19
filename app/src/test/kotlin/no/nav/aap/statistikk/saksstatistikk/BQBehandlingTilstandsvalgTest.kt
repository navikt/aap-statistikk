package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.util.UUID

class BQBehandlingTilstandsvalgTest {
    private val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val referanse = UUID.randomUUID()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `publisert rad kombinerer ytre tilstand siste hendelse og sortert tidslinje`(harVedtaksdata: Boolean) {
        val første = BehandlingHendelse(
            tidspunkt = tidspunkt,
            hendelsesTidspunkt = tidspunkt.plusMinutes(2),
            avklaringsbehovStatus = null,
            versjon = Versjon("første"),
            status = BehandlingStatus.AVSLUTTET,
            mottattTid = tidspunkt.minusHours(1),
            søknadsformat = SøknadsFormat.DIGITAL,
            ansvarligBeslutter = "Tidligere beslutter",
            vedtakstidspunkt = tidspunkt.minusMinutes(3),
            resultat = ResultatKode.INNVILGET,
            relatertBehandlingReferanse = null,
        )
        val siste = første.copy(
            hendelsesTidspunkt = tidspunkt.plusMinutes(1),
            versjon = Versjon("siste"),
            ansvarligBeslutter = if (harVedtaksdata) "Siste beslutter" else null,
            vedtakstidspunkt = if (harVedtaksdata) tidspunkt.minusMinutes(1) else null,
            resultat = if (harVedtaksdata) ResultatKode.AVSLAG else null,
        )
        val nyeste = første.copy(hendelsesTidspunkt = tidspunkt.plusMinutes(4), versjon = Versjon("nyeste"))
        val grunnlag = SaksstatistikkGrunnlag(
            behandling = behandling().copy(hendelser = listOf(første, nyeste, siste)),
            erSkjermet = false,
            relatertBehandlingUUID = null,
            oppgaver = emptyList(),
            rettighetstyper = emptyList(),
            tekniskTid = tidspunkt.plusDays(1),
        )

        assertThat(BQBehandlingMapper.bqBehandlingForBehandling(grunnlag))
            .isEqualTo(forventetRad(harVedtaksdata))
    }

    private fun forventetRad(harVedtaksdata: Boolean): BQBehandling = BQBehandling(
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
        ansvarligBeslutter = if (harVedtaksdata) "Siste beslutter" else "Ytre beslutter",
        vedtakTid = if (harVedtaksdata) tidspunkt.minusMinutes(1) else tidspunkt.minusMinutes(5),
        søknadsFormat = SøknadsFormat.PAPIR,
        saksbehandler = "Ytre saksbehandler",
        behandlingMetode = BehandlingMetode.AUTOMATISK,
        behandlingStatus = "AVSLUTTET",
        behandlingÅrsak = "SØKNAD",
        behandlingResultat = if (harVedtaksdata) "AVSLAG" else "UDEFINERT",
        resultatBegrunnelse = null,
        ansvarligEnhetKode = "KELVIN_AUTOMATISK",
        sakYtelse = "AAP",
        erResending = false,
    )

    private fun behandling(): Behandling = Behandling(
        referanse = referanse,
        sak = Sak(
            saksnummer = Saksnummer("SAK123"),
            person = Person(ident = Ident("0")),
            sakStatus = SakStatus.UTREDES,
            sistOppdatert = tidspunkt,
        ),
        typeBehandling = TypeBehandling.Førstegangsbehandling,
        status = BehandlingStatus.UTREDES,
        opprettetTid = tidspunkt,
        mottattTid = tidspunkt.minusDays(1),
        versjon = Versjon("ytre"),
        søknadsformat = SøknadsFormat.PAPIR,
        ansvarligBeslutter = "Ytre beslutter",
        vedtakstidspunkt = tidspunkt.minusMinutes(5),
        sisteSaksbehandler = "Ytre saksbehandler",
        resultat = ResultatKode.INNVILGET,
        oppdatertTidspunkt = tidspunkt.plusMinutes(3),
        opprettetAv = "Oppretter",
        årsakTilOpprettelse = "SØKNAD",
    )
}

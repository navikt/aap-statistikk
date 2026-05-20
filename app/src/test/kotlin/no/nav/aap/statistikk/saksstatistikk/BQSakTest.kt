package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.behandling.SøknadsFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import java.time.LocalDateTime
import java.util.UUID

class BQSakTest {

    private fun lagBQBehandling(ansvarligEnhetKode: String?) = BQBehandling(
        behandlingUUID = UUID.randomUUID(),
        behandlingType = "REVURDERING",
        aktorId = "12345678901",
        saksnummer = "123",
        tekniskTid = LocalDateTime.now(),
        registrertTid = LocalDateTime.now(),
        endretTid = LocalDateTime.now(),
        mottattTid = LocalDateTime.now(),
        versjon = "1",
        avsender = KELVIN,
        opprettetAv = KELVIN,
        ansvarligBeslutter = null,
        søknadsFormat = SøknadsFormat.DIGITAL,
        saksbehandler = null,
        behandlingMetode = BehandlingMetode.MANUELL,
        behandlingStatus = "UNDER_BEHANDLING",
        behandlingÅrsak = "SØKNAD",
        ansvarligEnhetKode = ansvarligEnhetKode,
        sakYtelse = "AAP",
        resultatBegrunnelse = null,
        erResending = false,
    )

    @ParameterizedTest
    @CsvSource("0393, UTLAND", "4402, UTLAND", "1234, NASJONAL")
    fun `sakUtland utledes fra enhet`(enhet: String, forventet: String) {
        assertThat(lagBQBehandling(enhet).sakUtland).isEqualTo(forventet)
    }

    @ParameterizedTest
    @NullSource
    fun `null enhet gir NASJONAL`(enhet: String?) {
        assertThat(lagBQBehandling(enhet).sakUtland).isEqualTo("NASJONAL")
    }
}

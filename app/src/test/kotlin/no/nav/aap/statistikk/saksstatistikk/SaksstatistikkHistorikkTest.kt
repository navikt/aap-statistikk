package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.saksstatistikk.SaksStatistikkServiceTest.Companion.lagTestBQBehandling
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime
import java.util.UUID

class SaksstatistikkHistorikkTest {
    private val start = LocalDateTime.of(2026, 11, 18, 10, 0)
    private val grunnlag = lagTestBQBehandling(
        behandlingUUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        endretTid = start,
        registrertTid = start,
    ).copy(tekniskTid = start)

    @Test
    fun `gamle tidspunkter før mellom og etter nye hendelser får nye data`() {
        val første = grunnlag.copy(endretTid = start.plusSeconds(2), saksbehandler = "Z111111")
        val andre = grunnlag.copy(endretTid = start.plusSeconds(4), saksbehandler = "Z222222")
        val originale = listOf(7L, 3L, 1L, 5L).map {
            grunnlag.copy(endretTid = start.plusSeconds(it), saksbehandler = "GAMMEL")
        }

        val resultat = flettSaksstatistikkHistorikk(originale, listOf(andre, første))

        assertThat(resultat).containsExactly(
            første.copy(endretTid = start.plusSeconds(1)),
            andre.copy(endretTid = start.plusSeconds(5)),
        )
    }

    @Test
    fun `et gammelt tidspunkt etter flere nye hendelser bruker den første og beholder resten`() {
        val første = grunnlag.copy(endretTid = start.plusSeconds(1), saksbehandler = "Z111111")
        val andre = grunnlag.copy(endretTid = start.plusSeconds(2), saksbehandler = "Z222222")
        val original = grunnlag.copy(endretTid = start.plusSeconds(3))

        val resultat = flettSaksstatistikkHistorikk(listOf(original), listOf(første, andre))

        assertThat(resultat).containsExactly(andre, første.copy(endretTid = original.endretTid))
    }

    @ParameterizedTest
    @CsvSource("0, true", "10999999, true", "11000000, false")
    fun `nære tidspunkter bruker millisekundavkorting ved matching`(avstandNanos: Long, matcher: Boolean) {
        val første = grunnlag.copy(saksbehandler = "Z111111")
        val andre = grunnlag.copy(endretTid = start.plusSeconds(1), saksbehandler = "Z222222")
        val originalTid = andre.endretTid.minusNanos(avstandNanos)
        val originale = listOf(grunnlag, grunnlag.copy(endretTid = originalTid))

        val resultat = flettSaksstatistikkHistorikk(originale, listOf(første, andre))
        val forventet = if (matcher) {
            listOf(første, andre.copy(endretTid = originalTid))
        } else {
            listOf(første, andre)
        }

        assertThat(resultat).containsExactlyElementsOf(forventet)
    }

    @Test
    fun `resultatet sorteres etter teknisk tid ved lik funksjonell tid`() {
        val tidlig = grunnlag.copy(tekniskTid = start.plusSeconds(1), saksbehandler = "Z111111")
        val sen = grunnlag.copy(tekniskTid = start.plusSeconds(2), saksbehandler = "Z222222")

        val resultat = flettSaksstatistikkHistorikk(emptyList(), listOf(sen, tidlig))

        assertThat(resultat).containsExactly(tidlig, sen)
    }

    @Test
    fun `uten gamle hendelser beholdes sorterte nye data og bare påfølgende duplikater fjernes`() {
        val første = grunnlag.copy(saksbehandler = "Z111111")
        val duplikat = første.copy(
            endretTid = start.plusSeconds(1),
            tekniskTid = start.plusSeconds(10),
            sekvensNummer = 2,
            versjon = "v2",
            erResending = true,
        )
        val endret = første.copy(endretTid = start.plusSeconds(2), saksbehandler = "Z222222")
        val tilbake = første.copy(endretTid = start.plusSeconds(3))

        val resultat = flettSaksstatistikkHistorikk(emptyList(), listOf(tilbake, duplikat, endret, første))

        assertThat(resultat).containsExactly(første, endret, tilbake)
    }

    @Test
    fun `nye hendelser med lik funksjonell tid beholder innsendt rekkefølge ved matching`() {
        val første = grunnlag.copy(tekniskTid = start.plusSeconds(2), saksbehandler = "Z111111")
        val andre = grunnlag.copy(tekniskTid = start.plusSeconds(1), saksbehandler = "Z222222")
        val original = grunnlag.copy(endretTid = start.minusSeconds(1))

        val resultat = flettSaksstatistikkHistorikk(listOf(original), listOf(første, andre))

        assertThat(resultat).containsExactly(første.copy(endretTid = original.endretTid), andre)
    }

    @Test
    fun `tomme historikker gir tomt resultat`() {
        assertThat(flettSaksstatistikkHistorikk(emptyList(), emptyList())).isEmpty()
    }

    @Test
    fun `gamle hendelser uten nye data kan ikke flettes`() {
        assertThatThrownBy { flettSaksstatistikkHistorikk(listOf(grunnlag), emptyList()) }
            .isInstanceOf(NoSuchElementException::class.java)
    }
}

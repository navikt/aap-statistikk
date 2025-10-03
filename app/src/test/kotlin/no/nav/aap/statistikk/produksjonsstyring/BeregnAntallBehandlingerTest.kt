package no.nav.aap.statistikk.produksjonsstyring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BeregnAntallBehandlingerTest {

    private val mandag = LocalDate.of(2024, 10, 28)
    private val tirsdag = LocalDate.of(2024, 10, 29)
    private val onsdag = LocalDate.of(2024, 10, 30)
    private val torsdag = LocalDate.of(2024, 10, 31)
    private val fredag = LocalDate.of(2024, 11, 1)

    @Test
    fun `Skal beregne antall behandlinger`() {
        val antallBehandlingerPerDag = BeregnAntallBehandlinger.antallBehandlingerPerDag(
            antallNye = listOf(AntallPerDag(mandag, 1), AntallPerDag(onsdag, 2), AntallPerDag(fredag, 3)),
            antallAvsluttede = listOf(AntallPerDag(tirsdag, 3), AntallPerDag(torsdag, 4)),
            antallÅpneBehandlinger = 100
        )

        assertThat(antallBehandlingerPerDag[mandag]).isEqualTo(AntallBehandlinger(nye = 1, avsluttede = 0, totalt = 102))
        assertThat(antallBehandlingerPerDag[tirsdag]).isEqualTo(AntallBehandlinger(nye = 0, avsluttede = 3, totalt = 99))
        assertThat(antallBehandlingerPerDag[onsdag]).isEqualTo(AntallBehandlinger(nye = 2, avsluttede = 0, totalt = 101))
        assertThat(antallBehandlingerPerDag[torsdag]).isEqualTo(AntallBehandlinger(nye = 0, avsluttede = 4, totalt = 97))
        assertThat(antallBehandlingerPerDag[fredag]).isEqualTo(AntallBehandlinger(nye = 3, avsluttede = 0, totalt = 100))
    }

    @Test
    fun `Skal fylle hull i datagrunnlaget`() {
        val antallBehandlingerPerDag = BeregnAntallBehandlinger.antallBehandlingerPerDag(
            antallNye = listOf(AntallPerDag(mandag, 1)),
            antallAvsluttede = listOf(AntallPerDag(onsdag, 2)),
            antallÅpneBehandlinger = 100
        )

        assertThat(antallBehandlingerPerDag[mandag]).isEqualTo(AntallBehandlinger(nye = 1, avsluttede = 0, totalt = 102))
        assertThat(antallBehandlingerPerDag[tirsdag]).isEqualTo(AntallBehandlinger(nye = 0, avsluttede = 0, totalt = 102))
        assertThat(antallBehandlingerPerDag[onsdag]).isEqualTo(AntallBehandlinger(nye = 0, avsluttede = 2, totalt = 100))
    }


}
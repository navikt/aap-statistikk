package no.nav.aap.statistikk.produksjonsstyring

import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate
import kotlin.test.Test

class BeregnAntallBehandlingerTest {

    @Test
    fun `Skal beregne antall behandlinger`() {
        val mandag = LocalDate.of(2024, 10, 28)
        val tirsdag = LocalDate.of(2024, 10, 29)
        val onsdag = LocalDate.of(2024, 10, 30)
        val torsdag = LocalDate.of(2024, 10, 31)
        val fredag = LocalDate.of(2024, 11, 1)

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

}
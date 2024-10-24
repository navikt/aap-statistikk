package no.nav.aap.statistikk.pdl

import no.nav.aap.statistikk.testutils.FakePdlClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SkjermingServiceTest {

    @Test
    fun `returnerer skjermet-status`() {
        val service =
            SkjermingService(FakePdlClient(identerHemmelig = mapOf("123" to true, "456" to false)))

        assertThat(service.erSkjermet(identer = listOf("123"))).isTrue()
        assertThat(service.erSkjermet(identer = listOf("456"))).isFalse()
        assertThat(service.erSkjermet(identer = listOf("123", "456"))).isTrue()
    }
}
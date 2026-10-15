package no.nav.aap.statistikk.kodeverk

import kotlinx.html.stream.createHTML
import kotlinx.html.body
import kotlinx.html.html
import no.nav.aap.statistikk.behandling.ÅrsakTilSattPåVent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KodeverkAPITest {
    @Test
    fun `tabell for venteårsaker viser alle enumverdier`() {
        val html = createHTML().html {
            body {
                venteårsakerTabell()
            }
        }

        assertThat(html).contains("<h2>Venteårsaker</h2>")
        ÅrsakTilSattPåVent.entries.forEach {
            assertThat(html).contains("<td>${it.name}</td>")
        }
    }
}

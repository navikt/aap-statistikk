package no.nav.aap.statistikk.pdl

import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.SakStatus
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.testutils.FakePdlClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class SkjermingServiceTest {

    private val behandling = Behandling(
        referanse = UUID.randomUUID(),
        sak = Sak(
            saksnummer = "234",
            person = Person(
                ident = "130289"
            ),
            sakStatus = SakStatus.LØPENDE,
            sistOppdatert = LocalDateTime.now()
        ),
        typeBehandling = TypeBehandling.Førstegangsbehandling,
        status = BehandlingStatus.UTREDES,
        opprettetTid = LocalDateTime.now(),
        mottattTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
        versjon = Versjon(
            verdi = "xxx"
        )
    )

    @Test
    fun `returnerer skjermet-true om noen av relaterte identer er skjermet`() {
        val service =
            SkjermingService(FakePdlClient(identerHemmelig = mapOf("123" to true, "456" to false)))

        assertThat(service.erSkjermet(behandling.copy(relaterteIdenter = listOf("123")))).isTrue()
        assertThat(service.erSkjermet(behandling.copy(relaterteIdenter = listOf("456")))).isFalse()
        assertThat(service.erSkjermet(behandling.copy(relaterteIdenter = listOf("123", "456"))))
    }

    @Test
    fun `returnerer skjermet om personen på saken er skjermet`() {
        val service =
            SkjermingService(
                FakePdlClient(
                    identerHemmelig = mapOf(
                        "123" to true,
                        behandling.sak.person.ident to true
                    )
                )
            )


        assertThat(service.erSkjermet(behandling)).isTrue()
    }

    @Test
    fun `returnerer false om ingen relaterte er skjermede om personen på saken er skjermet`() {
        val service = SkjermingService(FakePdlClient(identerHemmelig = mapOf()))


        assertThat(service.erSkjermet(behandling)).isFalse()
    }
}
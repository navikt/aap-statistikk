package no.nav.aap.statistikk.pdl

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.integrasjoner.pdl.PdlGateway
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.FakePdlGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class SkjermingServiceTest {

    private val behandling = Behandling(
        referanse = UUID.randomUUID(),
        sak = Sak(
            saksnummer = Saksnummer("234"),
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
        ),
        søknadsformat = SøknadsFormat.PAPIR,
    )

    @Test
    fun `returnerer skjermet-true om noen av relaterte identer er skjermet`() {
        val service =
            SkjermingService(FakePdlGateway(identerHemmelig = mapOf("123" to true, "456" to false)))

        assertThat(service.erSkjermet(behandling.copy(relaterteIdenter = listOf("123")))).isTrue()
        assertThat(service.erSkjermet(behandling.copy(relaterteIdenter = listOf("456")))).isFalse()
        assertThat(service.erSkjermet(behandling.copy(relaterteIdenter = listOf("123", "456"))))
    }

    @Test
    fun `returnerer skjermet om personen på saken er skjermet`() {
        val service =
            SkjermingService(
                FakePdlGateway(
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
        val service = SkjermingService(FakePdlGateway(identerHemmelig = mapOf()))


        assertThat(service.erSkjermet(behandling)).isFalse()
    }

    @Test
    fun `om pdl-kall feiler, returneres false med warning i logg`() {
        val logger =
            LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val listAppender = ListAppender<ILoggingEvent>()

        listAppender.start()

        logger.addAppender(listAppender)

        val service = SkjermingService(object : PdlGateway {
            override fun hentPersoner(identer: List<String>): List<no.nav.aap.statistikk.integrasjoner.pdl.Person> {
                throw Exception("oopsie")
            }
        })


        val res = service.erSkjermet(behandling)

        assertThat(res).isFalse()
        assertThat(listAppender.list.map { it.message }).anySatisfy { it.contains("Returnerer false for skjerming. Se stackTrace") }
    }
}
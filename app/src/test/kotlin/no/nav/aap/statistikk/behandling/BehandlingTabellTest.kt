package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.VURDER_FORMKRAV_KODE
import no.nav.aap.statistikk.avsluttetbehandling.RettighetsType
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.BigQuery
import no.nav.aap.statistikk.testutils.schemaRegistry
import no.nav.aap.utbetaling.helved.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.Consumer

class BehandlingTabellTest {
    @Test
    fun `sette inn og ta ut`(@BigQuery bigQuery: BigQueryConfig) {
        val client = BigQueryClient(bigQuery, schemaRegistry)

        val tabell = BehandlingTabell()

        val referanse = UUID.randomUUID()

        val datoAvsluttet = LocalDateTime.now()
        val rettighetsPerioder = listOf(
            RettighetstypePeriode(
                fraDato = LocalDate.now(),
                tilDato = LocalDate.now().plusDays(1),
                rettighetstype = RettighetsType.STUDENT
            ),
            RettighetstypePeriode(
                fraDato = LocalDate.now().plusDays(5),
                tilDato = LocalDate.now().plusDays(3),
                rettighetstype = RettighetsType.BISTANDSBEHOV
            )
        )
        val datoOpprettet = LocalDateTime.now().minusDays(1)
        client.insert(
            tabell, BQYtelseBehandling(
                saksnummer = Saksnummer("123XXX"),
                referanse = referanse,
                brukerFnr = "2902198512345",
                behandlingsType = TypeBehandling.Førstegangsbehandling,
                datoOpprettet = datoOpprettet,
                datoAvsluttet = datoAvsluttet,
                kodeverk = "IC23",
                diagnosekode = "PEST",
                bidiagnoser = listOf("KOLERA", "BOLIGSKADE"),
                radEndret = LocalDateTime.now(),
                rettighetsPerioder = rettighetsPerioder,
                utbetalingId = referanse.toBase64(),
                vurderingsbehov = listOf(
                    Vurderingsbehov.OVERGANG_UFORE.name,
                    Vurderingsbehov.SØKNAD.name
                )
            )
        )

        val read = client.read(tabell)

        assertThat(read).hasSize(1).first().satisfies(Consumer {
            assertThat(it.saksnummer.value).isEqualTo("123XXX")
            assertThat(it.referanse).isEqualTo(referanse)
            assertThat(it.brukerFnr).isEqualTo("2902198512345")
            assertThat(it.behandlingsType).isEqualTo(TypeBehandling.Førstegangsbehandling)
            assertThat(it.datoAvsluttet).isCloseTo(
                datoAvsluttet,
                within(500, ChronoUnit.MILLIS)
            )
            assertThat(it.datoOpprettet).isCloseTo(datoOpprettet, within(500, ChronoUnit.MILLIS))
            assertThat(it.kodeverk).isEqualTo("IC23")
            assertThat(it.diagnosekode).isEqualTo("PEST")
            assertThat(it.bidiagnoser).isEqualTo(listOf("KOLERA", "BOLIGSKADE"))
            assertThat(it.rettighetsPerioder).isEqualTo(rettighetsPerioder)
            assertThat(it.utbetalingId).isEqualTo(referanse.toBase64())
            assertThat(it.vurderingsbehov).isEqualTo(
                listOf(
                    Vurderingsbehov.OVERGANG_UFORE.name,
                    Vurderingsbehov.SØKNAD.name
                )
            )
        })
    }
}
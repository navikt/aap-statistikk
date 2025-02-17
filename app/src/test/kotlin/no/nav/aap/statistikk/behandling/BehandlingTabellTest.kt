package no.nav.aap.statistikk.behandling

import no.nav.aap.statistikk.avsluttetbehandling.RettighetsType
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.testutils.BigQuery
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

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
        client.insert(
            tabell, BQYtelseBehandling(
                referanse = referanse,
                brukerFnr = "2902198512345",
                behandlingsType = TypeBehandling.Førstegangsbehandling,
                datoAvsluttet = datoAvsluttet,
                kodeverk = "IC23",
                diagnosekode = "PEST",
                bidiagnoser = listOf("KOLERA", "BOLIGSKADE"),
                radEndret = LocalDateTime.now(),
                rettighetsPerioder = rettighetsPerioder
            )
        )

        val read = client.read(tabell)

        assertThat(read.size).isEqualTo(1)
        assertThat(read.first().referanse).isEqualTo(referanse)
        assertThat(read.first().brukerFnr).isEqualTo("2902198512345")
        assertThat(read.first().behandlingsType).isEqualTo(TypeBehandling.Førstegangsbehandling)
        assertThat(read.first().datoAvsluttet).isCloseTo(
            datoAvsluttet,
            within(500, ChronoUnit.MILLIS)
        )
        assertThat(read.first().kodeverk).isEqualTo("IC23")
        assertThat(read.first().diagnosekode).isEqualTo("PEST")
        assertThat(read.first().bidiagnoser).isEqualTo(listOf("KOLERA", "BOLIGSKADE"))
        assertThat(read.first().rettighetsPerioder).isEqualTo(rettighetsPerioder)
    }
}
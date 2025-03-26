package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.ÅrsakTilBehandling
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.testutils.BigQuery
import no.nav.aap.statistikk.testutils.schemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class SakTabellTest {
    @Test
    fun `sette inn og hente ut igjen sak`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)

        val sakTabell = SakTabell()

        val referanse = UUID.randomUUID()

        val mottattTid =
            LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS)
        val registrertTid = LocalDateTime.now().minusDays(1).plusHours(1)
            .truncatedTo(ChronoUnit.SECONDS)
        val endretTid = LocalDateTime.now()

        val tekniskTid = LocalDateTime.now()
        client.insert(
            sakTabell, BQBehandling(
                saksnummer = "123",
                behandlingUUID = referanse.toString(),
                tekniskTid = tekniskTid,
                behandlingType = TypeBehandling.Revurdering.toString().uppercase(),
                avsender = KELVIN,
                verson = "versjon",
                sekvensNummer = 0L,
                aktorId = "123456",
                mottattTid = mottattTid,
                registrertTid = registrertTid,
                endretTid = endretTid,
                opprettetAv = KELVIN,
                saksbehandler = "1234",
                søknadsFormat = SøknadsFormat.DIGITAL,
                behandlingMetode = BehandlingMetode.MANUELL,
                relatertBehandlingUUID = "123",
                relatertFagsystem = "Kelvin",
                fagsystemNavn = "Kelvin",
                ansvarligBeslutter = "Z1234",
                behandlingStatus = "UNDER_BEHANDLING",
                behandlingÅrsak = ÅrsakTilBehandling.SØKNAD.toString(),
                ansvarligEnhetKode = "1337"
            )
        )

        val uthentet = client.read(sakTabell)

        assertThat(uthentet.size).isEqualTo(1)
        val uthentetInnslag = uthentet.first()
        assertThat(uthentetInnslag.saksnummer).isEqualTo("123")
        assertThat(uthentetInnslag.behandlingUUID).isEqualTo(
            referanse.toString()
        )
        assertThat(uthentetInnslag.behandlingType).isEqualTo("REVURDERING")
        assertThat(uthentetInnslag.sekvensNummer).isEqualTo(0L)
        assertThat(uthentetInnslag.aktorId).isEqualTo("123456")
        assertThat(uthentetInnslag.mottattTid).isCloseTo(mottattTid, within(500, ChronoUnit.MILLIS))
        assertThat(uthentetInnslag.registrertTid).isCloseTo(
            registrertTid,
            within(500, ChronoUnit.MILLIS)
        )
        assertThat(uthentetInnslag.endretTid).isCloseTo(endretTid, within(500, ChronoUnit.MILLIS))
        assertThat(uthentetInnslag.opprettetAv).isEqualTo(KELVIN)
        assertThat(uthentetInnslag.saksbehandler).isEqualTo("1234")
        assertThat(uthentetInnslag.tekniskTid).isCloseTo(tekniskTid, within(500, ChronoUnit.MILLIS))
        assertThat(uthentetInnslag.søknadsFormat).isEqualTo(SøknadsFormat.DIGITAL)
        assertThat(uthentetInnslag.behandlingMetode).isEqualTo(BehandlingMetode.MANUELL)
        assertThat(uthentetInnslag.relatertBehandlingUUID).isEqualTo("123")
        assertThat(uthentetInnslag.relatertFagsystem).isEqualTo("Kelvin")
        assertThat(uthentetInnslag.fagsystemNavn).isEqualTo("Kelvin")
        assertThat(uthentetInnslag.ansvarligBeslutter).isEqualTo("Z1234")
        assertThat(uthentetInnslag.behandlingStatus).isEqualTo("UNDER_BEHANDLING")
        assertThat(uthentetInnslag.ansvarligEnhetKode).isEqualTo("1337")
    }
}
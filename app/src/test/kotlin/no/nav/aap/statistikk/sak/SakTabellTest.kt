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
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.BiPredicate

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
        val sakYtelse = "AAP"

        val tekniskTid = LocalDateTime.now()
        client.insert(
            sakTabell, BQBehandling(
                fagsystemNavn = KELVIN,
                sekvensNummer = 1L,
                behandlingUUID = referanse.toString(),
                relatertBehandlingUUID = "123",
                relatertFagsystem = "Kelvin",
                behandlingType = TypeBehandling.Revurdering.toString().uppercase(),
                aktorId = "123456",
                saksnummer = "123",
                tekniskTid = tekniskTid,
                registrertTid = registrertTid,
                endretTid = endretTid,
                verson = "versjon",
                avsender = KELVIN,
                mottattTid = mottattTid,
                opprettetAv = KELVIN,
                ansvarligBeslutter = "Z1234",
                søknadsFormat = SøknadsFormat.DIGITAL,
                saksbehandler = "1234",
                behandlingMetode = BehandlingMetode.MANUELL,
                behandlingStatus = "UNDER_BEHANDLING",
                behandlingÅrsak = ÅrsakTilBehandling.SØKNAD.toString(),
                ansvarligEnhetKode = "1337",
                sakYtelse = sakYtelse,
                behandlingResultat = "AX"
            )
        )

        val uthentet = client.read(sakTabell)

        assertThat(uthentet.size).isEqualTo(1)
        val uthentetInnslag = uthentet.first()

        val dataComparator: BiPredicate<LocalDateTime, LocalDateTime> =
            BiPredicate { a, b -> Duration.between(a, b).toMillis() < 1000 }

        assertThat(uthentetInnslag).usingRecursiveComparison()
            .withEqualsForType(dataComparator, LocalDateTime::class.java)
            .isEqualTo(
                BQBehandling(
                    fagsystemNavn = "KELVIN",
                    sekvensNummer = 1,
                    behandlingUUID = referanse.toString(),
                    relatertBehandlingUUID = "123",
                    relatertFagsystem = "Kelvin",
                    ferdigbehandletTid = null, //
                    behandlingType = "REVURDERING",
                    aktorId = "123456",
                    saksnummer = "123",
                    tekniskTid = tekniskTid,
                    registrertTid = registrertTid,
                    endretTid = endretTid,
                    verson = "versjon",
                    avsender = KELVIN,
                    mottattTid = mottattTid,
                    opprettetAv = KELVIN,
                    ansvarligBeslutter = "Z1234",
                    vedtakTid = null, //
                    søknadsFormat = SøknadsFormat.DIGITAL,
                    saksbehandler = "1234",
                    behandlingMetode = BehandlingMetode.MANUELL,
                    behandlingStatus = "UNDER_BEHANDLING",
                    behandlingÅrsak = "SØKNAD",
                    ansvarligEnhetKode = "1337",
                    sakYtelse = "AAP",
                    behandlingResultat = "AX"
                )
            )
    }
}
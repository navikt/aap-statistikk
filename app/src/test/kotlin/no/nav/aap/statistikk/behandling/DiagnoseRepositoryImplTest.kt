package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class DiagnoseRepositoryImplTest {

    @Test
    fun `sette inn diagnose og hente ut igjen`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        dataSource.transaction {
            forberedDatabase(it, behandlingReferanse)
            settInnDiagnose(
                it, DiagnoseEntity(
                    behandlingReferanse = behandlingReferanse,
                    kodeverk = "dsdsd",
                    diagnosekode = "DD",
                    bidiagnoser = listOf("PEST", "KOLERA")
                )
            )
        }

        val uthentet = dataSource.transaction {
            DiagnoseRepositoryImpl(it).hentForBehandling(behandlingReferanse)
        }

        assertThat(uthentet).isEqualTo(
            DiagnoseEntity(
                behandlingReferanse = behandlingReferanse,
                kodeverk = "dsdsd",
                diagnosekode = "DD",
                bidiagnoser = listOf("PEST", "KOLERA")
            )
        )

    }

    private fun settInnDiagnose(
        it: DBConnection,
        diagnoseEntity: DiagnoseEntity
    ) = DiagnoseRepositoryImpl(it).lagre(
        diagnoseEntity
    )

    private fun forberedDatabase(
        it: DBConnection,
        behandlingReferanse: UUID
    ) {
        val ident = "214"
        val person = PersonService(PersonRepository(it)).hentEllerLagrePerson(ident)

        val sak = Sak(
            saksnummer = "ABCDE",
            person = person,
            sakStatus = SakStatus.LØPENDE,
            sistOppdatert = LocalDateTime.now()
        )
        val sakId = SakRepositoryImpl(it).settInnSak(sak)

        BehandlingRepository(it).opprettBehandling(
            Behandling(
                referanse = behandlingReferanse,
                sak = sak.copy(id = sakId),
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                status = BehandlingStatus.OPPRETTET,
                opprettetTid = LocalDateTime.now(),
                mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                versjon = Versjon("xxx"),
                søknadsformat = SøknadsFormat.DIGITAL,
            )
        )
    }
}
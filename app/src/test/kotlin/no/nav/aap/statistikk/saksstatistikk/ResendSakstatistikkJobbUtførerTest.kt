package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.konstruerSakstatistikkService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class ResendSakstatistikkJobbUtførerTest {

    @Test
    fun `start jobb`(@Postgres dataSource: DataSource) {
        val ref = SaksStatistikkServiceTest.lagreHendelser(dataSource)
        val behandlingId = dataSource.transaction { BehandlingRepository(it).hent(ref) }?.id!!

        val input = JobbInput(
            jobb = ResendSakstatistikkJobb()
        ).medPayload(behandlingId)

        dataSource.transaction {
            ResendSakstatistikkJobbUtfører(
                konstruerSakstatistikkService(it)
            ).utfør(input)
        }

        val uthentet =
            dataSource.transaction {
                SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(ref)
            }

        assertThat(uthentet).isNotEmpty
    }

}
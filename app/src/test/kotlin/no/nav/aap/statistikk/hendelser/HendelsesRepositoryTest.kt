package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.Postgres
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class HendelsesRepositoryTest {
    @Test
    fun `sett inn hendelse i db`(@Postgres dataSource: DataSource) {
        val repository = HendelsesRepository(dataSource)

        repository.lagreHendelse(MottaStatistikkDTO(saksNummer = "123", status = "AVS", behandlingsType = "asd"))

        val hentHendelser = repository.hentHendelser()

        assertThat(hentHendelser).hasSize(1)
        assertThat(hentHendelser.first()).isEqualTo(
            MottaStatistikkDTO(
                saksNummer = "123",
                status = "AVS",
                behandlingsType = "asd"
            )
        )
    }
}

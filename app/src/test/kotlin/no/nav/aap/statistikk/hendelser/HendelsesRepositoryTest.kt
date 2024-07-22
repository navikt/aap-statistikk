package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.WithPostgresContainer
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HendelsesRepositoryTest : WithPostgresContainer() {
    @Test
    fun `sett inn hendelse i db`() {
        val dataSource = dataSource()

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

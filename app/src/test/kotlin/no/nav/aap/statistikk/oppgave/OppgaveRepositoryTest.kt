package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.enhet.EnhetRepository
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class OppgaveRepositoryTest {
    @Test
    fun `sette inn og hent ut oppgave`(@Postgres dataSource: DataSource) {
        val enhet = dataSource.transaction {
            val enhet = Enhet(kode = "1234")
            enhet.copy(id = EnhetRepository(it).lagreEnhet(enhet))
        }

        val oppgave = Oppgave(
            identifikator = 12L,
            avklaringsbehov = "5099",
            enhet = enhet,
            person = null,
            status = Oppgavestatus.AVSLUTTET,
            opprettetTidspunkt = LocalDateTime.now(),
            reservasjon = null,
            behandlingReferanse = BehandlingReferanse(referanse = UUID.randomUUID()),
            hendelser = listOf()
        )

        dataSource.transaction {
            OppgaveRepository(it).lagreOppgave(oppgave)
        }

        val res = dataSource.transaction { OppgaveRepository(it).hentOppgave(12L) }

        assertThat(res).isNotNull

        assertThat(res!!.identifikator).isEqualTo(oppgave.identifikator)
        assertThat(res.avklaringsbehov).isEqualTo(oppgave.avklaringsbehov)
        assertThat(res.enhet).isEqualTo(oppgave.enhet)
        assertThat(res.person).isNull()
        assertThat(res.status).isEqualTo(oppgave.status)
        assertThat(res.opprettetTidspunkt).isCloseTo(
            oppgave.opprettetTidspunkt,
            within(1, ChronoUnit.SECONDS)
        )
        assertThat(res.reservasjon).isNull()
        assertThat(res.behandlingReferanse!!.referanse).isEqualTo(oppgave.behandlingReferanse!!.referanse)
        assertThat(res.hendelser).isEqualTo(oppgave.hendelser)
    }
}
package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource


class OppgaveHendelseRepositoryTest {
    @Test
    fun `Lagre og hente ut igjen oppgave-hendelser`(@Postgres dataSource: DataSource) {
        val oppgaveId = 123L
        val hendelse = OppgaveHendelse(
            hendelse = HendelseType.OPPRETTET,
            mottattTidspunkt = LocalDateTime.now(),
            personIdent = "12345678901",
            saksnummer = "S12345",
            behandlingRef = UUID.randomUUID(),
            journalpostId = 123,
            enhet = "NAVKontor123",
            avklaringsbehovKode = "Kode123",
            status = Oppgavestatus.OPPRETTET,
            reservertAv = "Saksbehandler123",
            reservertTidspunkt = LocalDateTime.now(),
            opprettetTidspunkt = LocalDateTime.now(),
            endretAv = "SaksbehandlerEndret123",
            endretTidspunkt = LocalDateTime.now(),
            oppgaveId = oppgaveId
        )
        dataSource.transaction {
            OppgaveHendelseRepository(it).lagreHendelse(
                hendelse = hendelse
            )
        }

        val uthentet = dataSource.transaction {
            OppgaveHendelseRepository(it).hentHendelserForId(oppgaveId)
        }

        assertThat(uthentet.first()).isEqualTo(hendelse)
    }
}
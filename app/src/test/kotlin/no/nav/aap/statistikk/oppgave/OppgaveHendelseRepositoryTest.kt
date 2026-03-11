package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource


class OppgaveHendelseRepositoryTest {
    @Test
    fun `Lagre og hente ut igjen oppgave-hendelser`(@Postgres dataSource: DataSource) {
        val oppgaveId = 123L
        val hendelse = OppgaveHendelse(
            hendelse = HendelseType.OPPRETTET,
            oppgaveId = oppgaveId,
            mottattTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
            personIdent = "12345678901",
            saksnummer = "S12345",
            behandlingRef = UUID.randomUUID(),
            journalpostId = 123,
            enhet = "NAVKontor123",
            avklaringsbehovKode = "Kode123",
            status = Oppgavestatus.OPPRETTET,
            reservertAv = "Saksbehandler123",
            reservertTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
            opprettetTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
            endretAv = "SaksbehandlerEndret123",
            endretTidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
            sendtTid = LocalDateTime.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS),
            versjon = 1L,
        )
        dataSource.transaction {
            OppgaveHendelseRepositoryImpl(it).lagreHendelse(
                hendelse = hendelse
            )
        }

        val uthentet = dataSource.transaction {
            OppgaveHendelseRepositoryImpl(it).hentHendelserForId(oppgaveId)
        }

        assertThat(uthentet.first()).isEqualTo(hendelse)
    }

    @Test
    fun `hendelser for id returneres sortert etter mottatt_tidspunkt - opprettet-hendelse uten endret_tidspunkt skal komme forst`(
        @Postgres dataSource: DataSource
    ) {
        val oppgaveId = 456L
        val tidlig = LocalDateTime.of(2025, 1, 1, 10, 0, 0)
        val sent = LocalDateTime.of(2025, 1, 1, 11, 0, 0)

        // OPPRETTET-hendelse har alltid null endret_tidspunkt
        val opprettetHendelse = OppgaveHendelse(
            hendelse = HendelseType.OPPRETTET,
            oppgaveId = oppgaveId,
            mottattTidspunkt = tidlig,
            personIdent = null,
            saksnummer = null,
            behandlingRef = null,
            journalpostId = null,
            enhet = "4491",
            avklaringsbehovKode = "5003",
            status = Oppgavestatus.OPPRETTET,
            opprettetTidspunkt = tidlig,
            endretTidspunkt = null,
            sendtTid = tidlig,
            versjon = 1L,
        )

        // LUKKET-hendelse har endret_tidspunkt satt
        val lukketHendelse = OppgaveHendelse(
            hendelse = HendelseType.LUKKET,
            oppgaveId = oppgaveId,
            mottattTidspunkt = sent,
            personIdent = null,
            saksnummer = null,
            behandlingRef = null,
            journalpostId = null,
            enhet = "4491",
            avklaringsbehovKode = "5003",
            status = Oppgavestatus.AVSLUTTET,
            opprettetTidspunkt = tidlig,
            endretTidspunkt = sent.minusSeconds(1),
            sendtTid = sent,
            versjon = 2L,
        )

        dataSource.transaction {
            val repo = OppgaveHendelseRepositoryImpl(it)
            repo.lagreHendelse(opprettetHendelse)
            repo.lagreHendelse(lukketHendelse)
        }

        val hendelser = dataSource.transaction {
            OppgaveHendelseRepositoryImpl(it).hentHendelserForId(oppgaveId)
        }

        assertThat(hendelser).hasSize(2)
        assertThat(hendelser.first().hendelse)
            .describedAs("OPPRETTET-hendelse skal komme forst, selv om endret_tidspunkt er null")
            .isEqualTo(HendelseType.OPPRETTET)
        assertThat(hendelser.last().hendelse).isEqualTo(HendelseType.LUKKET)
    }

    @Test
    fun `hent ut enhet for avklaringsbehov`(@Postgres dataSource: DataSource) {
        val oppgaveId = 40611L
        val behandlingRef = UUID.randomUUID()
        val hendelse = OppgaveHendelse(
            hendelse = HendelseType.LUKKET,
            oppgaveId = oppgaveId,
            mottattTidspunkt = LocalDateTime.parse("2025-08-20T12:35:06.260915"),
            personIdent = "02499243246",
            saksnummer = "4o2WNB4",
            behandlingRef = behandlingRef,
            enhet = "0417",
            avklaringsbehovKode = "5053",
            status = Oppgavestatus.AVSLUTTET,
            opprettetTidspunkt = LocalDateTime.parse("2025-08-20T12:34:58.428000"),
            endretAv = "Kelvin",
            endretTidspunkt = LocalDateTime.parse("2025-08-20T12:35:05.590000"),
            sendtTid = LocalDateTime.now().minusSeconds(1),
            versjon = 12L,
        )
        dataSource.transaction {
            OppgaveHendelseRepositoryImpl(it).lagreHendelse(
                hendelse = hendelse
            )
        }

        val uthentet = dataSource.transaction {
            OppgaveHendelseRepositoryImpl(it).hentEnhetOgReservasjonForAvklaringsbehov(
                behandlingRef,
                "5053"
            )
        }

        assertThat(uthentet).isEqualTo(
            listOf(
                EnhetReservasjonOgTidspunkt(
                    enhet = "0417",
                    reservertAv = null,
                    tidspunkt = LocalDateTime.parse("2025-08-20T12:35:06.260915"),
                )
            )
        )
    }
}
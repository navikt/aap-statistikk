package no.nav.aap.statistikk.postmottak

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.JobbInput
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.postmottak.kontrakt.behandling.Status
import no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling
import no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO
import no.nav.aap.postmottak.kontrakt.journalpost.JournalpostId
import no.nav.aap.postmottak.kontrakt.steg.StegType
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class LagrePostmottakHendelseJobbUtførerTest {
    @Test
    fun `lagre ny postmottak-hendelse`(@Postgres dataSource: DataSource) {
        val meterRegistry = SimpleMeterRegistry()
        val payload = DokumentflytStoppetHendelse(
            journalpostId = JournalpostId(123),
            ident = "543",
            referanse = UUID.randomUUID(),
            behandlingType = TypeBehandling.Journalføring,
            status = Status.OPPRETTET,
            avklaringsbehov = listOf(),
            opprettetTidspunkt = LocalDateTime.now(),
            hendelsesTidspunkt = LocalDateTime.now().minusSeconds(23),
            saksnummer = null
        )

        dataSource.transaction(block = utførLagreHendelseJobb(meterRegistry, payload))

        val uthentet = hentUt(dataSource, payload.referanse)

        assertThat(uthentet!!.journalpostId).isEqualTo(JournalpostId(123).referanse)
    }

    @Test
    fun `lagre flere nye postmottak-hendelser`(@Postgres dataSource: DataSource) {
        val meterRegistry = SimpleMeterRegistry()
        val referanse = UUID.randomUUID()
        val hendelse = DokumentflytStoppetHendelse(
            journalpostId = JournalpostId(123),
            ident = "543",
            referanse = referanse,
            behandlingType = TypeBehandling.Journalføring,
            status = Status.OPPRETTET,
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_TEMA,
                    status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            tidsstempel = LocalDateTime.of(2025, 1, 29, 12, 12),
                            frist = null,
                            endretAv = "Kelvin"
                        )
                    )
                )
            ),
            opprettetTidspunkt = LocalDateTime.now(),
            hendelsesTidspunkt = LocalDateTime.now().minusSeconds(23),
            saksnummer = null
        )

        dataSource.transaction(block = utførLagreHendelseJobb(meterRegistry, hendelse))

        val uthentet = hentUt(dataSource, hendelse.referanse)

        assertThat(uthentet!!.journalpostId).isEqualTo(JournalpostId(123).referanse)
        assertThat(uthentet.referanse).isEqualTo(referanse)

        dataSource.transaction(
            block = utførLagreHendelseJobb(
                meterRegistry,
                hendelse.copy(status = Status.AVSLUTTET)
            )
        )

        val uthentet2 = hentUt(dataSource, hendelse.referanse)

        assertThat(uthentet2!!.status()).isEqualTo(Status.AVSLUTTET.toString())
    }

    private fun hentUt(
        dataSource: DataSource,
        referanse: UUID
    ) = dataSource.transaction {
        PostmottakBehandlingRepository(it).hentEksisterendeBehandling(
            referanse
        )
    }

    private fun utførLagreHendelseJobb(
        meterRegistry: SimpleMeterRegistry,
        payload: DokumentflytStoppetHendelse
    ): (DBConnection) -> Unit = {
        LagrePostmottakHendelseJobbUtfører(
            PostmottakBehandlingService(PostmottakBehandlingRepository(it)), PersonService(
                PersonRepository(it)
            ), meterRegistry
        ).utfør(
            JobbInput(LagrePostmottakHendelseJobb(meterRegistry)).medPayload(
                payload
            )
        )
    }
}
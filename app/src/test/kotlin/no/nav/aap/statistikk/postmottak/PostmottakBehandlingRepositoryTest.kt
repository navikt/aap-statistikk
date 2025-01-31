package no.nav.aap.statistikk.postmottak

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class PostmottakBehandlingRepositoryTest {
    @Test
    fun `lagre og hente ut igjen`(@Postgres dataSource: DataSource) {
        val oppdatertTid = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        val behandling = opprettBehandling(
            dataSource, PostmottakBehandling(
                journalpostId = 213,
                person = Person("1234"),
                referanse = UUID.randomUUID(),
                behandlingType = TypeBehandling.Journalføring,
                mottattTid = oppdatertTid,
                endringer = mutableListOf(
                    PostmottakOppdatering(
                        gjeldende = true,
                        status = "ddd",
                        oppdatertTid = oppdatertTid,
                        sisteSaksbehandler = "sasdasd",
                        gjeldendeAvklaringsBehov = "asdasd"
                    )
                )
            )
        )

        val uthentet = hentUtBehandling(dataSource, behandling)

        assertThat(uthentet).isEqualTo(behandling)
    }

    @Test
    fun `ikke-eksisterende behandling returnerer null`(@Postgres dataSource: DataSource) {
        val uthentet = dataSource.transaction {
            PostmottakBehandlingRepository(it).hentEksisterendeBehandling(UUID.randomUUID())
        }

        assertNull(uthentet)
    }

    @Test
    fun `oppdater eksisterende behandling`(@Postgres dataSource: DataSource) {
        val behandling = opprettBehandling(
            dataSource, PostmottakBehandling(
                journalpostId = 213,
                person = Person("1235"),
                referanse = UUID.randomUUID(),
                behandlingType = TypeBehandling.Journalføring,
                mottattTid = LocalDateTime.now(),
                endringer = mutableListOf(
                    PostmottakOppdatering(
                        gjeldende = true,
                        status = "ddd",
                        oppdatertTid = LocalDateTime.now(),
                        sisteSaksbehandler = "sasdasd",
                        gjeldendeAvklaringsBehov = "asdasd"
                    )
                )
            )
        )

        val uthentet = hentUtBehandling(dataSource, behandling)

        assertThat(uthentet!!.endringer()).hasSize(1)

        dataSource.transaction {
            PostmottakBehandlingRepository(it).oppdaterBehandling(
                uthentet.referanse, PostmottakOppdatering(
                    gjeldende = true,
                    status = "enstatus",
                    oppdatertTid = LocalDateTime.now(),
                    sisteSaksbehandler = "ny saksbehandler",
                    gjeldendeAvklaringsBehov = "avkl"
                )
            )
        }

        val oppdatert = hentUtBehandling(dataSource, behandling)!!

        assertThat(oppdatert.endringer()).hasSize(2)
        val gjeldendeOppdatering = oppdatert.endringer().find { it.gjeldende }!!
        assertThat(gjeldendeOppdatering.status).isEqualTo("enstatus")
        assertThat(gjeldendeOppdatering.sisteSaksbehandler).isEqualTo("ny saksbehandler")
        assertThat(gjeldendeOppdatering.gjeldendeAvklaringsBehov).isEqualTo("avkl")
    }

    private fun hentUtBehandling(
        dataSource: DataSource,
        behandling: PostmottakBehandling
    ) = dataSource.transaction {
        PostmottakBehandlingRepository(it).hentEksisterendeBehandling(behandling.referanse)
    }

    private fun opprettBehandling(
        dataSource: DataSource, postmottakBehandling: PostmottakBehandling
    ) = dataSource.transaction {
        val personMedId =
            PersonService(PersonRepository(it)).hentEllerLagrePerson(postmottakBehandling.person.ident)
        postmottakBehandling.person.settId(personMedId.id()!!)
        val id = PostmottakBehandlingRepository(it).opprettBehandling(
            postmottakBehandling
        )
        postmottakBehandling.medId(id)
    }
}
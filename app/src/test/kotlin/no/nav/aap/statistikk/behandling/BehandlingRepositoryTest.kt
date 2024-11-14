package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class BehandlingRepositoryTest {
    @Test
    fun `sette inn og hente ut igjen`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789", person)

        val referanse = UUID.randomUUID()

        dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    relaterteIdenter = listOf("123", "456", "123456789"),
                    gjeldendeAvklaringsBehov = "0559",
                    søknadsformat = SøknadsFormat.PAPIR
                )
            )
        }

        val uthentet = dataSource.transaction { BehandlingRepository(it).hent(referanse) }

        uthentet!!
        assertThat(uthentet.status).isEqualTo(BehandlingStatus.UTREDES)
        assertThat(uthentet.sak.sakStatus).isEqualTo(SakStatus.UTREDES)
        assertThat(uthentet.relaterteIdenter).containsExactlyInAnyOrder("123", "456", "123456789")
        assertThat(uthentet.gjeldendeAvklaringsBehov).isEqualTo("0559")
        assertThat(uthentet.søknadsformat).isEqualTo(SøknadsFormat.PAPIR)
    }

    @Test
    fun `lagre to ganger med eksisterende versjon`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789", person)

        val referanse = UUID.randomUUID()
        dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    relaterteIdenter = listOf(),
                    søknadsformat = SøknadsFormat.PAPIR
                )
            )
        }

        val referanse2 = UUID.randomUUID()
        dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse2,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    relaterteIdenter = listOf(),
                    søknadsformat = SøknadsFormat.PAPIR
                )
            )
        }

        dataSource.transaction {
            assertThat(BehandlingRepository(it).hent(referanse)).isNotNull()
            assertThat(BehandlingRepository(it).hent(referanse2)).isNotNull()
        }
    }

    @Test
    fun `lagre oppdatert behandling, henter ut nyeste info`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789", person)

        val referanse = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR
                )
            )
        }
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId,
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx2"),
                    søknadsformat = SøknadsFormat.DIGITAL
                )
            )
        }

        dataSource.transaction {
            assertThat(BehandlingRepository(it).hent(referanse)).isNotNull()
        }
    }

    @Test
    fun `telle antall fullførte behandlinger`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789", person)

        val referanse = UUID.randomUUID()
        val referanse2 = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.PAPIR
                )
            )
        }
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId,
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.AVSLUTTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx2"),
                    søknadsformat = SøknadsFormat.PAPIR
                )
            )
        }

        val behandlingId2 = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse2,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.OPPRETTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    søknadsformat = SøknadsFormat.DIGITAL
                )
            )
        }
        dataSource.transaction {
            BehandlingRepository(it).oppdaterBehandling(
                Behandling(
                    id = behandlingId2,
                    referanse = referanse2,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.AVSLUTTET,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx2"),
                    søknadsformat = SøknadsFormat.DIGITAL
                )
            )
        }

        val antall = dataSource.transaction {
            BehandlingRepository(it).tellFullførteBehandlinger()
        }

        assertThat(antall).isEqualTo(2)
    }

    @Test
    fun `prøve å sette inn flere med gjeldende = true skal feile`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789", person)

        val referanse = UUID.randomUUID()

        val id = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("xxx"),
                    relaterteIdenter = listOf("123", "456", "123456789"),
                    gjeldendeAvklaringsBehov = "0559",
                    søknadsformat = SøknadsFormat.PAPIR
                )
            )
        }

        org.junit.jupiter.api.assertThrows<Exception> {
            dataSource.transaction {
                it.execute("INSERT INTO behandling_historikk (behandling_id, versjon_id, gjeldende, oppdatert_tid, mottatt_tid, status, siste_saksbehandler, gjeldende_avklaringsbehov) VALUES (?,?,?,?,?,?,?,?)") {
                    setParams {
                        setLong(1, id)
                        setLong(2, 1)
                        setBoolean(3, true)
                        setLocalDateTime(4, LocalDateTime.now())
                        setLocalDateTime(5, LocalDateTime.now())
                        setString(6, BehandlingStatus.UTREDES.name)
                        setString(7, BehandlingStatus.UTREDES.name)
                        setString(8, BehandlingStatus.UTREDES.name)
                    }
                }
            }
        }
    }
}
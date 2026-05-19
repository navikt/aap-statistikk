package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.forberedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class SamordningRepositoryImplTest {
    @Test
    fun `lagre og hent igjen - alle typer`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }
        val now = LocalDate.now()

        val samordning = Samordning(
            uføre = listOf(
                Samordning.UførePeriode(fom = now.minusMonths(6), tom = now.minusMonths(3), grad = 50),
            ),
            statligeYtelser = listOf(
                Samordning.StatligYtelse(
                    fom = now.minusMonths(4),
                    tom = now.minusMonths(2),
                    ytelse = Samordning.SamordningYtelse.SYKEPENGER,
                    prosent = 75,
                ),
            ),
            avregningAndreYtelser = listOf(
                Samordning.AvregningAndreYtelse(
                    fom = now.minusMonths(3),
                    tom = now.minusMonths(1),
                    ytelse = Samordning.AndreStatligeYtelse.DAGPENGER,
                ),
            ),
            arbeidsgiver = listOf(
                Samordning.Arbeidsgiver(fom = now.minusWeeks(4), tom = now.minusWeeks(2)),
            ),
        )

        dataSource.transaction {
            SamordningRepositoryImpl(it).lagre(behandlingId, samordning)
        }

        val hentet = dataSource.transaction {
            SamordningRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(hentet).isNotNull
        assertThat(hentet!!.uføre).isEqualTo(samordning.uføre)
        assertThat(hentet.statligeYtelser).isEqualTo(samordning.statligeYtelser)
        assertThat(hentet.avregningAndreYtelser).isEqualTo(samordning.avregningAndreYtelser)
        assertThat(hentet.arbeidsgiver).isEqualTo(samordning.arbeidsgiver)
    }

    @Test
    fun `tom samordning lagres og hentes`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val samordning = Samordning(
            uføre = emptyList(),
            statligeYtelser = emptyList(),
            avregningAndreYtelser = emptyList(),
            arbeidsgiver = emptyList(),
        )

        dataSource.transaction {
            SamordningRepositoryImpl(it).lagre(behandlingId, samordning)
        }

        val hentet = dataSource.transaction {
            SamordningRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(hentet).isNotNull
        assertThat(hentet!!.uføre).isEmpty()
        assertThat(hentet.statligeYtelser).isEmpty()
        assertThat(hentet.avregningAndreYtelser).isEmpty()
        assertThat(hentet.arbeidsgiver).isEmpty()
    }

    @Test
    fun `skal overskrive eksisterende data ved ny lagring`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }
        val now = LocalDate.now()

        val første = Samordning(
            uføre = listOf(Samordning.UførePeriode(fom = now.minusMonths(3), tom = now.minusMonths(1), grad = 30)),
            statligeYtelser = emptyList(),
            avregningAndreYtelser = emptyList(),
            arbeidsgiver = emptyList(),
        )

        dataSource.transaction {
            SamordningRepositoryImpl(it).lagre(behandlingId, første)
        }

        val oppdatert = Samordning(
            uføre = emptyList(),
            statligeYtelser = listOf(
                Samordning.StatligYtelse(
                    fom = now.minusMonths(2),
                    tom = now,
                    ytelse = Samordning.SamordningYtelse.FORELDREPENGER,
                    prosent = 100,
                )
            ),
            avregningAndreYtelser = emptyList(),
            arbeidsgiver = emptyList(),
        )

        dataSource.transaction {
            SamordningRepositoryImpl(it).lagre(behandlingId, oppdatert)
        }

        val hentet = dataSource.transaction {
            SamordningRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(hentet).isNotNull
        assertThat(hentet!!.uføre).isEmpty()
        assertThat(hentet.statligeYtelser).isEqualTo(oppdatert.statligeYtelser)
    }

    @Test
    fun `hent returnerer null for ikke-eksisterende behandling`(@Postgres dataSource: DataSource) {
        val ikkeEksisterendeBehandlingId = BehandlingId(999999L)

        val res = dataSource.transaction {
            SamordningRepositoryImpl(it).hent(ikkeEksisterendeBehandlingId)
        }

        assertThat(res).isNull()
    }
}

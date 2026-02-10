package no.nav.aap.statistikk.meldekort

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestBehandling
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class FritaksvurderingRepositoryImplTest {

    @Test
    fun `Lagre og hente ut igjen fritaksvurdering data`(@Postgres dataSource: DataSource) {
        val behandlingRef = UUID.randomUUID()
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, Saksnummer("123456789"), person)
        val behandling = opprettTestBehandling(dataSource, behandlingRef, sak)

        val vurderinger = listOf(
            Fritakvurdering(
                harFritak = true,
                fraDato = LocalDate.of(2024, 1, 1),
                tilDato = LocalDate.of(2024, 12, 31)
            ),
            Fritakvurdering(
                harFritak = false,
                fraDato = LocalDate.of(2025, 1, 1),
                tilDato = null
            )
        )

        dataSource.transaction {
            FritaksvurderingRepositoryImpl(it).lagre(
                behandlingId = behandling.id(),
                vurderinger = vurderinger
            )
        }

        val uthentet = dataSource.transaction {
            FritaksvurderingRepositoryImpl(it).hentFritaksvurderinger(
                behandlingId = behandling.id()
            )
        }

        assertThat(uthentet).hasSize(2)
        assertThat(uthentet).isEqualTo(vurderinger)
    }
}

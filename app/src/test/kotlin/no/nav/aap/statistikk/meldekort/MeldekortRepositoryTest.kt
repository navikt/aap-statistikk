package no.nav.aap.statistikk.meldekort

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.opprettTestPerson
import no.nav.aap.statistikk.testutils.opprettTestSak
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals

class MeldekortRepositoryTest {

    @Test
    fun `Lagre og hente ut igjen meldekort data`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".let(::Saksnummer), person)

        val meldekort = listOf(
            Meldekort(
                journalpostId = "JP123456",
                arbeidIPeriodeDTO = listOf()
            ),
            Meldekort(
                journalpostId = "JP654321",
                arbeidIPeriodeDTO = listOf(
                    ArbeidIPerioder(
                        periodeFom = LocalDate.of(2024, 1, 1),
                        periodeTom = LocalDate.of(2024, 1, 7),
                        timerArbeidet = BigDecimal(20)
                    )
                )
            )
        )
        val behandling = Behandling(
            referanse = UUID.randomUUID(),
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = LocalDateTime.now(),
            oppdatertTidspunkt = LocalDateTime.now(),
            mottattTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.PAPIR,
            hendelser = listOf()
        )

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                behandling
            )
        }


        dataSource.transaction {
            MeldekortRepository(it).lagre(
                behandlingId = behandlingId,
                meldekort = meldekort
            )
        }

        val uthentet = dataSource.transaction {
            MeldekortRepository(it).hentMeldekortperioder(
                behandlingId = behandlingId
            )
        }

        assertEquals(uthentet.size,meldekort.size)
        assertEquals(uthentet[0].journalpostId, meldekort[0].journalpostId)
        assert(uthentet[1].arbeidIPeriodeDTO.get(0).timerArbeidet.toInt() == meldekort[1].arbeidIPeriodeDTO.get(0).timerArbeidet.toInt())
    }
}
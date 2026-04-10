package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.forberedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class VedtattStansOpphørRepositoryImplTest {
    @Test
    fun `lagre og hent igjen`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val stansOpphørListe = listOf(
            StansEllerOpphør(
                type = StansType.STANS,
                fom = LocalDate.of(2024, 1, 1),
                årsaker = setOf(Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS)
            ),
            StansEllerOpphør(
                type = StansType.OPPHØR,
                fom = LocalDate.of(2024, 6, 1),
                årsaker = setOf(
                    Avslagsårsak.BRUDD_PÅ_OPPHOLDSKRAV_OPPHØR,
                    Avslagsårsak.MANGLENDE_DOKUMENTASJON
                )
            )
        )

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, stansOpphørListe)
        }

        val resultat = dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(resultat).hasSize(2)
        assertThat(resultat).usingRecursiveComparison().isEqualTo(stansOpphørListe)
    }

    @Test
    fun `tom liste sletter eksisterende data`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        val stansOpphørListe = listOf(
            StansEllerOpphør(
                type = StansType.STANS,
                fom = LocalDate.of(2024, 1, 1),
                årsaker = setOf(Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS)
            )
        )

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, stansOpphørListe)
        }

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, emptyList())
        }

        val resultat = dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(resultat).isEmpty()
    }

    @Test
    fun `overskriv eksisterende data ved ny lagring`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(
                behandlingId,
                listOf(
                    StansEllerOpphør(
                        type = StansType.STANS,
                        fom = LocalDate.of(2024, 1, 1),
                        årsaker = setOf(Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS)
                    )
                )
            )
        }

        val oppdatertListe = listOf(
            StansEllerOpphør(
                type = StansType.OPPHØR,
                fom = LocalDate.of(2024, 3, 1),
                årsaker = setOf(Avslagsårsak.ORDINÆRKVOTE_BRUKT_OPP)
            )
        )

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, oppdatertListe)
        }

        val resultat = dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).hent(behandlingId)
        }

        assertThat(resultat).hasSize(1)
        assertThat(resultat).usingRecursiveComparison().isEqualTo(oppdatertListe)
    }

    @Test
    fun `hent returnerer null for ikke-eksisterende behandling`(@Postgres dataSource: DataSource) {
        val ikkeEksisterendeBehandlingId = BehandlingId(999999L)

        val resultat = dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).hent(ikkeEksisterendeBehandlingId)
        }

        assertThat(resultat).isNull()
    }
}

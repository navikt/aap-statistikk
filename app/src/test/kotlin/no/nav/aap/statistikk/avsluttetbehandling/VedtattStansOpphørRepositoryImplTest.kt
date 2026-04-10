package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.transaction
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
        val behandlingId = dataSource.transaction { forberedDatabase(it, UUID.randomUUID()) }

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

        dataSource.transaction { VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, stansOpphørListe) }

        val resultat = dataSource.transaction { VedtattStansOpphørRepositoryImpl(it).hent(behandlingId) }

        assertThat(resultat).usingRecursiveComparison().isEqualTo(stansOpphørListe)
    }

    @Test
    fun `tom liste sletter eksisterende data`(@Postgres dataSource: DataSource) {
        val behandlingId = dataSource.transaction { forberedDatabase(it, UUID.randomUUID()) }

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(
                behandlingId,
                listOf(StansEllerOpphør(StansType.STANS, LocalDate.of(2024, 1, 1), setOf(Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS)))
            )
        }
        dataSource.transaction { VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, emptyList()) }

        val resultat = dataSource.transaction { VedtattStansOpphørRepositoryImpl(it).hent(behandlingId) }

        assertThat(resultat).isEmpty()
    }

    @Test
    fun `overskriv eksisterende data ved ny lagring`(@Postgres dataSource: DataSource) {
        val behandlingId = dataSource.transaction { forberedDatabase(it, UUID.randomUUID()) }

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(
                behandlingId,
                listOf(StansEllerOpphør(StansType.STANS, LocalDate.of(2024, 1, 1), setOf(Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS)))
            )
        }

        val oppdatert = listOf(
            StansEllerOpphør(StansType.OPPHØR, LocalDate.of(2024, 3, 1), setOf(Avslagsårsak.ORDINÆRKVOTE_BRUKT_OPP))
        )
        dataSource.transaction { VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, oppdatert) }

        val resultat = dataSource.transaction { VedtattStansOpphørRepositoryImpl(it).hent(behandlingId) }

        assertThat(resultat).usingRecursiveComparison().isEqualTo(oppdatert)
    }
}

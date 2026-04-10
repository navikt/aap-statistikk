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
    fun `lagre to perioder og verifiser i databasen`(@Postgres dataSource: DataSource) {
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

        val antallPerioder = dataSource.transaction { conn ->
            conn.queryFirst("SELECT COUNT(*) AS count FROM vedtatt_stans_opphor WHERE behandling_id = ?") {
                setParams { setLong(1, behandlingId.id) }
                setRowMapper { it.getInt("count") }
            }
        }
        assertThat(antallPerioder).isEqualTo(2)

        val antallÅrsaker = dataSource.transaction { conn ->
            conn.queryFirst(
                """
                SELECT COUNT(*) AS count FROM vedtatt_stans_opphor_aarsak a
                JOIN vedtatt_stans_opphor s ON a.vedtatt_stans_opphor_id = s.id
                WHERE s.behandling_id = ?
                """.trimIndent()
            ) {
                setParams { setLong(1, behandlingId.id) }
                setRowMapper { it.getInt("count") }
            }
        }
        assertThat(antallÅrsaker).isEqualTo(3)
    }

    @Test
    fun `tom liste sletter eksisterende data`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(
                behandlingId,
                listOf(StansEllerOpphør(StansType.STANS, LocalDate.of(2024, 1, 1), setOf(Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS)))
            )
        }
        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(behandlingId, emptyList())
        }

        val antall = dataSource.transaction { conn ->
            conn.queryFirst("SELECT COUNT(*) AS count FROM vedtatt_stans_opphor WHERE behandling_id = ?") {
                setParams { setLong(1, behandlingId.id) }
                setRowMapper { it.getInt("count") }
            }
        }
        assertThat(antall).isEqualTo(0)
    }

    @Test
    fun `overskriv eksisterende data ved ny lagring`(@Postgres dataSource: DataSource) {
        val behandlingReferanse = UUID.randomUUID()
        val behandlingId = dataSource.transaction { forberedDatabase(it, behandlingReferanse) }

        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(
                behandlingId,
                listOf(StansEllerOpphør(StansType.STANS, LocalDate.of(2024, 1, 1), setOf(Avslagsårsak.BRUDD_PÅ_AKTIVITETSPLIKT_STANS)))
            )
        }
        dataSource.transaction {
            VedtattStansOpphørRepositoryImpl(it).lagre(
                behandlingId,
                listOf(StansEllerOpphør(StansType.OPPHØR, LocalDate.of(2024, 3, 1), setOf(Avslagsårsak.ORDINÆRKVOTE_BRUKT_OPP)))
            )
        }

        val typer = dataSource.transaction { conn ->
            conn.queryList("SELECT type FROM vedtatt_stans_opphor WHERE behandling_id = ?") {
                setParams { setLong(1, behandlingId.id) }
                setRowMapper { it.getString("type") }
            }
        }
        assertThat(typer).containsExactly("OPPHØR")
    }
}

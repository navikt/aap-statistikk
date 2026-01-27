package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.behandling.BehandlingId
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

private val log = LoggerFactory.getLogger(VilkårsresultatRepository::class.java)

class VilkårsresultatRepository(
    private val dbConnection: DBConnection
) : IVilkårsresultatRepository {
    companion object : RepositoryFactory<IVilkårsresultatRepository> {
        override fun konstruer(connection: DBConnection): IVilkårsresultatRepository {
            return VilkårsresultatRepository(connection)
        }
    }

    override fun lagreVilkårsResultat(
        vilkårsresultat: VilkårsResultatEntity,
        behandlingId: BehandlingId
    ): Long {
        val deletePeriodeSql = """
            DELETE
            FROM VILKARSPERIODE
            WHERE vilkar_id IN (SELECT v.id
                                FROM VILKAR v
                                         JOIN VILKARSRESULTAT vr ON v.vilkarresult_id = vr.id
                                WHERE vr.behandling_id = ?)
        """.trimIndent()
        val deleteVilkarSql = """
            DELETE FROM VILKAR WHERE vilkarresult_id IN (
                SELECT id FROM VILKARSRESULTAT WHERE behandling_id = ?
            )
        """.trimIndent()
        val deleteResultatSql = "DELETE FROM VILKARSRESULTAT WHERE behandling_id = ?"

        dbConnection.execute(deletePeriodeSql) { setParams { setLong(1, behandlingId.id) } }
        dbConnection.execute(deleteVilkarSql) { setParams { setLong(1, behandlingId.id) } }
        dbConnection.execute(deleteResultatSql) { setParams { setLong(1, behandlingId.id) } }

        val sqlInsertResultat =
            """INSERT INTO VILKARSRESULTAT (behandling_id) VALUES (?)"""

        val uthentetId = dbConnection.executeReturnKey(sqlInsertResultat) {
            setParams {
                setLong(1, behandlingId.id)
            }
        }

        val sqlInsertVilkar = """INSERT INTO VILKAR(vilkar_type, vilkarresult_id)
VALUES (?, ?);
            """

        val sqlInsertPeriode =
            """INSERT INTO VILKARSPERIODE(fra_dato, til_dato, utfall, manuell_vurdering, innvilgelsesaarsak,
                           avslagsaarsak, vilkar_id)
VALUES (?, ?, ?, ?, ?, ?, ?);
                """

        vilkårsresultat.vilkår.forEach { vilkårEntity ->
            val vilkårKey = dbConnection.executeReturnKey(sqlInsertVilkar) {
                setParams {
                    setString(1, vilkårEntity.vilkårType)
                    setLong(2, uthentetId)
                }
            }

            dbConnection.executeBatch(sqlInsertPeriode, vilkårEntity.perioder) {
                setParams { periode ->
                    setLocalDate(1, periode.fraDato)
                    setLocalDate(2, periode.tilDato)
                    setString(3, periode.utfall)
                    setBoolean(4, periode.manuellVurdering)
                    setString(5, periode.innvilgelsesårsak)
                    setString(6, periode.avslagsårsak)
                    setLong(7, vilkårKey)
                }
            }
        }
        log.info("Satte inn vilkårsresulat med db ID: $uthentetId")

        return uthentetId
    }

    data class EkstraInfo(
        val id: Long,
        val saksnummer: String,
        val type: String,
        val referanse: String
    )

    override fun hentVilkårsResultat(vilkårResultatId: Long): VilkårsResultatEntity {
        val preparedSqlStatement = """
SELECT vr.id         as vr_id,
       s.saksnummer  as s_saksnummer,
       b.type        as b_type,
       br.referanse  as br_referanse,
       v.id             v_id,
       v.vilkar_type as v_vilkar_type
FROM VILKARSRESULTAT vr
         LEFT JOIN VILKAR v ON vr.id = v.vilkarresult_id
         LEFT JOIN behandling b on vr.behandling_id = b.id
         LEFT JOIN behandling_referanse br on b.referanse_id = br.id
         LEFT JOIN sak s on s.id = b.sak_id
WHERE vr.id = ?;
            """

        val vilkarsResultatList = dbConnection.queryList(preparedSqlStatement) {
            setParams { setLong(1, vilkårResultatId) }
            setRowMapper(mapVilkår())
        }

        return VilkårsResultatEntity(
            id = vilkarsResultatList.first().first.id,
            vilkår = vilkarsResultatList.mapNotNull { it.second },
        )
    }

    override fun hentForBehandling(behandlingsReferanse: UUID): VilkårsResultatEntity {
        val sql = """
SELECT vr.id         as vr_id,
       s.saksnummer  as s_saksnummer,
       b.type        as b_type,
       br.referanse  as br_referanse,
       v.id             v_id,
       v.vilkar_type as v_vilkar_type
FROM VILKARSRESULTAT vr
         LEFT JOIN VILKAR v ON vr.id = v.vilkarresult_id
         LEFT JOIN behandling b on vr.behandling_id = b.id
         LEFT JOIN behandling_referanse br on b.referanse_id = br.id
         LEFT JOIN sak s on s.id = b.sak_id
WHERE br.referanse = ?;            
        """.trimIndent()

        val resultList = dbConnection.queryList(sql) {
            setParams { setUUID(1, behandlingsReferanse) }
            setRowMapper(mapVilkår())
        }

        return VilkårsResultatEntity(
            id = resultList.first().first.id,
            vilkår = resultList.mapNotNull { it.second },
        )
    }

    private fun mapVilkår(): (Row) -> Pair<EkstraInfo, VilkårEntity?> = {
        val id = it.getLong("vr_id")
        val saksNummer = it.getString("s_saksnummer")
        val typeBehandling = it.getString("b_type")
        val behandlingsReferanse = it.getString("br_referanse")

        val vilkårId = it.getLongOrNull("v_id")
        val vilkårType = it.getStringOrNull("v_vilkar_type")

        val vilkårPerioder = vilkårId?.let { getVilkårPerioder(dbConnection, vilkårId) }.orEmpty()
        val vilkårEntity = vilkårId?.let {
            VilkårEntity(
                id = vilkårId,
                vilkårType = requireNotNull(vilkårType) { "VilkårType må være satt når vilkårId finnes. Vilkår-ID: $vilkårId." },
                perioder = vilkårPerioder
            )
        }

        Pair(
            EkstraInfo(
                id = id,
                saksnummer = saksNummer,
                type = typeBehandling,
                referanse = behandlingsReferanse
            ), vilkårEntity
        )
    }

    private fun getVilkårPerioder(
        dbConnection: DBConnection,
        vilkårId: Long,
    ): List<VilkårsPeriodeEntity> {
        val sql = """SELECT *
FROM VILKARSPERIODE
WHERE vilkar_id = ?;
            """
        return dbConnection.queryList(sql) {
            setParams {
                setLong(1, vilkårId)
            }
            setRowMapper {
                VilkårsPeriodeEntity(
                    id = it.getLong("id"),
                    fraDato = it.getLocalDate("fra_dato"),
                    tilDato = it.getLocalDate("til_dato"),
                    utfall = it.getString("utfall"),
                    manuellVurdering = it.getBoolean("manuell_vurdering"),
                    innvilgelsesårsak = it.getStringOrNull("innvilgelsesaarsak"),
                    avslagsårsak = it.getStringOrNull("avslagsaarsak")
                )
            }
        }
    }
}


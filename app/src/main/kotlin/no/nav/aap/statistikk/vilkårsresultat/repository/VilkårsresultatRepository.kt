package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.behandling.BehandlingId
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger(VilkårsresultatRepository::class.java)

class VilkårsresultatRepository(
    private val dbConnection: DBConnection
) : IVilkårsresultatRepository {
    override fun lagreVilkårsResultat(
        vilkårsresultat: VilkårsResultatEntity,
        behandlingId: BehandlingId
    ): Long {
        val sqlInsertResultat =
            """INSERT INTO VILKARSRESULTAT (behandling_id) VALUES (?)"""

        val uthentetId = dbConnection.executeReturnKey(sqlInsertResultat) {
            setParams {
                setLong(1, behandlingId.id)
            }
        }

        val sqlInsertVilkar = """
                INSERT INTO VILKAR(vilkar_type, vilkarresult_id) VALUES(?, ?);
            """

        val sqlInsertPeriode = """
                    INSERT INTO VILKARSPERIODE(fra_dato,til_dato,utfall,manuell_vurdering,innvilgelsesaarsak,avslagsaarsak,vilkar_id)
                    VALUES(?,?,?,?,?,?,?);
                """

        vilkårsresultat.vilkår.forEach { vilkårEntity ->
            val vilkårKey = dbConnection.executeReturnKey(sqlInsertVilkar) {
                setParams {
                    setString(1, vilkårEntity.vilkårType)
                    setLong(2, uthentetId)
                }
            }

            vilkårEntity.perioder.forEach { periode ->
                dbConnection.execute(sqlInsertPeriode) {
                    setParams {
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

        val xx = dbConnection.queryList(preparedSqlStatement) {
            setParams { setLong(1, vilkårResultatId) }
            setRowMapper(mapVilkår())
        }

        return VilkårsResultatEntity(
            id = xx.first().first.id,
            vilkår = xx.map { it.second },
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

        val xx = dbConnection.queryList(sql) {
            setParams { setUUID(1, behandlingsReferanse) }
            setRowMapper(mapVilkår())
        }

        return VilkårsResultatEntity(
            id = xx.first().first.id,
            vilkår = xx.map { it.second },
        )
    }

    private fun mapVilkår(): (Row) -> Pair<EkstraInfo, VilkårEntity> = {
        val id = it.getLong("vr_id")
        val saksNummer = it.getString("s_saksnummer")
        val typeBehandling = it.getString("b_type")
        val behandlingsReferanse = it.getString("br_referanse")

        val vilkårId = it.getLong("v_id")
        val vilkårType = it.getString("v_vilkar_type")

        val vilkårPerioder = getVilkårPerioder(dbConnection, vilkårId)
        val vilkårEntity =
            VilkårEntity(id = vilkårId, vilkårType = vilkårType, perioder = vilkårPerioder)

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


package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.komponenter.dbconnect.DBConnection
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger(VilkårsresultatRepository::class.java)

class VilkårsresultatRepository(
    private val dbConnection: DBConnection
) : IVilkårsresultatRepository {
    override fun lagreVilkårsResultat(vilkårsresultat: VilkårsResultatEntity): Int {
        val sqlInsertResultat =
            """
    WITH inserted AS (
        INSERT INTO VILKARSRESULTAT (behandling_id)
        SELECT b.id AS behandling_id
        FROM BEHANDLING b
        JOIN SAK s ON b.sak_id = s.id
        WHERE b.referanse = ?
          AND s.saksnummer = ?
        RETURNING *
    )
    SELECT id FROM inserted"""

        val saksnummer = vilkårsresultat.saksnummer
        val behandlingsReferanse = vilkårsresultat.behandlingsReferanse

        val uthentetId = requireNotNull(dbConnection.queryFirstOrNull<Int>(sqlInsertResultat) {
            setParams {
                setUUID(1, UUID.fromString(behandlingsReferanse))
                setString(2, saksnummer)
            }
            setRowMapper {
                it.getInt("id")
            }
        }) { "Kunne ikke skrive vilkårsresultat. Behandling-ref: $behandlingsReferanse. Saksnummer: $saksnummer" }


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
                    setInt(2, uthentetId)
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

    override fun hentVilkårsResultat(vilkårResultatId: Int): VilkårsResultatEntity? {
        val preparedSqlStatement = """
SELECT vr.id         as vr_id,
       s.saksnummer  as s_saksnummer,
       b.type        as b_type,
       b.referanse   as b_referanse,
       v.id             v_id,
       v.vilkar_type as v_vilkar_type
FROM VILKARSRESULTAT vr
         LEFT JOIN VILKAR v ON vr.id = v.vilkarresult_id
         LEFT JOIN behandling b on vr.behandling_id = b.id
         LEFT JOIN sak s on s.id = b.sak_id
WHERE vr.id = ?;
            """

        data class EkstraInfo(
            val id: Long,
            val saksnummer: String,
            val type: String,
            val referanse: String
        )

        val xx = dbConnection.queryList<Pair<EkstraInfo, VilkårEntity>>(preparedSqlStatement) {
            setParams { setInt(1, vilkårResultatId) }
            setRowMapper {
                val id = it.getLong("vr_id")
                val saksNummer = it.getString("s_saksnummer")
                val typeBehandling = it.getString("b_type")
                val behandlingsReferanse = it.getString("b_referanse")

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
        }

        return VilkårsResultatEntity(
            id = xx.first().first.id,
            behandlingsReferanse = xx.first().first.referanse,
            saksnummer = xx.first().first.saksnummer,
            typeBehandling = xx.first().first.type,
            vilkår = xx.map { it.second },
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
        return dbConnection.queryList<VilkårsPeriodeEntity>(sql) {
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


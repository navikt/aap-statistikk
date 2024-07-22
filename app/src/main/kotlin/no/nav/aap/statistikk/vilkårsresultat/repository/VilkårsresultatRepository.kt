package no.nav.aap.statistikk.vilkårsresultat.repository

import no.nav.aap.statistikk.db.withinTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import javax.sql.DataSource

private val log = LoggerFactory.getLogger(VilkårsresultatRepository::class.java)

class VilkårsresultatRepository(private val dataSource: DataSource) : IVilkårsresultatRepository {
    override fun lagreVilkårsResultat(vilkårsresultat: VilkårsResultatEntity): Int {

        return dataSource.withinTransaction { connection ->
            val sqlInsertResultat = """INSERT INTO VILKARSRESULTAT(saksnummer, behandlingsreferanse, type_behandling)
VALUES (?, ?, ?);
        """
            val preparedStatement =
                connection.prepareStatement(sqlInsertResultat, Statement.RETURN_GENERATED_KEYS).apply {
                    setString(1, vilkårsresultat.saksnummer)
                    setObject(2, vilkårsresultat.behandlingsReferanse, Types.OTHER)
                    setString(3, vilkårsresultat.typeBehandling)
                    executeUpdate()
                }

            val vilkårResultId = hentGenerertNøkkel(preparedStatement)

            val sqlInsertVilkar = """
                INSERT INTO VILKAR(vilkar_type, vilkarresult_id) VALUES(?, ?);
            """

            vilkårsresultat.vilkår.forEach { vilkarDTO ->

                val resultat = connection.prepareStatement(sqlInsertVilkar, Statement.RETURN_GENERATED_KEYS).apply {
                    setString(1, vilkarDTO.vilkårType)
                    setInt(2, vilkårResultId)
                    executeUpdate()
                }

                val vilkårKey = hentGenerertNøkkel(resultat)

                vilkarDTO.perioder.forEach { periode ->
                    val sqlInsertPeriode = """
                    INSERT INTO VILKARSPERIODE(fra_dato,til_dato,utfall,manuell_vurdering,innvilgelsesaarsak,avslagsaarsak,vilkar_id)
                    VALUES(?,?,?,?,?,?,?);
                """
                    connection.prepareStatement(sqlInsertPeriode, Statement.RETURN_GENERATED_KEYS).apply {
                        setDate(1, java.sql.Date.valueOf(periode.fraDato))
                        setDate(2, java.sql.Date.valueOf(periode.tilDato))
                        setString(3, periode.utfall)
                        setBoolean(4, periode.manuellVurdering)
                        setString(5, periode.innvilgelsesårsak)
                        setString(6, periode.avslagsårsak)
                        setInt(7, vilkårKey)
                        executeUpdate()
                    }
                }
            }
            log.info("Satte inn vilkårsresulat med db ID: $vilkårResultId")
            vilkårResultId
        }
    }


    private fun hentGenerertNøkkel(preparedStatement: PreparedStatement): Int {
        val resultSet = preparedStatement.generatedKeys
        resultSet.next()
        val vilkårResultId = resultSet.getInt(1)
        return vilkårResultId
    }

    override fun hentVilkårsResultat(vilkårResultatId: Int): VilkårsResultatEntity? {
        return dataSource.withinTransaction { connection ->
            val preparedStatement = createPreparedStatement(connection, vilkårResultatId)
            val resultSet = preparedStatement.executeQuery()

            if (resultSet.next()) {
                processResultSet(resultSet)
            } else {
                null
            }
        }
    }

    private fun createPreparedStatement(connection: Connection, vilkårResultatId: Int): PreparedStatement {
        val preparedSqlStatement = """
            SELECT * FROM VILKARSRESULTAT 
            LEFT JOIN VILKAR ON VILKARSRESULTAT.id = VILKAR.vilkarresult_id
            LEFT JOIN VILKARSPERIODE ON VILKAR.id = VILKARSPERIODE.vilkar_id
            WHERE VILKARSRESULTAT.id = ?;
            """
        val preparedStatement = connection.prepareStatement(preparedSqlStatement)
        preparedStatement.setInt(1, vilkårResultatId)
        return preparedStatement
    }

    private fun processResultSet(resultSet: ResultSet): VilkårsResultatEntity {
        val vilkårList = mutableListOf<VilkårEntity>()
        val id = resultSet.getLong("id")
        val saksNummer = resultSet.getString("saksnummer")
        val typeBehandling = resultSet.getString("type_behandling")
        val behandlingsReferanse = resultSet.getString("behandlingsreferanse")

        do {
            val vilkårId = resultSet.getLong("id")
            val vilkårType = resultSet.getString("vilkar_type")
            val vilkårPerioder = getVilkårPerioder(resultSet, vilkårType)
            val vilkårEntity = VilkårEntity(id = vilkårId, vilkårType = vilkårType, perioder = vilkårPerioder)
            vilkårList.add(vilkårEntity)
        } while (!resultSet.isAfterLast)

        return VilkårsResultatEntity(
            id = id,
            saksnummer = saksNummer,
            behandlingsReferanse = behandlingsReferanse,
            typeBehandling = typeBehandling,
            vilkår = vilkårList
        )
    }

    private fun getVilkårPerioder(resultSet: ResultSet, vilkårType: String): MutableList<VilkårsPeriodeEntity> {
        val vilkårPerioder = mutableListOf<VilkårsPeriodeEntity>()
        do {
            val vilkårsPeriodeDTO = resultSetTilVilkårsPeriode(resultSet)
            vilkårPerioder.add(vilkårsPeriodeDTO)
        } while (resultSet.next() && resultSet.getString("vilkar_type") == vilkårType)

        return vilkårPerioder
    }
}

fun resultSetTilVilkårsPeriode(resultSet: ResultSet): VilkårsPeriodeEntity {
    return VilkårsPeriodeEntity(
        id = resultSet.getLong("id"),
        fraDato = resultSet.getDate("fra_dato").toLocalDate(),
        tilDato = resultSet.getDate("til_dato").toLocalDate(),
        utfall = resultSet.getString("utfall"),
        manuellVurdering = resultSet.getBoolean("manuell_vurdering"),
        innvilgelsesårsak = resultSet.getString("innvilgelsesaarsak"),
        avslagsårsak = resultSet.getString("avslagsaarsak")
    )
}


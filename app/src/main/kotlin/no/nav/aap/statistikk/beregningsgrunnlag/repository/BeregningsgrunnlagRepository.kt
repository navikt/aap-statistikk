package no.nav.aap.statistikk.beregningsgrunnlag.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.aap.statistikk.api_kontrakt.UføreType
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.db.hentGenerertNøkkel
import no.nav.aap.statistikk.db.withinTransaction
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import javax.sql.DataSource


interface IBeregningsgrunnlagRepository {
    fun lagreBeregningsGrunnlag(beregningsGrunnlag: IBeregningsGrunnlag): Int
    fun hentBeregningsGrunnlag(): List<IBeregningsGrunnlag>
}

class BeregningsgrunnlagRepository(private val dataSource: DataSource) :
    IBeregningsgrunnlagRepository {
    override fun lagreBeregningsGrunnlag(beregningsGrunnlag: IBeregningsGrunnlag): Int {
        return dataSource.withinTransaction {
            val baseGrunnlagId = lagreBaseGrunnlag(it, beregningsGrunnlag.type())

            when (beregningsGrunnlag) {
                is IBeregningsGrunnlag.Grunnlag_11_19 -> {
                    lagre11_19(it, baseGrunnlagId, beregningsGrunnlag)
                }

                is IBeregningsGrunnlag.GrunnlagYrkesskade -> {
                    lagreGrunnlagYrkesskade(it, baseGrunnlagId, beregningsGrunnlag)
                }

                is IBeregningsGrunnlag.GrunnlagUføre ->
                    lagreGrunnlagUføre(it, baseGrunnlagId, beregningsGrunnlag)
            }
        }
    }

    private fun lagreBaseGrunnlag(connection: Connection, type: String): Int {
        val sql = "INSERT INTO GRUNNLAG(type) VALUES (?) ";

        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
            .apply {
                setString(1, type)
                executeUpdate()
            }
            .hentGenerertNøkkel()
    }

    private fun lagreGrunnlagYrkesskade(
        connection: Connection,
        baseGrunnlagId: Int,
        beregningsGrunnlag: IBeregningsGrunnlag.GrunnlagYrkesskade
    ): Int {
        val grunnlagType: String;
        val id = when (beregningsGrunnlag.beregningsgrunnlag) {
            is IBeregningsGrunnlag.Grunnlag_11_19 -> {
                grunnlagType = "normal"
                lagre11_19(
                    connection,
                    baseGrunnlagId,
                    beregningsGrunnlag.beregningsgrunnlag
                )
            }

            is IBeregningsGrunnlag.GrunnlagUføre -> {
                grunnlagType = "ufore"
                lagreGrunnlagUføre(
                    connection, baseGrunnlagId,
                    beregningsGrunnlag.beregningsgrunnlag
                )
            }

            is IBeregningsGrunnlag.GrunnlagYrkesskade -> throw RuntimeException("Yrkesskadegrunnlag kan ikke inneholde seg selv.")
        }

        val insertQuery =
            """
INSERT INTO GRUNNLAG_YRKESSKADE(grunnlag, er6g_begrenset, beregningsgrunnlag_id, beregningsgrunnlag_type,
                                terskelverdi_for_yrkesskade,
                                andel_som_skyldes_yrkesskade, andel_yrkesskade,
                                benyttet_andel_for_yrkesskade,
                                andel_som_ikke_skyldes_yrkesskade,
                                antatt_arlig_inntekt_yrkesskade_tidspunktet,
                                yrkesskade_tidspunkt, grunnlag_for_beregning_av_yrkesskadeandel,
                                yrkesskadeinntekt_ig,
                                grunnlag_etter_yrkesskade_fordel)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        return connection.prepareStatement(
            insertQuery, Statement.RETURN_GENERATED_KEYS
        )
            .apply {
                setDouble(1, beregningsGrunnlag.grunnlaget())
                setBoolean(2, beregningsGrunnlag.er6GBegrenset())
                setInt(3, id)
                setString(4, grunnlagType)
                setInt(5, beregningsGrunnlag.terskelverdiForYrkesskade)
                setBigDecimal(6, beregningsGrunnlag.andelSomSkyldesYrkesskade)
                setInt(7, beregningsGrunnlag.andelYrkesskade)
                setInt(8, beregningsGrunnlag.benyttetAndelForYrkesskade)
                setBigDecimal(9, beregningsGrunnlag.andelSomIkkeSkyldesYrkesskade)
                setBigDecimal(
                    10,
                    beregningsGrunnlag.antattÅrligInntektYrkesskadeTidspunktet
                )
                setInt(11, beregningsGrunnlag.yrkesskadeTidspunkt)
                setBigDecimal(
                    12,
                    beregningsGrunnlag.grunnlagForBeregningAvYrkesskadeandel
                )
                setBigDecimal(13, beregningsGrunnlag.yrkesskadeinntektIG)
                setBigDecimal(14, beregningsGrunnlag.grunnlagEtterYrkesskadeFordel)
                executeUpdate()
            }.hentGenerertNøkkel()
    }

    private fun lagreGrunnlagUføre(
        connection: Connection,
        baseGrunnlagId: Int,
        beregningsGrunnlag: IBeregningsGrunnlag.GrunnlagUføre
    ): Int {
        val id = lagre11_19(connection, baseGrunnlagId, beregningsGrunnlag.grunnlag11_19)
        val insertQuery =
            """INSERT INTO GRUNNLAG_UFORE(grunnlag_id, grunnlag, er6g_begrenset, grunnlag_11_19_id, type,
                               uforegrad, ufore_inntekter_fra_foregaende_ar, ufore_inntekt_i_kroner,
                               ufore_ytterligere_nedsatt_arbeidsevne_ar)
    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)"""
        return connection.prepareStatement(
            insertQuery, Statement.RETURN_GENERATED_KEYS

        )
            .apply {
                setInt(1, baseGrunnlagId)
                setDouble(2, beregningsGrunnlag.grunnlag)
                setBoolean(3, beregningsGrunnlag.er6GBegrenset)
                setInt(4, id)
                setString(5, beregningsGrunnlag.type.name)
                setInt(6, beregningsGrunnlag.uføregrad)
                setString(
                    7,
                    ObjectMapper().writeValueAsString(beregningsGrunnlag.uføreInntekterFraForegåendeÅr)
                )
                setBigDecimal(8, beregningsGrunnlag.uføreInntektIKroner)
                setInt(9, beregningsGrunnlag.uføreYtterligereNedsattArbeidsevneÅr)
                executeUpdate()
            }.hentGenerertNøkkel()
    }

    override fun hentBeregningsGrunnlag(): List<IBeregningsGrunnlag> {
        return dataSource.connection.use { conn ->
            val resultSet = conn.prepareStatement(
                """
select grunnlag.id                                    as gr_id,
       grunnlag.type                                  as gr_type,
       g.id                                           as g_id,
       g.grunnlag_id                                  as g_grunnlag_id,
       g.grunnlag                                     as g_grunnlag,
       g.er6g_begrenset                               as g_er6g_begrenset,
       g.er_gjennomsnitt                              as g_er_gjennomsnitt,
       g.inntekter                                    as g_inntekter,
       gy.id                                          as gy_id,
       gy.grunnlag                                    as gy_grunnlag,
       gy.er6g_begrenset                              as gy_er6g_begrenset,
       gy.beregningsgrunnlag_id                       as gy_beregningsgrunnlag_id,
       gy.beregningsgrunnlag_type                     as gy_beregningsgrunnlag_type,
       gy.terskelverdi_for_yrkesskade                 as gy_terskelverdi_for_yrkesskade,
       gy.andel_som_skyldes_yrkesskade                as gy_andel_som_skyldes_yrkesskade,
       gy.andel_yrkesskade                            as gy_andel_yrkesskade,
       gy.benyttet_andel_for_yrkesskade               as gy_benyttet_andel_for_yrkesskade,
       gy.andel_som_ikke_skyldes_yrkesskade           as gy_andel_som_ikke_skyldes_yrkesskade,
       gy.antatt_arlig_inntekt_yrkesskade_tidspunktet as gy_antatt_arlig_inntekt_yrkesskade_tidspunktet,
       gy.yrkesskade_tidspunkt                        as gy_yrkesskade_tidspunkt,
       gy.grunnlag_for_beregning_av_yrkesskadeandel   as gy_grunnlag_for_beregning_av_yrkesskadeandel,
       gy.yrkesskadeinntekt_ig                        as gy_yrkesskadeinntekt_ig,
       gy.grunnlag_etter_yrkesskade_fordel            as gy_grunnlag_etter_yrkesskade_fordel,
       gu.id                                          as gu_id,
       gu.grunnlag_id                                 as gu_grunnlag_id,
       gu.grunnlag                                    as gu_grunnlag,
       gu.er6g_begrenset                              as gu_er6g_begrenset,
       gu.type                                        as gu_type,
       gu.grunnlag_11_19_id                           as gu_grunnlag_11_19_id,
       gu.uforegrad                                   as gu_uforegrad,
       gu.ufore_inntekter_fra_foregaende_ar           as gu_ufore_inntekter_fra_foregaende_ar,
       gu.ufore_inntekt_i_kroner                      as gu_ufore_inntekt_i_kroner,
       gu.ufore_ytterligere_nedsatt_arbeidsevne_ar    as gu_ufore_ytterligere_nedsatt_arbeidsevne_ar
from grunnlag
         left outer join grunnlag_11_19 as g on grunnlag.id = g.grunnlag_id
         left outer join grunnlag_yrkesskade as gy on g.id = gy.beregningsgrunnlag_id
         left outer join grunnlag_ufore as gu on g.id = gu.grunnlag_11_19_id;
            """
            ).executeQuery()

            val beregningsGrunnlag = mutableListOf<IBeregningsGrunnlag>()

            while (resultSet.next()) {
                val grunnlagsType: IBeregningsGrunnlag = when (resultSet.getString("gr_type")) {
                    "11_19" -> {
                        hentGrunnlag11_19(resultSet)
                    }

                    "uføre" -> {
                        hentUtGrunnlagUføre(resultSet)
                    }

                    "yrkesskade" -> {
                        hentUtGrunnlagYrkesskade(resultSet)
                    }

                    else -> {
                        throw IllegalStateException("Dette er alle mulige grunnlagstyper.")
                    }
                }

                beregningsGrunnlag.add(grunnlagsType)
            }

            beregningsGrunnlag
        }
    }

    private fun hentUtGrunnlagYrkesskade(resultSet: ResultSet): IBeregningsGrunnlag.GrunnlagYrkesskade {
        val type = resultSet.getString("gy_beregningsgrunnlag_type")
        return IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = resultSet.getDouble("gy_grunnlag"),
            er6GBegrenset = resultSet.getBoolean("gy_er6g_begrenset"),
            beregningsgrunnlag = when (type) {
                "normal" -> hentGrunnlag11_19(resultSet)
                "ufore" -> hentUtGrunnlagUføre(resultSet)
                else -> throw IllegalStateException("Umulig å komme hit.")
            },
            terskelverdiForYrkesskade = resultSet.getInt("gy_terskelverdi_for_yrkesskade"),
            andelSomSkyldesYrkesskade = resultSet.getBigDecimal("gy_andel_som_skyldes_yrkesskade"),
            andelYrkesskade = resultSet.getInt("gy_andel_yrkesskade"),
            benyttetAndelForYrkesskade = resultSet.getInt("gy_benyttet_andel_for_yrkesskade"),
            andelSomIkkeSkyldesYrkesskade = resultSet.getBigDecimal("gy_andel_som_ikke_skyldes_yrkesskade"),
            antattÅrligInntektYrkesskadeTidspunktet = resultSet.getBigDecimal(
                "gy_antatt_arlig_inntekt_yrkesskade_tidspunktet"
            ),
            yrkesskadeTidspunkt = resultSet.getInt("gy_yrkesskade_tidspunkt"),
            grunnlagForBeregningAvYrkesskadeandel = resultSet.getBigDecimal(
                "gy_grunnlag_for_beregning_av_yrkesskadeandel"
            ),
            yrkesskadeinntektIG = resultSet.getBigDecimal("gy_yrkesskadeinntekt_ig"),
            grunnlagEtterYrkesskadeFordel = resultSet.getBigDecimal("gy_grunnlag_etter_yrkesskade_fordel")
        )
    }

    private fun hentUtGrunnlagUføre(resultSet: ResultSet): IBeregningsGrunnlag.GrunnlagUføre {
        return IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = resultSet.getDouble("gu_grunnlag"),
            er6GBegrenset = resultSet.getBoolean("gu_er6g_begrenset"),
            grunnlag11_19 = hentGrunnlag11_19(resultSet),
            type = UføreType.valueOf(resultSet.getString("gu_type")),
            uføregrad = resultSet.getInt("gu_uforegrad"),
            uføreInntekterFraForegåendeÅr = ObjectMapper().readValue(
                resultSet.getString(
                    "gu_ufore_inntekter_fra_foregaende_ar"
                )
            ),
            uføreInntektIKroner = resultSet.getBigDecimal("gu_ufore_inntekt_i_kroner"),
            uføreYtterligereNedsattArbeidsevneÅr = resultSet.getInt("gu_ufore_ytterligere_nedsatt_arbeidsevne_ar"),
        )
    }

    private fun hentGrunnlag11_19(grunnlagUføreRs: ResultSet): IBeregningsGrunnlag.Grunnlag_11_19 {
        val typeRef
                : TypeReference<Map<Int, Double>> =
            object : TypeReference<Map<Int, Double>>() {}
        val parsedMap = ObjectMapper().readValue(grunnlagUføreRs.getString("g_inntekter"), typeRef)

        val grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = grunnlagUføreRs.getDouble("g_grunnlag"),
            er6GBegrenset = grunnlagUføreRs.getBoolean("g_er6g_begrenset"),
            erGjennomsnitt = grunnlagUføreRs.getBoolean("g_er_gjennomsnitt"),
            inntekter = parsedMap
        )
        return grunnlag11_19
    }

    private fun lagre11_19(
        connection: Connection,
        baseGrunnlagId: Int,
        beregningsGrunnlag: IBeregningsGrunnlag.Grunnlag_11_19
    ): Int {
        val sqlStatement =
            "INSERT INTO GRUNNLAG_11_19(grunnlag_id, grunnlag, er6g_begrenset, er_gjennomsnitt, inntekter) VALUES (?, ?,?, ?, ?::jsonb)"
        val id = connection.prepareStatement(
            sqlStatement,
            Statement.RETURN_GENERATED_KEYS
        )
            .apply {
                setInt(1, baseGrunnlagId)
                setDouble(2, beregningsGrunnlag.grunnlag)
                setBoolean(3, beregningsGrunnlag.er6GBegrenset)
                setBoolean(4, beregningsGrunnlag.erGjennomsnitt)
                setString(5, ObjectMapper().writeValueAsString(beregningsGrunnlag.inntekter))
                executeUpdate()
            }.hentGenerertNøkkel()
        return id
    }
}

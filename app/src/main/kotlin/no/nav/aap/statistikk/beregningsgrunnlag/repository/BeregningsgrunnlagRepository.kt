package no.nav.aap.statistikk.beregningsgrunnlag.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.UføreType
import no.nav.aap.statistikk.db.hentGenerertNøkkel
import no.nav.aap.statistikk.db.withinTransaction
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import javax.sql.DataSource


class BeregningsgrunnlagRepository(private val dataSource: DataSource) {
    fun lagreBeregningsGrunnlag(beregningsGrunnlag: IBeregningsGrunnlag): Int {
        return when (beregningsGrunnlag) {
            is IBeregningsGrunnlag.Grunnlag_11_19 -> {
                dataSource.withinTransaction { conn ->
                    lagre11_19(beregningsGrunnlag, conn)
                }
            }

            is IBeregningsGrunnlag.GrunnlagYrkesskade -> {
                dataSource.withinTransaction { conn ->
                    val id = lagre11_19(beregningsGrunnlag.beregningsgrunnlag, conn)
                    val insertQuery =
                        """INSERT INTO GRUNNLAG_YRKESSKADE(grunnlag, er6g_begrenset, beregningsgrunnlag_id, terskelverdi_for_yrkesskade,
                                andel_som_skyldes_yrkesskade, andel_yrkesskade, benyttet_andel_for_yrkesskade,
                                andel_som_ikke_skyldes_yrkesskade, antatt_arlig_inntekt_yrkesskade_tidspunktet,
                                yrkesskade_tidspunkt, grunnlag_for_beregning_av_yrkesskadeandel,
                                yrkesskadeinntekt_ig,
                                grunnlag_etter_yrkesskade_fordel)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
                    conn.prepareStatement(
                        insertQuery, Statement.RETURN_GENERATED_KEYS
                    )
                        .apply {
                            setDouble(1, beregningsGrunnlag.grunnlag)
                            setBoolean(2, beregningsGrunnlag.er6GBegrenset)
                            setInt(3, id)
                            setInt(4, beregningsGrunnlag.terskelverdiForYrkesskade)
                            setBigDecimal(5, beregningsGrunnlag.andelSomSkyldesYrkesskade)
                            setInt(6, beregningsGrunnlag.andelYrkesskade)
                            setInt(7, beregningsGrunnlag.benyttetAndelForYrkesskade)
                            setBigDecimal(8, beregningsGrunnlag.andelSomIkkeSkyldesYrkesskade)
                            setBigDecimal(9, beregningsGrunnlag.antattÅrligInntektYrkesskadeTidspunktet)
                            setInt(10, beregningsGrunnlag.yrkesskadeTidspunkt)
                            setBigDecimal(11, beregningsGrunnlag.grunnlagForBeregningAvYrkesskadeandel)
                            setBigDecimal(12, beregningsGrunnlag.yrkesskadeinntektIG)
                            setBigDecimal(13, beregningsGrunnlag.grunnlagEtterYrkesskadeFordel)
                            executeUpdate()
                        }.hentGenerertNøkkel()
                }
            }


            is IBeregningsGrunnlag.GrunnlagUføre -> {
                dataSource.withinTransaction { conn ->
                    val id = lagre11_19(beregningsGrunnlag.grunnlag11_19, conn)
                    val insertQuery =
                        """INSERT INTO GRUNNLAG_UFORE(grunnlag, er6g_begrenset, grunnlag_11_19_id, type,
                           uforegrad, ufore_inntekter_fra_foregaende_ar, ufore_inntekt_i_kroner,
                           ufore_ytterligere_nedsatt_arbeidsevne_ar)
VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)"""
                    conn.prepareStatement(
                        insertQuery, Statement.RETURN_GENERATED_KEYS

                    )
                        .apply {
                            setDouble(1, beregningsGrunnlag.grunnlag)
                            setBoolean(2, beregningsGrunnlag.er6GBegrenset)
                            setInt(3, id)
                            setString(4, beregningsGrunnlag.type.name)
                            setInt(5, beregningsGrunnlag.uføregrad)
                            setString(
                                6,
                                ObjectMapper().writeValueAsString(beregningsGrunnlag.uføreInntekterFraForegåendeÅr)
                            )
                            setBigDecimal(7, beregningsGrunnlag.uføreInntektIKroner)
                            setInt(8, beregningsGrunnlag.uføreYtterligereNedsattArbeidsevneÅr)
                            executeUpdate()
                        }.hentGenerertNøkkel()
                }
            }
        }
    }


    fun hentBeregningsGrunnlag(): List<IBeregningsGrunnlag> {
        return dataSource.connection.use { conn ->
            val grunnlagYrkesskadeRs = conn.prepareStatement(
                """SELECT gy.*,
       g11_19.grunnlag       as grunnlag_g11_19,
       g11_19.er6g_begrenset as er6g_begrenset_11_19,
       g11_19.*
FROM GRUNNLAG_YRKESSKADE AS gy
         JOIN GRUNNLAG_11_19 AS g11_19 ON gy.beregningsgrunnlag_id = g11_19.id
            """
            ).executeQuery()

            val beregningsGrunnlag = mutableListOf<IBeregningsGrunnlag>()

            while (grunnlagYrkesskadeRs.next()) {
                val grunnlag11_19 = grunnlag1119(grunnlagYrkesskadeRs)

                val grunnlagYrkesskade = IBeregningsGrunnlag.GrunnlagYrkesskade(
                    grunnlag = grunnlagYrkesskadeRs.getDouble("grunnlag"),
                    er6GBegrenset = grunnlagYrkesskadeRs.getBoolean("er6g_begrenset"),
                    beregningsgrunnlag = grunnlag11_19,
                    terskelverdiForYrkesskade = grunnlagYrkesskadeRs.getInt("terskelverdi_for_yrkesskade"),
                    andelSomSkyldesYrkesskade = grunnlagYrkesskadeRs.getBigDecimal("andel_som_skyldes_yrkesskade"),
                    andelYrkesskade = grunnlagYrkesskadeRs.getInt("andel_yrkesskade"),
                    benyttetAndelForYrkesskade = grunnlagYrkesskadeRs.getInt("benyttet_andel_for_yrkesskade"),
                    andelSomIkkeSkyldesYrkesskade = grunnlagYrkesskadeRs.getBigDecimal("andel_som_ikke_skyldes_yrkesskade"),
                    antattÅrligInntektYrkesskadeTidspunktet = grunnlagYrkesskadeRs.getBigDecimal("antatt_arlig_inntekt_yrkesskade_tidspunktet"),
                    yrkesskadeTidspunkt = grunnlagYrkesskadeRs.getInt("yrkesskade_tidspunkt"),
                    grunnlagForBeregningAvYrkesskadeandel = grunnlagYrkesskadeRs.getBigDecimal("grunnlag_for_beregning_av_yrkesskadeandel"),
                    yrkesskadeinntektIG = grunnlagYrkesskadeRs.getBigDecimal("yrkesskadeinntekt_ig"),
                    grunnlagEtterYrkesskadeFordel = grunnlagYrkesskadeRs.getBigDecimal("grunnlag_etter_yrkesskade_fordel")
                )

                beregningsGrunnlag.add(grunnlagYrkesskade)
            }

            val grunnlagUføreRs = conn.prepareStatement(
                """SELECT gu.*,
       g11_19.grunnlag       as grunnlag_g11_19,
       g11_19.er6g_begrenset as er6g_begrenset_11_19,
       g11_19.*
FROM GRUNNLAG_UFORE gu
         JOIN GRUNNLAG_11_19 g11_19 ON gu.grunnlag_11_19_id = g11_19.id
            """
            ).executeQuery()

            while (grunnlagUføreRs.next()) {
                val grunnlag11_19 = grunnlag1119(grunnlagUføreRs)

                val grunnlagUføre = IBeregningsGrunnlag.GrunnlagUføre(
                    grunnlag = grunnlagUføreRs.getDouble("grunnlag"),
                    er6GBegrenset = grunnlagUføreRs.getBoolean("er6g_begrenset"),
                    grunnlag11_19 = grunnlag11_19,
                    type = UføreType.valueOf(grunnlagUføreRs.getString("type")),
                    uføregrad = grunnlagUføreRs.getInt("uforegrad"),
                    uføreInntekterFraForegåendeÅr = ObjectMapper().readValue(grunnlagUføreRs.getString("ufore_inntekter_fra_foregaende_ar")),
                    uføreInntektIKroner = grunnlagUføreRs.getBigDecimal("ufore_inntekt_i_kroner"),
                    uføreYtterligereNedsattArbeidsevneÅr = grunnlagUføreRs.getInt("ufore_ytterligere_nedsatt_arbeidsevne_ar"),
                )

                beregningsGrunnlag.add(grunnlagUføre)
            }

            beregningsGrunnlag
        }
    }

    private fun grunnlag1119(grunnlagUføreRs: ResultSet): IBeregningsGrunnlag.Grunnlag_11_19 {
        val typeRef
                : TypeReference<Map<String, BigDecimal>> = object : TypeReference<Map<String, BigDecimal>>() {}
        val parsedMap = ObjectMapper().readValue(grunnlagUføreRs.getString("inntekter"), typeRef)

        val grunnlag11_19 = IBeregningsGrunnlag.Grunnlag_11_19(
            grunnlag = grunnlagUføreRs.getDouble("grunnlag_g11_19"),
            er6GBegrenset = grunnlagUføreRs.getBoolean("er6g_begrenset_11_19"),
            inntekter = parsedMap
        )
        return grunnlag11_19
    }

    private fun lagre11_19(beregningsGrunnlag: IBeregningsGrunnlag.Grunnlag_11_19, connection: Connection): Int {
        val sqlStatement = "INSERT INTO GRUNNLAG_11_19(grunnlag, er6g_begrenset, inntekter) VALUES (?, ?, ?::jsonb)"
        val id = connection.prepareStatement(
            sqlStatement,
            Statement.RETURN_GENERATED_KEYS
        )
            .apply {
                setDouble(1, beregningsGrunnlag.grunnlag)
                setBoolean(2, beregningsGrunnlag.er6GBegrenset)
                setString(3, ObjectMapper().writeValueAsString(beregningsGrunnlag.inntekter))
                executeUpdate()
            }.hentGenerertNøkkel()
        return id
    }
}

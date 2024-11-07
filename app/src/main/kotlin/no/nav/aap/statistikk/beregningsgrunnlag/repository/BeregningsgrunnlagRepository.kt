package no.nav.aap.statistikk.beregningsgrunnlag.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.avsluttetbehandling.UføreType
import no.nav.aap.statistikk.behandling.BehandlingId
import org.slf4j.LoggerFactory
import java.util.*


interface IBeregningsgrunnlagRepository {
    fun lagreBeregningsGrunnlag(beregningsGrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>): Long
    fun hentBeregningsGrunnlag(): List<MedBehandlingsreferanse<IBeregningsGrunnlag>>
}

private val logger = LoggerFactory.getLogger(BeregningsgrunnlagRepository::class.java)


class BeregningsgrunnlagRepository(
    private val dbConnection: DBConnection
) :
    IBeregningsgrunnlagRepository {
    override fun lagreBeregningsGrunnlag(beregningsGrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>): Long {
        val behandlingsReferanseId = hentBehandlingsReferanseId(
            dbConnection,
            beregningsGrunnlag.behandlingsReferanse
        )

        val beregningsGrunnlagVerdi = beregningsGrunnlag.value

        val baseGrunnlagId =
            lagreBaseGrunnlag(dbConnection, beregningsGrunnlagVerdi.type().toString(), behandlingsReferanseId)

        return when (beregningsGrunnlagVerdi) {
            is IBeregningsGrunnlag.Grunnlag_11_19 -> {
                lagre11_19(dbConnection, baseGrunnlagId, beregningsGrunnlagVerdi)
            }

            is IBeregningsGrunnlag.GrunnlagYrkesskade -> {
                lagreGrunnlagYrkesskade(dbConnection, baseGrunnlagId, beregningsGrunnlagVerdi)
            }

            is IBeregningsGrunnlag.GrunnlagUføre ->
                lagreGrunnlagUføre(dbConnection, baseGrunnlagId, beregningsGrunnlagVerdi)
        }

    }

    private fun hentBehandlingsReferanseId(dbConnection: DBConnection, referanse: UUID): Long {
        val sql = "SELECT id FROM behandling WHERE referanse = ?"

        return dbConnection.queryFirst<Long>(sql) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                it.getLong("id")
            }
        }
    }

    private fun lagreBaseGrunnlag(
        connection: DBConnection,
        type: String,
        behandlingId: BehandlingId
    ): Long {
        val sql = "INSERT INTO GRUNNLAG(type, behandling_id) VALUES (?, ?) ";

        return connection.executeReturnKey(sql) {
            setParams {
                setString(1, type)
                setLong(2, behandlingId)
            }
        }
    }

    private fun lagreGrunnlagYrkesskade(
        connection: DBConnection,
        baseGrunnlagId: Long,
        beregningsGrunnlag: IBeregningsGrunnlag.GrunnlagYrkesskade
    ): Long {
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
INSERT INTO GRUNNLAG_YRKESSKADE(grunnlag, beregningsgrunnlag_id, beregningsgrunnlag_type,
                                terskelverdi_for_yrkesskade,
                                andel_som_skyldes_yrkesskade, andel_yrkesskade,
                                benyttet_andel_for_yrkesskade,
                                andel_som_ikke_skyldes_yrkesskade,
                                antatt_arlig_inntekt_yrkesskade_tidspunktet,
                                yrkesskade_tidspunkt, grunnlag_for_beregning_av_yrkesskadeandel,
                                yrkesskadeinntekt_ig,
                                grunnlag_etter_yrkesskade_fordel)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        return connection.executeReturnKey(insertQuery) {
            var c = 1
            setParams {
                setDouble(c++, beregningsGrunnlag.grunnlaget())
                setLong(c++, id)
                setString(c++, grunnlagType)
                setInt(c++, beregningsGrunnlag.terskelverdiForYrkesskade)
                setBigDecimal(c++, beregningsGrunnlag.andelSomSkyldesYrkesskade)
                setInt(c++, beregningsGrunnlag.andelYrkesskade)
                setInt(c++, beregningsGrunnlag.benyttetAndelForYrkesskade)
                setBigDecimal(c++, beregningsGrunnlag.andelSomIkkeSkyldesYrkesskade)
                setBigDecimal(
                    c++,
                    beregningsGrunnlag.antattÅrligInntektYrkesskadeTidspunktet
                )
                setInt(c++, beregningsGrunnlag.yrkesskadeTidspunkt)
                setBigDecimal(
                    c++,
                    beregningsGrunnlag.grunnlagForBeregningAvYrkesskadeandel
                )
                setBigDecimal(c++, beregningsGrunnlag.yrkesskadeinntektIG)
                setBigDecimal(c++, beregningsGrunnlag.grunnlagEtterYrkesskadeFordel)
            }
        }
    }

    private fun lagreGrunnlagUføre(
        connection: DBConnection,
        baseGrunnlagId: Long,
        beregningsGrunnlag: IBeregningsGrunnlag.GrunnlagUføre
    ): Long {
        val id = lagre11_19(connection, baseGrunnlagId, beregningsGrunnlag.grunnlag11_19)
        val insertQuery =
            """INSERT INTO GRUNNLAG_UFORE(grunnlag_id, grunnlag, grunnlag_11_19_id, type,
                               uforegrad, ufore_inntekter_fra_foregaende_ar,
                               ufore_ytterligere_nedsatt_arbeidsevne_ar)
    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)"""

        return connection.executeReturnKey(insertQuery) {
            var c = 1
            setParams {
                setLong(c++, baseGrunnlagId)
                setDouble(c++, beregningsGrunnlag.grunnlag)
                setLong(c++, id)
                setString(c++, beregningsGrunnlag.type.name)
                setInt(c++, beregningsGrunnlag.uføregrad)
                setString(
                    c++,
                    ObjectMapper().writeValueAsString(beregningsGrunnlag.uføreInntekterFraForegåendeÅr)
                )
                setInt(c++, beregningsGrunnlag.uføreYtterligereNedsattArbeidsevneÅr)
            }
        }
    }

    override fun hentBeregningsGrunnlag(): List<MedBehandlingsreferanse<IBeregningsGrunnlag>> {
        val sql = """
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
       gu.type                                        as gu_type,
       gu.grunnlag_11_19_id                           as gu_grunnlag_11_19_id,
       gu.uforegrad                                   as gu_uforegrad,
       gu.ufore_inntekter_fra_foregaende_ar           as gu_ufore_inntekter_fra_foregaende_ar,
       gu.ufore_ytterligere_nedsatt_arbeidsevne_ar    as gu_ufore_ytterligere_nedsatt_arbeidsevne_ar,
       b.referanse                                    as b_referanse
from grunnlag
         left outer join grunnlag_11_19 as g on grunnlag.id = g.grunnlag_id
         left outer join grunnlag_yrkesskade as gy on g.id = gy.beregningsgrunnlag_id
         left outer join grunnlag_ufore as gu on g.id = gu.grunnlag_11_19_id
         left outer join behandling as b on b.id = grunnlag.behandling_id
            """
        return dbConnection.queryList(sql) {
            setRowMapper { row ->
                val referanse = row.getUUID("b_referanse")

                val type = row.getString("gr_type")

                val grunnlagsType: IBeregningsGrunnlag = when (type) {
                    "11_19" -> {
                        hentGrunnlag11_19(row)
                    }

                    "uføre" -> {
                        hentUtGrunnlagUføre(row)
                    }

                    "yrkesskade" -> {
                        hentUtGrunnlagYrkesskade(row)
                    }

                    else -> {
                        throw IllegalStateException("Dette er alle mulige grunnlagstyper.")
                    }
                }

                MedBehandlingsreferanse<IBeregningsGrunnlag>(
                    behandlingsReferanse = referanse,
                    value = grunnlagsType
                )
            }
        }
    }

    private fun hentUtGrunnlagYrkesskade(resultSet: Row): IBeregningsGrunnlag.GrunnlagYrkesskade {
        val type = resultSet.getString("gy_beregningsgrunnlag_type")
        return IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = resultSet.getDouble("gy_grunnlag"),
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

    private fun hentUtGrunnlagUføre(resultSet: Row): IBeregningsGrunnlag.GrunnlagUføre {
        return IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = resultSet.getDouble("gu_grunnlag"),
            grunnlag11_19 = hentGrunnlag11_19(resultSet),
            type = UføreType.valueOf(resultSet.getString("gu_type")),
            uføregrad = resultSet.getInt("gu_uforegrad"),
            uføreInntekterFraForegåendeÅr = ObjectMapper().readValue(
                resultSet.getString(
                    "gu_ufore_inntekter_fra_foregaende_ar"
                )
            ),
            uføreYtterligereNedsattArbeidsevneÅr = resultSet.getInt("gu_ufore_ytterligere_nedsatt_arbeidsevne_ar"),
        )
    }

    private fun hentGrunnlag11_19(grunnlagUføreRs: Row): IBeregningsGrunnlag.Grunnlag_11_19 {
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
        connection: DBConnection,
        baseGrunnlagId: Long,
        beregningsGrunnlag: IBeregningsGrunnlag.Grunnlag_11_19
    ): Long {
        val sqlStatement =
            "INSERT INTO GRUNNLAG_11_19(grunnlag_id, grunnlag, er6g_begrenset, er_gjennomsnitt, inntekter) VALUES (?, ?,?, ?, ?::jsonb)"

        return connection.executeReturnKey(sqlStatement) {
            setParams {
                setLong(1, baseGrunnlagId)
                setDouble(2, beregningsGrunnlag.grunnlag)
                setBoolean(3, beregningsGrunnlag.er6GBegrenset)
                setBoolean(4, beregningsGrunnlag.erGjennomsnitt)
                setString(5, ObjectMapper().writeValueAsString(beregningsGrunnlag.inntekter))
            }
        }
    }
}

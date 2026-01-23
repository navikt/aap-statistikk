package no.nav.aap.statistikk.beregningsgrunnlag.repository

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.avsluttetbehandling.UføreType
import no.nav.aap.statistikk.behandling.BehandlingId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.util.*
import kotlin.collections.orEmpty


interface IBeregningsgrunnlagRepository : Repository {
    fun lagreBeregningsGrunnlag(beregningsGrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>): Long
    fun hentBeregningsGrunnlag(referanse: UUID): List<MedBehandlingsreferanse<IBeregningsGrunnlag>>
}


class BeregningsgrunnlagRepository(
    private val dbConnection: DBConnection
) : IBeregningsgrunnlagRepository {
    companion object : RepositoryFactory<IBeregningsgrunnlagRepository> {
        override fun konstruer(connection: DBConnection): IBeregningsgrunnlagRepository {
            return BeregningsgrunnlagRepository(connection)
        }
    }

    override fun lagreBeregningsGrunnlag(beregningsGrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>): Long {
        val behandlingsReferanseId = hentBehandlingsReferanseId(
            dbConnection,
            beregningsGrunnlag.behandlingsReferanse
        )

        val beregningsGrunnlagVerdi = beregningsGrunnlag.value

        val baseGrunnlagId =
            lagreBaseGrunnlag(
                dbConnection,
                beregningsGrunnlagVerdi,
                behandlingsReferanseId
            )

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

    private fun hentBehandlingsReferanseId(
        dbConnection: DBConnection,
        referanse: UUID
    ): BehandlingId {
        val sql = """SELECT b.id
FROM behandling b
         JOIN behandling_referanse br
              on b.referanse_id = br.id
WHERE br.referanse = ?"""

        return dbConnection.queryFirst(sql) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                it.getLong("id").let(::BehandlingId)
            }
        }
    }

    private fun lagreBaseGrunnlag(
        connection: DBConnection,
        grunnlag: IBeregningsGrunnlag,
        behandlingId: BehandlingId
    ): Long {
        val sql = "INSERT INTO GRUNNLAG(type, behandling_id, opprettet_tidspunkt, beregningsaar) VALUES (?, ?, ?, ?) "

        return connection.executeReturnKey(sql) {
            setParams {
                setString(1, grunnlag.type().toString())
                setLong(2, behandlingId.id)
                setLocalDateTime(3, LocalDateTime.now())
                setInt(4, grunnlag.beregningsår().value)
            }
        }
    }

    private fun lagreGrunnlagYrkesskade(
        connection: DBConnection,
        baseGrunnlagId: Long,
        beregningsGrunnlag: IBeregningsGrunnlag.GrunnlagYrkesskade
    ): Long {
        val grunnlagType: String
        when (beregningsGrunnlag.beregningsgrunnlag) {
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

            is IBeregningsGrunnlag.GrunnlagYrkesskade -> error("Yrkesskadegrunnlag kan ikke inneholde seg selv.")
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
                setLong(c++, baseGrunnlagId)
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
                setInt(c++, beregningsGrunnlag.yrkesskadeTidspunkt.value)
                setBigDecimal(
                    c++,
                    beregningsGrunnlag.grunnlagForBeregningAvYrkesskadeandel
                )
                setBigDecimal(c++, beregningsGrunnlag.yrkesskadeinntektIG)
                setBigDecimal(c, beregningsGrunnlag.grunnlagEtterYrkesskadeFordel)
            }
        }
    }

    private data class ÅrOgInntekt(val aar: Int, val inntekt: Double)

    private fun lagreGrunnlagUføre(
        connection: DBConnection,
        baseGrunnlagId: Long,
        beregningsGrunnlag: IBeregningsGrunnlag.GrunnlagUføre
    ): Long {
        val id = lagre11_19(connection, baseGrunnlagId, beregningsGrunnlag.grunnlag11_19)
        val insertQuery =
            """INSERT INTO GRUNNLAG_UFORE(grunnlag_id, grunnlag, grunnlag_11_19_id, type,
                               uforegrad, ufore_inntekter_fra_foregaende_ar,
                               ufore_ytterligere_nedsatt_arbeidsevne_ar, ufore_inntekter)
    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb)"""

        val executeReturnKey = connection.executeReturnKey(insertQuery) {
            var c = 1
            setParams {
                setLong(c++, baseGrunnlagId)
                setDouble(c++, beregningsGrunnlag.grunnlag)
                setLong(c++, id)
                setString(c++, beregningsGrunnlag.type.name)
                setInt(
                    c++,
                    beregningsGrunnlag.uføregrader.entries.maxByOrNull { it.key }?.value ?: 0
                )
                setString(
                    c++,
                    ObjectMapper().writeValueAsString(beregningsGrunnlag.uføreInntekterFraForegåendeÅr)
                )
                setInt(c++, beregningsGrunnlag.uføreYtterligereNedsattArbeidsevneÅr)
                setString(
                    c++,
                    DefaultJsonMapper.toJson(beregningsGrunnlag.uføreInntekterFraForegåendeÅr.entries.map {
                        ÅrOgInntekt(
                            it.key,
                            it.value
                        )
                    })
                )
            }
        }

        connection.executeBatch(
            """
            insert into grunnlag_uforegrader(grunnlag_ufore_id, uforegrad, virkningstidspunkt)
            values (?, ?, ?)
        """.trimIndent(), beregningsGrunnlag.uføregrader.entries
        ) {
            setParams { (virkningstidspunkt, grad) ->
                setLong(1, executeReturnKey)
                setInt(2, grad)
                setLocalDate(3, virkningstidspunkt)
            }
        }

        return executeReturnKey
    }

    override fun hentBeregningsGrunnlag(referanse: UUID): List<MedBehandlingsreferanse<IBeregningsGrunnlag>> {
        val sql = """select grunnlag.id                                    as gr_id,
       grunnlag.type                                  as gr_type,
       g.id                                           as g_id,
       g.grunnlag_id                                  as g_grunnlag_id,
       g.grunnlag                                     as g_grunnlag,
       g.er6g_begrenset                               as g_er6g_begrenset,
       g.er_gjennomsnitt                              as g_er_gjennomsnitt,
       g.inntekter                                    as g_inntekter,
       g.inntekter_foregaaende_aar                    as g_inntekter_foregaaende_aar,
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
       (select json_agg(json_build_object('virkningstidspunkt', virkningstidspunkt, 'uforegrad',
                                          uforegrad))
        from grunnlag_uforegrader
        where grunnlag_ufore_id = gu.id)              as gu_uforegrader,
       gu.ufore_inntekter_fra_foregaende_ar           as gu_ufore_inntekter_fra_foregaende_ar,
       gu.ufore_inntekter                             as gu_ufore_inntekter,
       gu.ufore_ytterligere_nedsatt_arbeidsevne_ar    as gu_ufore_ytterligere_nedsatt_arbeidsevne_ar,
       br.referanse                                   as b_referanse
from grunnlag
         left outer join grunnlag_11_19 as g on grunnlag.id = g.grunnlag_id
         left outer join grunnlag_yrkesskade as gy on g.id = gy.beregningsgrunnlag_id
         left outer join grunnlag_ufore as gu on g.id = gu.grunnlag_11_19_id
         left outer join behandling as b on b.id = grunnlag.behandling_id
         left outer join behandling_referanse br on b.referanse_id = br.id
where br.referanse = ?
            """
        return dbConnection.queryList(sql) {
            setParams { setUUID(1, referanse) }
            setRowMapper { row ->
                val ref = row.getUUID("b_referanse")

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
                        error("Dette er alle mulige grunnlagstyper.")
                    }
                }

                MedBehandlingsreferanse(
                    behandlingsReferanse = ref,
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
                else -> error("Umulig å komme hit.")
            },
            terskelverdiForYrkesskade = resultSet.getInt("gy_terskelverdi_for_yrkesskade"),
            andelSomSkyldesYrkesskade = resultSet.getBigDecimal("gy_andel_som_skyldes_yrkesskade"),
            andelYrkesskade = resultSet.getInt("gy_andel_yrkesskade"),
            benyttetAndelForYrkesskade = resultSet.getInt("gy_benyttet_andel_for_yrkesskade"),
            andelSomIkkeSkyldesYrkesskade = resultSet.getBigDecimal("gy_andel_som_ikke_skyldes_yrkesskade"),
            antattÅrligInntektYrkesskadeTidspunktet = resultSet.getBigDecimal(
                "gy_antatt_arlig_inntekt_yrkesskade_tidspunktet"
            ),
            yrkesskadeTidspunkt = Year.of(resultSet.getInt("gy_yrkesskade_tidspunkt")),
            grunnlagForBeregningAvYrkesskadeandel = resultSet.getBigDecimal(
                "gy_grunnlag_for_beregning_av_yrkesskadeandel"
            ),
            yrkesskadeinntektIG = resultSet.getBigDecimal("gy_yrkesskadeinntekt_ig"),
            grunnlagEtterYrkesskadeFordel = resultSet.getBigDecimal("gy_grunnlag_etter_yrkesskade_fordel")
        )
    }

    private data class UføregradOgDato(val uforegrad: Int, val virkningstidspunkt: LocalDate)

    private fun hentUtGrunnlagUføre(resultSet: Row): IBeregningsGrunnlag.GrunnlagUføre {
        val uføreInntekterFraForegåendeÅr = DefaultJsonMapper.fromJson<List<ÅrOgInntekt>>(
            resultSet.getString(
                "gu_ufore_inntekter"
            )
        ).associateBy { it.aar }.mapValues { it.value.inntekt }

        val hentUføregraderKompatibel: () -> Map<LocalDate, Int> = hentUføregraderGammel@{
            val uføregrader = resultSet.getStringOrNull("gu_uforegrader")
                ?.let { DefaultJsonMapper.fromJson<List<UføregradOgDato>>(it) }.orEmpty()
                .associate { it.virkningstidspunkt to it.uforegrad }

            val gammelUføregrad =
                resultSet.getIntOrNull("gu_uforegrad") ?: return@hentUføregraderGammel uføregrader

            val fallbackBeregningsÅrUføre =
                Year.of(uføreInntekterFraForegåendeÅr.keys.max() + 1).atDay(1)

            uføregrader.ifEmpty { return@hentUføregraderGammel mapOf(fallbackBeregningsÅrUføre to gammelUføregrad) }
        }

        return IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = resultSet.getDouble("gu_grunnlag"),
            grunnlag11_19 = hentGrunnlag11_19(resultSet),
            type = UføreType.valueOf(resultSet.getString("gu_type")),
            uføregrader = hentUføregraderKompatibel(),
            uføreInntekterFraForegåendeÅr = uføreInntekterFraForegåendeÅr,
            uføreYtterligereNedsattArbeidsevneÅr = resultSet.getInt("gu_ufore_ytterligere_nedsatt_arbeidsevne_ar"),
        )
    }

    @Suppress("FunctionName")
    private fun hentGrunnlag11_19(row: Row): IBeregningsGrunnlag.Grunnlag_11_19 {
        val inntekter =
            DefaultJsonMapper.fromJson<List<ÅrOgInntekt>>(row.getString("g_inntekter_foregaaende_aar"))

        @Suppress("LocalVariableName", "VariableNaming") val grunnlag11_19 =
            IBeregningsGrunnlag.Grunnlag_11_19(
                grunnlag = row.getDouble("g_grunnlag"),
                er6GBegrenset = row.getBoolean("g_er6g_begrenset"),
                erGjennomsnitt = row.getBoolean("g_er_gjennomsnitt"),
                inntekter = inntekter.associateBy { it.aar }.mapValues { it.value.inntekt },
            )
        return grunnlag11_19
    }

    @Suppress("FunctionName")
    private fun lagre11_19(
        connection: DBConnection,
        baseGrunnlagId: Long,
        beregningsGrunnlag: IBeregningsGrunnlag.Grunnlag_11_19
    ): Long {
        val sqlStatement =
            """INSERT INTO GRUNNLAG_11_19(grunnlag_id, grunnlag, er6g_begrenset, er_gjennomsnitt, inntekter, inntekter_foregaaende_aar)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)"""

        return connection.executeReturnKey(sqlStatement) {
            setParams {
                setLong(1, baseGrunnlagId)
                setDouble(2, beregningsGrunnlag.grunnlag)
                setBoolean(3, beregningsGrunnlag.er6GBegrenset)
                setBoolean(4, beregningsGrunnlag.erGjennomsnitt)
                setString(5, ObjectMapper().writeValueAsString(beregningsGrunnlag.inntekter))
                setString(
                    6,
                    DefaultJsonMapper.toJson(beregningsGrunnlag.inntekter.map {
                        ÅrOgInntekt(
                            it.key,
                            it.value
                        )
                    })
                )
            }
        }
    }
}

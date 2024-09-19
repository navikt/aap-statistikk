package no.nav.aap.statistikk.beregningsgrunnlag.repository

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.api_kontrakt.UføreType
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.bigquery.BQTable
import java.math.BigDecimal
import java.util.UUID

typealias Beregningsgrunnlag = MedBehandlingsreferanse<IBeregningsGrunnlag>

class BeregningsGrunnlagTabell : BQTable<Beregningsgrunnlag> {
    override val tableName: String = "beregningsgrunnlag"
    override val schema: Schema
        get() {
            val inntekt = Field.newBuilder(
                "inntekter",
                StandardSQLTypeName.STRUCT,
                Field.of("aar", StandardSQLTypeName.INT64),
                Field.of("inntekt", StandardSQLTypeName.FLOAT64),
            ).setMode(Field.Mode.REPEATED)

            val s11_19_struct = Field.newBuilder(
                "beregningsgrunnlag_11_19", StandardSQLTypeName.STRUCT,
                inntekt.build(),
                Field.of("grunnlag", StandardSQLTypeName.FLOAT64),
                Field.of("erGjennomsnitt", StandardSQLTypeName.BOOL),
                Field.of("er6GBegrenset", StandardSQLTypeName.BOOL),
            )

            val uføre_struct = Field.newBuilder(
                "beregningsgrunnlag_ufore", StandardSQLTypeName.STRUCT,
                Field.of("grunnlag", StandardSQLTypeName.FLOAT64),
                Field.of("er6GBegrenset", StandardSQLTypeName.BOOL),
                Field.of("type", StandardSQLTypeName.STRING),
                s11_19_struct.build(),
                Field.of("uforegrad", StandardSQLTypeName.INT64),
                inntekt.setName("uforeInntekterFraForegaaendeAar").build(),
                Field.of("uforeInntektIKroner", StandardSQLTypeName.FLOAT64),
                Field.of("uforeYtterligereNedsattArbeidsevneAar", StandardSQLTypeName.INT64)
            )

            val yrkesskade_struct = Field.newBuilder(
                "beregningsgrunnlag_yrkesskade", StandardSQLTypeName.STRUCT,
                Field.of("grunnlaget", StandardSQLTypeName.FLOAT64),
                Field.of("er6GBegrenset", StandardSQLTypeName.BOOL),
                s11_19_struct.setName("beregningsgrunnlag_11_19").build(),
                uføre_struct.setName("beregningsgrunnlag_ufore").build(),
                Field.of("terskelverdiForYrkesskade", StandardSQLTypeName.INT64),
                Field.of("andelSomSkyldesYrkesskade", StandardSQLTypeName.FLOAT64),
                Field.of("andelYrkesskade", StandardSQLTypeName.INT64),
                Field.of("benyttetAndelForYrkesskade", StandardSQLTypeName.INT64),
                Field.of("andelSomIkkeSkyldesYrkesskade", StandardSQLTypeName.FLOAT64),
                Field.of("antattAarligInntektYrkesskadeTidspunktet", StandardSQLTypeName.FLOAT64),
                Field.of("yrkesskadeTidspunkt", StandardSQLTypeName.INT64),
                Field.of("grunnlagForBeregningAvYrkesskadeandel", StandardSQLTypeName.FLOAT64),
                Field.of("yrkesskadeinntektIG", StandardSQLTypeName.FLOAT64),
                Field.of("grunnlagEtterYrkesskadeFordel", StandardSQLTypeName.FLOAT64),
            )

            val behandlingsreferanse = Field.of("behandlingsreferanse", StandardSQLTypeName.STRING)

            return Schema.of(
                s11_19_struct.build(),
                uføre_struct.build(),
                yrkesskade_struct.build(),
                behandlingsreferanse
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): Beregningsgrunnlag {
        val behandlingsreferanse =
            UUID.fromString(fieldValueList.get("behandlingsreferanse").stringValue)

        if (!fieldValueList.get(0).isNull) {
            return Beregningsgrunnlag(
                value = grunnlag1119(fieldValueList.get(0).recordValue),
                behandlingsReferanse = behandlingsreferanse
            )
        }

        if (!fieldValueList.get(1).isNull) {
            val recordValue = fieldValueList.get(1).recordValue
            return Beregningsgrunnlag(
                value = grunnlagUføre(recordValue),
                behandlingsReferanse = behandlingsreferanse
            )
        }

        if (!fieldValueList.get(2).isNull) {
            val recordValue = fieldValueList.get(2).recordValue

            return Beregningsgrunnlag(
                value = getGrunnlagYrkesskade(recordValue),
                behandlingsReferanse = behandlingsreferanse
            )
        }

        throw IllegalStateException()
    }

    private fun getGrunnlagYrkesskade(recordValue: FieldValueList): IBeregningsGrunnlag.GrunnlagYrkesskade {
        val grunnlaget = recordValue.get(0).doubleValue
        val er6GBegrenset = recordValue.get(1).booleanValue
        val beregningsgrunnlag_11_19 =
            if (recordValue.get(2).isNull) null else recordValue.get(0).recordValue

        val beregningsgrunnlag_ufore =
            if (recordValue.get(3).isNull) null else recordValue.get(3).recordValue

        val terskelverdiForYrkesskade = recordValue.get(4).longValue
        val andelSomSkyldesYrkesskade = recordValue.get(5).doubleValue
        val andelYrkesskade = recordValue.get(6).longValue
        val benyttetAndelForYrkesskade = recordValue.get(7).longValue
        val andelSomIkkeSkyldesYrkesskade = recordValue.get(8).doubleValue
        val antattAarligInntektYrkesskadeTidspunktet = recordValue.get(9).doubleValue
        val yrkesskadeTidspunkt = recordValue.get(10).longValue
        val grunnlagForBeregningAvYrkesskadeandel = recordValue.get(11).doubleValue
        val yrkesskadeinntektIG = recordValue.get(12).doubleValue
        val grunnlagEtterYrkesskadeFordel = recordValue.get(13).doubleValue

        return IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = grunnlaget,
            er6GBegrenset = er6GBegrenset,
            beregningsgrunnlag = if (beregningsgrunnlag_ufore !== null) {
                grunnlagUføre(beregningsgrunnlag_ufore)
            } else if (beregningsgrunnlag_11_19 !== null) {
                grunnlag1119(beregningsgrunnlag_11_19)
            } else {
                throw IllegalStateException()
            },
            terskelverdiForYrkesskade = terskelverdiForYrkesskade.toInt(),
            andelSomSkyldesYrkesskade = BigDecimal(andelSomSkyldesYrkesskade),
            andelYrkesskade = andelYrkesskade.toInt(),
            benyttetAndelForYrkesskade = benyttetAndelForYrkesskade.toInt(),
            andelSomIkkeSkyldesYrkesskade = BigDecimal(andelSomIkkeSkyldesYrkesskade),
            antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(
                antattAarligInntektYrkesskadeTidspunktet
            ),
            yrkesskadeTidspunkt = yrkesskadeTidspunkt.toInt(),
            grunnlagForBeregningAvYrkesskadeandel = BigDecimal(
                grunnlagForBeregningAvYrkesskadeandel
            ),
            yrkesskadeinntektIG = BigDecimal(yrkesskadeinntektIG),
            grunnlagEtterYrkesskadeFordel = BigDecimal(grunnlagEtterYrkesskadeFordel)
        )
    }

    private fun grunnlagUføre(recordValue: FieldValueList): IBeregningsGrunnlag.GrunnlagUføre {
        val grunnlag = recordValue.get("grunnlag").doubleValue
        val er6GBegrenset = recordValue.get("er6GBegrenset").booleanValue
        val type = recordValue.get("type").stringValue
        val grunnlag11_19 = recordValue.get(3).recordValue
        val uføregrad = recordValue.get("uforegrad").longValue
        val uføreInntekterFraForegåendeÅr =
            recordValue.get("uforeInntekterFraForegaaendeAar").repeatedValue.map { inntekt ->
                val entry = if (inntekt.attribute == FieldValue.Attribute.REPEATED) {
                    inntekt.repeatedValue[0].recordValue.get(0).doubleValue
                } else {
                    inntekt.recordValue.get(0).doubleValue
                }
                val entry2 = if (inntekt.attribute == FieldValue.Attribute.REPEATED) {
                    inntekt.repeatedValue[1].recordValue.get(0).doubleValue
                } else {
                    inntekt.recordValue.get(1).doubleValue
                }
                entry to entry2
            }.associate { it.first.toInt() to it.second }
        val uføreInntektIKroner = recordValue.get("uforeInntektIKroner").doubleValue
        val uføreYtterligereNedsattArbeidsevneÅr =
            recordValue.get("uforeYtterligereNedsattArbeidsevneAar").longValue

        val grunnlag11_19_field_value = grunnlag11_19.get(0)
        val inntekter = grunnlag11_19_field_value.repeatedValue.map { inntekt ->
            val rep = inntekt
            val kuttedNed =
                if (rep.attribute == FieldValue.Attribute.REPEATED && rep.repeatedValue.get(0).attribute == FieldValue.Attribute.REPEATED) {
                    rep.repeatedValue.get(0).repeatedValue
                } else {
                    rep.repeatedValue
                }
            val entry = kuttedNed.get(0).recordValue.get(0).longValue
            val entry2 = kuttedNed.get(1).recordValue.get(0).doubleValue

            entry to entry2
        }.associate { it.first.toInt() to it.second }

        val grunnlag_11_19_ = if (grunnlag11_19.get(1).attribute == FieldValue.Attribute.RECORD) {
            grunnlag11_19.get(1).recordValue.get(0).doubleValue
        } else {
            grunnlag11_19.get(1).doubleValue
        }
        val erGjennomsnitt_ = if (grunnlag11_19.get(2).attribute == FieldValue.Attribute.RECORD) {
            grunnlag11_19.get(2).recordValue.get(0).booleanValue
        } else {
            grunnlag11_19.get(2).booleanValue
        }
        val er6GBegrenset_11_19_ =
            if (grunnlag11_19.get(3).attribute == FieldValue.Attribute.RECORD) {
                grunnlag11_19.get(3).recordValue.get(0).booleanValue
            } else {
                grunnlag11_19.get(3).booleanValue
            }
        val grunnlag1119 = IBeregningsGrunnlag.Grunnlag_11_19(
            inntekter = inntekter,
            grunnlag = grunnlag_11_19_,
            erGjennomsnitt = erGjennomsnitt_,
            er6GBegrenset = er6GBegrenset_11_19_
        )

        // TODO: test i bigquery for å skjønne dette

        return IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = grunnlag,
            er6GBegrenset = er6GBegrenset,
            type = UføreType.valueOf(type),
            grunnlag11_19 = grunnlag1119,
            uføregrad = uføregrad.toInt(),
            uføreInntekterFraForegåendeÅr = uføreInntekterFraForegåendeÅr,
            uføreInntektIKroner = BigDecimal(uføreInntektIKroner),
            uføreYtterligereNedsattArbeidsevneÅr = uføreYtterligereNedsattArbeidsevneÅr.toInt()
        )
    }

    private fun grunnlag1119(grunnlag_11_19: FieldValueList): IBeregningsGrunnlag.Grunnlag_11_19 {
        val inntekter = grunnlag_11_19.get("inntekter").repeatedValue.toList().map { inntekt ->
            inntekt.recordValue[0].longValue to inntekt.recordValue[1].doubleValue
        }.associate { it.first.toInt() to it.second }

        val grunnlag = grunnlag_11_19.get("grunnlag").doubleValue
        val erGjennomsnitt = grunnlag_11_19.get("erGjennomsnitt").booleanValue
        val er6GBegrenset = grunnlag_11_19.get("er6GBegrenset").booleanValue
        return IBeregningsGrunnlag.Grunnlag_11_19(
            inntekter = inntekter,
            grunnlag = grunnlag,
            erGjennomsnitt = erGjennomsnitt,
            er6GBegrenset = er6GBegrenset
        )
    }


    override fun toRow(input: Beregningsgrunnlag): InsertAllRequest.RowToInsert {
        val value = input.value
        when (value) {
            is IBeregningsGrunnlag.GrunnlagUføre -> {
                return InsertAllRequest.RowToInsert.of(
                    mapOf(
                        "behandlingsreferanse" to input.behandlingsReferanse.toString(),
                        "beregningsgrunnlag_ufore" to mapOf(
                            "grunnlag" to value.grunnlag,
                            "er6GBegrenset" to value.er6GBegrenset,
                            "type" to value.type.name,
                            "beregningsgrunnlag_11_19" to mapOf(
                                "grunnlag" to value.grunnlag11_19.grunnlag,
                                "erGjennomsnitt" to value.grunnlag11_19.erGjennomsnitt,
                                "er6GBegrenset" to value.grunnlag11_19.er6GBegrenset,
                                "inntekter" to value.grunnlag11_19.inntekter.map { (år, inntekt) ->
                                    mapOf(
                                        "aar" to år,
                                        "inntekt" to inntekt
                                    )
                                }
                            ),
                            "uforegrad" to value.uføregrad,
                            "uforeInntekterFraForegaaendeAar" to value.uføreInntekterFraForegåendeÅr.map { (år, inntekt) ->
                                mapOf(
                                    "aar" to år,
                                    "inntekt" to inntekt
                                )
                            },
                            "uforeYtterligereNedsattArbeidsevneAar" to value.uføreYtterligereNedsattArbeidsevneÅr,
                            "uforeInntektIKroner" to value.uføreInntektIKroner
                        )
                    )
                )
            }

            is IBeregningsGrunnlag.GrunnlagYrkesskade -> return InsertAllRequest.RowToInsert.of(
                mapOf(
                    "behandlingsreferanse" to input.behandlingsReferanse.toString(),
                    "beregningsgrunnlag_yrkesskade" to mapOf(
                        "grunnlaget" to value.grunnlaget,
                        "er6GBegrenset" to value.er6GBegrenset,
                        "beregningsgrunnlag_ufore" to (if (value.beregningsgrunnlag is IBeregningsGrunnlag.GrunnlagUføre) {
                            toRow(
                                Beregningsgrunnlag(
                                    value = value.beregningsgrunnlag,
                                    behandlingsReferanse = input.behandlingsReferanse
                                )
                            ).content.get("beregningsgrunnlag_ufore")
                        } else {
                            null
                        }),
                        "beregningsgrunnlag_11_19" to (if (value.beregningsgrunnlag is IBeregningsGrunnlag.Grunnlag_11_19) {
                            toRow(
                                Beregningsgrunnlag(
                                    value = value.beregningsgrunnlag,
                                    behandlingsReferanse = input.behandlingsReferanse
                                )
                            ).content.get("beregningsgrunnlag_11_19")
                        } else {
                            null
                        }),
                        "terskelverdiForYrkesskade" to value.terskelverdiForYrkesskade,
                        "andelSomSkyldesYrkesskade" to value.andelSomSkyldesYrkesskade,
                        "andelYrkesskade" to value.andelYrkesskade,
                        "benyttetAndelForYrkesskade" to value.benyttetAndelForYrkesskade,
                        "andelSomIkkeSkyldesYrkesskade" to value.andelSomIkkeSkyldesYrkesskade,
                        "antattAarligInntektYrkesskadeTidspunktet" to value.antattÅrligInntektYrkesskadeTidspunktet,
                        "yrkesskadeTidspunkt" to value.yrkesskadeTidspunkt,
                        "grunnlagForBeregningAvYrkesskadeandel" to value.grunnlagForBeregningAvYrkesskadeandel,
                        "yrkesskadeinntektIG" to value.yrkesskadeinntektIG,
                        "grunnlagEtterYrkesskadeFordel" to value.grunnlagEtterYrkesskadeFordel,
                    ).filterValues { it != null }
                )
            )

            is IBeregningsGrunnlag.Grunnlag_11_19 -> return InsertAllRequest.RowToInsert.of(
                mapOf(
                    "beregningsgrunnlag_11_19" to mapOf(
                        "grunnlag" to value.grunnlag,
                        "erGjennomsnitt" to value.erGjennomsnitt,
                        "er6GBegrenset" to value.er6GBegrenset,
                        "inntekter" to value.inntekter.map { (år, inntekt) ->
                            mapOf(
                                "aar" to år,
                                "inntekt" to inntekt
                            )
                        }
                    ),
                    "behandlingsreferanse" to input.behandlingsReferanse.toString()
                )
            )
        }
    }
}
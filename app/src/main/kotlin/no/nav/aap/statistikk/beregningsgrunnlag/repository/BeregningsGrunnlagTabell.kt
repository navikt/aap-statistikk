package no.nav.aap.statistikk.beregningsgrunnlag.repository

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.avsluttetbehandling.GrunnlagType
import no.nav.aap.statistikk.bigquery.BQTable
import java.util.*

// TODO legg til inntekt
data class BeregningsGrunnlagBQ(
    val saksnummer: String,
    val behandlingsreferanse: UUID,
    val type: GrunnlagType,
    val grunnlaget: Double,
    val standardGrunnlag: Double,
    val standardEr6GBegrenset: Boolean,
    val standardErGjennomsnitt: Boolean,
    val uføreGrunnlag: Double? = null,
    val uføreUføregrad: Int? = null,
    val yrkesskadeTerskelVerdiForYrkesskade: Int? = null,
    val yrkesskadeAndelSomSkyldesYrkesskade: Double? = null,
    val yrkesskadeAndelSomIkkeSkyldesYrkesskade: Double? = null,
    val yrkesskadeAndelYrkesskade: Int? = null,
    val yrkesskadeBenyttetAndelForYrkesskade: Int? = null,
    val yrkesskadeAntattÅrligInntektYrkesskadeTidspunktet: Double? = null,
    val yrkesskadeYrkesskadeTidspunkt: Int? = null,
    val yrkesskadeGrunnlagForBeregningAvYrkesskadeandel: Double? = null,
    val yrkesskadeYrkesskadeinntektIG: Double? = null,
    val yrkesskadeGrunnlagEtterYrkesskadeFordel: Double? = null,
)

class BeregningsGrunnlagTabell : BQTable<BeregningsGrunnlagBQ> {
    companion object {
        const val TABLE_NAME = "beregningsgrunnlag"
    }

    override val tableName: String = TABLE_NAME
    override val version: Int = 1
    override val schema: Schema
        get() {
            val sak_id = Field.of("sakId", StandardSQLTypeName.STRING)
            val behandlingsreferanse = Field.of("behandlingsreferanse", StandardSQLTypeName.STRING)
            val type = Field.of("type", StandardSQLTypeName.STRING)
            val grunnlaget = Field.of("grunnlaget", StandardSQLTypeName.FLOAT64)
            val standardGrunnlag = Field.of("standardGrunnlag", StandardSQLTypeName.FLOAT64)
            val standardEr6GBegrenset = Field.of("standardEr6GBegrenset", StandardSQLTypeName.BOOL)
            val standardErGjennomsnitt =
                Field.of("standardErGjennomsnitt", StandardSQLTypeName.BOOL)

            val uforeGrunnlag = Field.of("uforeGrunnlag", StandardSQLTypeName.FLOAT64)
            val uforeUforegrad = Field.of("uforeUforegrad", StandardSQLTypeName.INT64)
            val yrkesskadeTerskelVerdiForYrkesskade =
                Field.of("yrkesskadeTerskelVerdiForYrkesskade", StandardSQLTypeName.INT64)
            val yrkesskadeAndelSomSkyldesYrkesskade =
                Field.of("yrkesskadeAndelSomSkyldesYrkesskade", StandardSQLTypeName.FLOAT64)
            val yrkesskadeAndelSomIkkeSkyldesYrkesskade =
                Field.of("yrkesskadeAndelSomIkkeSkyldesYrkesskade", StandardSQLTypeName.FLOAT64)
            val yrkesskadeAndelYrkesskade =
                Field.of("yrkesskadeAndelYrkesskade", StandardSQLTypeName.INT64)
            val yrkesskadeBenyttetAndelForYrkesskade =
                Field.of("yrkesskadeBenyttetAndelForYrkesskade", StandardSQLTypeName.INT64)
            val yrkesskadeAntattaarligInntektYrkesskadeTidspunktet = Field.of(
                "yrkesskadeAntattaarligInntektYrkesskadeTidspunktet",
                StandardSQLTypeName.FLOAT64
            )
            val yrkesskadeYrkesskadeTidspunkt =
                Field.of("yrkesskadeYrkesskadeTidspunkt", StandardSQLTypeName.INT64)
            val yrkesskadeGrunnlagForBeregningAvYrkesskadeandel = Field.of(
                "yrkesskadeGrunnlagForBeregningAvYrkesskadeandel",
                StandardSQLTypeName.FLOAT64
            )
            val yrkesskadeYrkesskadeinntektIG =
                Field.of("yrkesskadeYrkesskadeinntektIG", StandardSQLTypeName.FLOAT64)
            val yrkesskadeGrunnlagEtterYrkesskadeFordel =
                Field.of("yrkesskadeGrunnlagEtterYrkesskadeFordel", StandardSQLTypeName.FLOAT64)

            return Schema.of(
                sak_id,
                behandlingsreferanse,
                type,
                grunnlaget,
                standardGrunnlag,
                standardEr6GBegrenset,
                standardErGjennomsnitt,
                uforeGrunnlag,
                uforeUforegrad,
                yrkesskadeTerskelVerdiForYrkesskade,
                yrkesskadeAndelSomSkyldesYrkesskade,
                yrkesskadeAndelSomIkkeSkyldesYrkesskade,
                yrkesskadeAndelYrkesskade,
                yrkesskadeBenyttetAndelForYrkesskade,
                yrkesskadeAntattaarligInntektYrkesskadeTidspunktet,
                yrkesskadeYrkesskadeTidspunkt,
                yrkesskadeGrunnlagForBeregningAvYrkesskadeandel,
                yrkesskadeYrkesskadeinntektIG,
                yrkesskadeGrunnlagEtterYrkesskadeFordel
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BeregningsGrunnlagBQ {
        val sakId = fieldValueList.get("sakId").stringValue
        val behandlingsreferanse =
            UUID.fromString(fieldValueList.get("behandlingsreferanse").stringValue)
        val type = GrunnlagType.valueOf(fieldValueList.get("type").stringValue)
        val grunnlaget = fieldValueList.get("grunnlaget").doubleValue
        val standardGrunnlag = fieldValueList.get("standardGrunnlag").doubleValue
        val standardEr6GBegrenset = fieldValueList.get("standardEr6GBegrenset").booleanValue
        val standardErGjennomsnitt = fieldValueList.get("standardErGjennomsnitt").booleanValue
        val uforeGrunnlag =
            fieldValueList.get("uforeGrunnlag").let { if (it.isNull) null else it.doubleValue }
        val uforeUforegrad = fieldValueList.get("uforeUforegrad")
            .let { if (it.isNull) null else it.longValue.toInt() }
        val yrkesskadeTerskelVerdiForYrkesskade =
            fieldValueList.get("yrkesskadeTerskelVerdiForYrkesskade")
                .let { if (it.isNull) null else it.longValue.toInt() }
        val yrkesskadeAndelSomSkyldesYrkesskade =
            fieldValueList.get("yrkesskadeAndelSomSkyldesYrkesskade")
                .let { if (it.isNull) null else it.doubleValue }
        val yrkesskadeAndelSomIkkeSkyldesYrkesskade =
            fieldValueList.get("yrkesskadeAndelSomIkkeSkyldesYrkesskade")
                .let { if (it.isNull) null else it.doubleValue }
        val yrkesskadeAndelYrkesskade =
            fieldValueList.get("yrkesskadeAndelYrkesskade")
                .let { if (it.isNull) null else it.longValue.toInt() }
        val yrkesskadeBenyttetAndelForYrkesskade =
            fieldValueList.get("yrkesskadeBenyttetAndelForYrkesskade")
                .let { if (it.isNull) null else it.longValue.toInt() }
        val yrkesskadeAntattaarligInntektYrkesskadeTidspunktet =
            fieldValueList.get("yrkesskadeAntattaarligInntektYrkesskadeTidspunktet")
                .let { if (it.isNull) null else it.doubleValue }
        val yrkesskadeYrkesskadeTidspunkt =
            fieldValueList.get("yrkesskadeYrkesskadeTidspunkt")
                .let { if (it.isNull) null else it.longValue.toInt() }
        val yrkesskadeGrunnlagForBeregningAvYrkesskadeandel =
            fieldValueList.get("yrkesskadeGrunnlagForBeregningAvYrkesskadeandel")
                .let { if (it.isNull) null else it.doubleValue }
        val yrkesskadeYrkesskadeinntektIG =
            fieldValueList.get("yrkesskadeYrkesskadeinntektIG")
                .let { if (it.isNull) null else it.doubleValue }
        val yrkesskadeGrunnlagEtterYrkesskadeFordel =
            fieldValueList.get("yrkesskadeGrunnlagEtterYrkesskadeFordel")
                .let { if (it.isNull) null else it.doubleValue }

        return BeregningsGrunnlagBQ(
            saksnummer = sakId,
            behandlingsreferanse = behandlingsreferanse,
            type = type,
            grunnlaget = grunnlaget,
            standardGrunnlag = standardGrunnlag,
            standardEr6GBegrenset = standardEr6GBegrenset,
            standardErGjennomsnitt = standardErGjennomsnitt,
            uføreGrunnlag = uforeGrunnlag,
            uføreUføregrad = uforeUforegrad,
            yrkesskadeTerskelVerdiForYrkesskade = yrkesskadeTerskelVerdiForYrkesskade,
            yrkesskadeAndelSomSkyldesYrkesskade = yrkesskadeAndelSomSkyldesYrkesskade,
            yrkesskadeAndelSomIkkeSkyldesYrkesskade = yrkesskadeAndelSomIkkeSkyldesYrkesskade,
            yrkesskadeAndelYrkesskade = yrkesskadeAndelYrkesskade,
            yrkesskadeBenyttetAndelForYrkesskade = yrkesskadeBenyttetAndelForYrkesskade,
            yrkesskadeAntattÅrligInntektYrkesskadeTidspunktet = yrkesskadeAntattaarligInntektYrkesskadeTidspunktet,
            yrkesskadeYrkesskadeTidspunkt = yrkesskadeYrkesskadeTidspunkt,
            yrkesskadeGrunnlagForBeregningAvYrkesskadeandel = yrkesskadeGrunnlagForBeregningAvYrkesskadeandel,
            yrkesskadeYrkesskadeinntektIG = yrkesskadeYrkesskadeinntektIG,
            yrkesskadeGrunnlagEtterYrkesskadeFordel = yrkesskadeGrunnlagEtterYrkesskadeFordel
        )
    }


    override fun toRow(value: BeregningsGrunnlagBQ): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "sakId" to value.saksnummer,
                "behandlingsreferanse" to value.behandlingsreferanse.toString(),
                "type" to value.type.name,
                "grunnlaget" to value.grunnlaget,
                "standardGrunnlag" to value.standardGrunnlag,
                "standardEr6GBegrenset" to value.standardEr6GBegrenset,
                "standardErGjennomsnitt" to value.standardErGjennomsnitt,
                "uforeGrunnlag" to value.uføreGrunnlag,
                "uforeUforegrad" to value.uføreUføregrad,
                "yrkesskadeTerskelVerdiForYrkesskade" to value.yrkesskadeTerskelVerdiForYrkesskade,
                "yrkesskadeAndelSomSkyldesYrkesskade" to value.yrkesskadeAndelSomSkyldesYrkesskade,
                "yrkesskadeAndelSomIkkeSkyldesYrkesskade" to value.yrkesskadeAndelSomIkkeSkyldesYrkesskade,
                "yrkesskadeAndelYrkesskade" to value.yrkesskadeAndelYrkesskade,
                "yrkesskadeBenyttetAndelForYrkesskade" to value.yrkesskadeBenyttetAndelForYrkesskade,
                "yrkesskadeAntattaarligInntektYrkesskadeTidspunktet" to value.yrkesskadeAntattÅrligInntektYrkesskadeTidspunktet,
                "yrkesskadeYrkesskadeTidspunkt" to value.yrkesskadeYrkesskadeTidspunkt,
                "yrkesskadeGrunnlagForBeregningAvYrkesskadeandel" to value.yrkesskadeGrunnlagForBeregningAvYrkesskadeandel,
                "yrkesskadeYrkesskadeinntektIG" to value.yrkesskadeYrkesskadeinntektIG,
                "yrkesskadeGrunnlagEtterYrkesskadeFordel" to value.yrkesskadeGrunnlagEtterYrkesskadeFordel
            )
        )
    }
}
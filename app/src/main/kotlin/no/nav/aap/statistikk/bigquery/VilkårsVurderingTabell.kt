package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.*
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.time.LocalDate

class VilkårsVurderingTabell : BQTable<Vilkårsresultat> {
    override val tableName: String = "vilkarsResultat5"
    override val schema: Schema
        get() {
            val saksnummerField = Field.of("saksnummer", StandardSQLTypeName.STRING)
            val behandlingsType = Field.of("behandlingsType", StandardSQLTypeName.STRING)
            val vilkårField =
                Field.newBuilder(
                    "vilkar",
                    StandardSQLTypeName.STRUCT,
                    Field.of("type", StandardSQLTypeName.STRING),
                    Field.newBuilder(
                        "perioder",
                        StandardSQLTypeName.STRUCT,
                        Field.of("fraDato", StandardSQLTypeName.DATE),
                        Field.of("tilDato", StandardSQLTypeName.DATE),
                        Field.of("utfall", StandardSQLTypeName.STRING),
                        Field.of("manuell_vurdering", StandardSQLTypeName.BOOL),
                    ).setMode(Field.Mode.REPEATED).build()
                )
                    .setMode(Field.Mode.REPEATED)
                    .build()
            return Schema.of(saksnummerField, behandlingsType, vilkårField)
        }


    override fun parseRow(fieldValueList: FieldValueList): Vilkårsresultat {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlingsType = fieldValueList.get("behandlingsType").stringValue

        // TODO https://github.com/googleapis/java-bigquery/issues/3389
        val vilkår =
            fieldValueList.get("vilkar").repeatedValue.map {
                Vilkår(
                    vilkårType = it.recordValue[0].stringValue,
                    perioder = it.recordValue[1].repeatedValue.map(::vilkårsPeriodeFraFieldValue)
                )
            }

        return Vilkårsresultat(saksnummer = saksnummer, behandlingsType = behandlingsType, vilkår = vilkår)
    }

    private fun vilkårsPeriodeFraFieldValue(periodeRecord: FieldValue) = VilkårsPeriode(
        fraDato = LocalDate.parse(periodeRecord.recordValue[0].stringValue),
        tilDato = LocalDate.parse(periodeRecord.recordValue[1].stringValue),
        utfall = periodeRecord.recordValue[2].stringValue,
        manuellVurdering = periodeRecord.recordValue[3].booleanValue,
    )

    override fun toRow(value: Vilkårsresultat): RowToInsert {
        // TODO: bruke ID?
        return RowToInsert.of(
            mapOf(
                "saksnummer" to value.saksnummer,
                "behandlingsType" to value.behandlingsType,
                "vilkar" to value.vilkår.map {
                    mapOf(
                        "type" to it.vilkårType,
                        "perioder" to it.perioder.map { periode ->
                            mapOf(
                                "fraDato" to periode.fraDato.toString(),
                                "tilDato" to periode.tilDato.toString(),
                                "utfall" to periode.utfall,
                                "manuell_vurdering" to periode.manuellVurdering
                            )
                        }
                    )
                })
        )
    }
}
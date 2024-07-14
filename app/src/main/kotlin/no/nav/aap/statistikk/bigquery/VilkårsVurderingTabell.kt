package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat

class VilkårsVurderingTabell : BQTable<Vilkårsresultat> {
    override val tableName: String = "vilkarsResultat"
    override val schema: Schema
        get() {
            val saksnummrField = Field.of("saksnummer", StandardSQLTypeName.STRING)
            val behandlingsType = Field.of("behandlingsType", StandardSQLTypeName.STRING)
            val vilkårField =
                Field.newBuilder("vilkar", StandardSQLTypeName.STRUCT, Field.of("type", StandardSQLTypeName.STRING))
                    .setMode(Field.Mode.REPEATED)
                    .build()
            return Schema.of(saksnummrField, behandlingsType, vilkårField)
        }


    override fun parseRow(fieldValueList: FieldValueList): Vilkårsresultat {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlingsType = fieldValueList.get("behandlingsType").stringValue

        val vilkår =
            fieldValueList.get("vilkar").repeatedValue.map {
                Vilkår(
                    vilkårType = it.recordValue.get(0).stringValue, // finnes måte å unngå 0 her?
                    perioder = listOf()
                )
            }

        return Vilkårsresultat(saksnummer = saksnummer, behandlingsType = behandlingsType, vilkår = vilkår)
    }

    override fun toRow(value: Vilkårsresultat): RowToInsert {
        // TODO: bruke ID?
        return RowToInsert.of(mapOf("saksnummer" to value.saksnummer, "behandlingsType" to value.behandlingsType,
            "vilkar" to value.vilkår.map { mapOf("type" to it.vilkårType) }
        ))
    }
}
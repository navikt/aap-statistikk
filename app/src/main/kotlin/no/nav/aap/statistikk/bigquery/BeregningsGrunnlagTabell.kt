package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema

class BeregningsGrunnlagTabell : BQTable<String> {
    override val tableName: String = "beregningsgrunnlag"
    override val schema: Schema
        get() = TODO("Not yet implemented")

    override fun parseRow(fieldValueList: FieldValueList): String {
        TODO("Not yet implemented")
    }

    override fun toRow(value: String): InsertAllRequest.RowToInsert {
        TODO("Not yet implemented")
    }
}
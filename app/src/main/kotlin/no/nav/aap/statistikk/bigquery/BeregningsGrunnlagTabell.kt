package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag

class BeregningsGrunnlagTabell : BQTable<IBeregningsGrunnlag> {
    override val tableName: String = "beregningsgrunnlag"
    override val schema: Schema
        get() {
            return Schema.of()
        }

    override fun parseRow(fieldValueList: FieldValueList): IBeregningsGrunnlag {
        TODO("Not yet implemented")
    }

    override fun toRow(value: IBeregningsGrunnlag): InsertAllRequest.RowToInsert {
        TODO("Not yet implemented")
    }
}

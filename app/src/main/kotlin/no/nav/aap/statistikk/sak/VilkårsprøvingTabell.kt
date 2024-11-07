package no.nav.aap.statistikk.sak

import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema
import no.nav.aap.statistikk.bigquery.BQTable

class VilkårsprøvingTabell : BQTable<BQVilkårsPrøving> {
    override val tableName: String
        get() = TODO("Not yet implemented")
    override val version: Int
        get() = TODO("Not yet implemented")
    override val schema: Schema
        get() = TODO("Not yet implemented")

    override fun parseRow(fieldValueList: FieldValueList): BQVilkårsPrøving {
        TODO("Not yet implemented")
    }

    override fun toRow(value: BQVilkårsPrøving): InsertAllRequest.RowToInsert {
        TODO("Not yet implemented")
    }
}
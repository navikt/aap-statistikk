package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema

interface BQTable<E> {
    val tableName: String
    val version: Int
    val schema: Schema

    fun parseRow(fieldValueList: FieldValueList): E

    fun toRow(value: E): InsertAllRequest.RowToInsert
}
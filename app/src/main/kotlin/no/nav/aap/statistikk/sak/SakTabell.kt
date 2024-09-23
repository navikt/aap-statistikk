package no.nav.aap.statistikk.sak

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.aap.statistikk.bigquery.BQTable

class SakTabell : BQTable<BQSak> {
    companion object {
        const val TABLE_NAME = "sak"
    }

    override val tableName = TABLE_NAME

    override val version: Int = 0
    override val schema: Schema
        get() {
            val saksnummmer = Field.of("saksnummer", StandardSQLTypeName.STRING)
            val behandlinger = Field.newBuilder(
                "behandlinger",
                StandardSQLTypeName.STRUCT,
                Field.of("behandlingUuid", StandardSQLTypeName.STRING)
            ).setMode(Field.Mode.REPEATED).build()
            return Schema.of(saksnummmer, behandlinger)
        }

    override fun parseRow(fieldValueList: FieldValueList): BQSak {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlinger = fieldValueList.get("behandlinger").repeatedValue.map {
            Behandling(it.recordValue[0].stringValue)
        }

        return BQSak(
            saksnummer = saksnummer,
            behandlinger = behandlinger
        )
    }

    override fun toRow(value: BQSak): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf("saksnummer" to value.saksnummer,
                "behandlinger" to value.behandlinger.map { mapOf("behandlingUuid" to it.referanse) })
        )
    }
}
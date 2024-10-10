package no.nav.aap.statistikk.behandling

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.bigquery.BQTable
import java.util.*

data class BQYtelseBehandling(
    val referanse: UUID,
    val brukerFnr: String,
    val behandlingsType: TypeBehandling
)

class BehandlingTabell : BQTable<BQYtelseBehandling> {
    companion object {
        const val TABLE_NAME = "behandlinger"
    }

    override val tableName: String = TABLE_NAME
    override val version: Int = 0
    override val schema: Schema
        get() {
            val referanse = Field.of("referanse", StandardSQLTypeName.STRING)
            val brukerFnr = Field.of("brukerFnr", StandardSQLTypeName.STRING)
            val behandlingsType = Field.of("behandlingsType", StandardSQLTypeName.STRING)
            return Schema.of(referanse, behandlingsType, brukerFnr)
        }

    override fun parseRow(fieldValueList: FieldValueList): BQYtelseBehandling {
        val referanse = fieldValueList.get("referanse").stringValue
        val brukerFnr = fieldValueList.get("brukerFnr").stringValue
        val behandlingsType = fieldValueList.get("behandlingsType").stringValue

        return BQYtelseBehandling(
            UUID.fromString(referanse),
            brukerFnr,
            behandlingsType = behandlingsType.let { TypeBehandling.valueOf(it) }
        )
    }

    override fun toRow(value: BQYtelseBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "referanse" to value.referanse.toString(),
                "brukerFnr" to value.brukerFnr,
                "behandlingsType" to value.behandlingsType.toString()
            )
        )
    }
}
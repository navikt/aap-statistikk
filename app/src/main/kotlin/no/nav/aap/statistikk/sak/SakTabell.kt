package no.nav.aap.statistikk.sak

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.bigquery.BQTable
import no.nav.aap.statistikk.person.Person
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class SakTabell : BQTable<BQBehandling> {
    companion object {
        const val TABLE_NAME = "sak"
    }

    override val tableName = TABLE_NAME

    override val version: Int = 0
    override val schema: Schema
        get() {
            val saksnummmer = Field.of("saksnummer", StandardSQLTypeName.STRING)
            val behandlingUuid = Field.of("behandlingUuid", StandardSQLTypeName.STRING)
            return Schema.of(saksnummmer, behandlingUuid)
        }

    override fun parseRow(fieldValueList: FieldValueList): BQBehandling {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlingUuid = fieldValueList.get("behandlingUuid").stringValue

        return BQBehandling(
            saksnummer = saksnummer,
            behandlingUUID = behandlingUuid,
        )
    }

    override fun toRow(value: BQBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "saksnummer" to value.saksnummer,
                "behandlingUuid" to value.behandlingUUID,
            )
        )
    }
}
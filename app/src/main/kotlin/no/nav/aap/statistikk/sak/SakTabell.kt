package no.nav.aap.statistikk.sak

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.LocalDateTime

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
            val behandlingType = Field.of("behandlingType", StandardSQLTypeName.STRING)
            val tekniskTid =
                Field.newBuilder("tekniskTid", StandardSQLTypeName.DATETIME)
                    .setDescription("Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.")
                    .build()
            val versjon = Field.of("versjon", StandardSQLTypeName.STRING)
            val avsender = Field.of("avsender", StandardSQLTypeName.STRING)
            return Schema.of(
                saksnummmer,
                behandlingUuid,
                behandlingType,
                tekniskTid,
                versjon,
                avsender
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BQBehandling {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlingUuid = fieldValueList.get("behandlingUuid").stringValue
        val tekniskTid = fieldValueList.get("tekniskTid").stringValue
        val behandlingType = fieldValueList.get("behandlingType").stringValue
        val versjon = fieldValueList.get("versjon").stringValue
        val avsender = fieldValueList.get("avsender").stringValue

        return BQBehandling(
            saksnummer = saksnummer,
            behandlingUUID = behandlingUuid,
            tekniskTid = LocalDateTime.parse(tekniskTid),
            behandlingType = behandlingType,
            avsender = avsender,
            verson = versjon
        )
    }

    override fun toRow(value: BQBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "saksnummer" to value.saksnummer,
                "behandlingUuid" to value.behandlingUUID,
                "behandlingType" to value.behandlingType,
                "tekniskTid" to value.tekniskTid,
                "avsender" to value.avsender,
                "versjon" to value.verson,
            )
        )
    }
}
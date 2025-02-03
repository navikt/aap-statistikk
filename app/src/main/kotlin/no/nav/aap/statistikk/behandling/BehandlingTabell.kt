package no.nav.aap.statistikk.behandling

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

data class BQYtelseBehandling(
    val referanse: UUID,
    val brukerFnr: String,
    val behandlingsType: TypeBehandling,
    val datoAvsluttet: LocalDateTime,
    val kodeverk: String?,
    val diagnosekode: String?,
    val bidiagnoser: List<String>?,
    val radEndret: LocalDateTime
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
            val datoAvsluttet = Field.of("datoAvsluttet", StandardSQLTypeName.DATETIME)
            val kodeverk = Field.of("kodeverk", StandardSQLTypeName.STRING)
            val diagnosekode = Field.of("diagnosekode", StandardSQLTypeName.STRING)
            val bidiagnoser = Field.newBuilder(
                "bidiagnoser",
                StandardSQLTypeName.STRUCT,
                Field.of("kode", StandardSQLTypeName.STRING)
            )
                .setMode(Field.Mode.REPEATED)
                .build();

            val radEndret = Field.of("radEndret", StandardSQLTypeName.DATETIME)

            return Schema.of(
                referanse,
                behandlingsType,
                brukerFnr,
                datoAvsluttet,
                kodeverk,
                diagnosekode,
                bidiagnoser,
                radEndret
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BQYtelseBehandling {
        val referanse = fieldValueList.get("referanse").stringValue
        val brukerFnr = fieldValueList.get("brukerFnr").stringValue
        val behandlingsType = fieldValueList.get("behandlingsType").stringValue
        val datoAvsluttet = LocalDateTime.parse(fieldValueList.get("datoAvsluttet").stringValue)
        val kodeverk = fieldValueList.get("kodeverk").stringValue
        val diagnosekode = fieldValueList.get("diagnosekode").stringValue
        val bidiagnoser =
            fieldValueList.get("bidiagnoser").repeatedValue.map { it.recordValue[0].stringValue }
        val radEndret = LocalDateTime.parse(fieldValueList.get("radEndret").stringValue)

        return BQYtelseBehandling(
            UUID.fromString(referanse),
            brukerFnr,
            behandlingsType = behandlingsType.let { TypeBehandling.valueOf(it) },
            datoAvsluttet = datoAvsluttet,
            kodeverk = kodeverk,
            diagnosekode = diagnosekode,
            bidiagnoser = bidiagnoser,
            radEndret = radEndret
        )
    }

    override fun toRow(value: BQYtelseBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "referanse" to value.referanse.toString(),
                "brukerFnr" to value.brukerFnr,
                "behandlingsType" to value.behandlingsType.toString(),
                "datoAvsluttet" to value.datoAvsluttet.truncatedTo(ChronoUnit.MILLIS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "kodeverk" to value.kodeverk,
                "diagnosekode" to value.diagnosekode,
                "bidiagnoser" to value.bidiagnoser?.map {
                    mapOf(
                        "kode" to it
                    )
                },
                "radEndret" to value.radEndret.truncatedTo(ChronoUnit.MILLIS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )
    }
}
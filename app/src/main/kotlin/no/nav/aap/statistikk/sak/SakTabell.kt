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
                Field.of("behandlingUuid", StandardSQLTypeName.STRING),
                Field.of("behandlingType", StandardSQLTypeName.STRING),
                Field.of("opprettetTid", StandardSQLTypeName.DATETIME),
            ).setMode(Field.Mode.REPEATED).build()
            return Schema.of(saksnummmer, behandlinger)
        }

    override fun parseRow(fieldValueList: FieldValueList): BQSak {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlinger = fieldValueList.get("behandlinger").repeatedValue.map {
            Behandling(
                referanse = UUID.fromString(it.recordValue[0].stringValue),
                typeBehandling = TypeBehandling.Førstegangsbehandling, // TODO
                opprettetTid = LocalDateTime.parse(it.recordValue[2].stringValue),
                sak = Sak(
                    saksnummer = "123",
                    person = Person(
                        ident = "213", // TODO!!
                    )
                )
            )
        }

        return BQSak(
            saksnummer = saksnummer,
            behandlinger = behandlinger
        )
    }

    override fun toRow(value: BQSak): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf("saksnummer" to value.saksnummer,
                "behandlinger" to value.behandlinger.map {
                    mapOf(
                        "behandlingUuid" to it.referanse.toString(),
                        "behandlingType" to it.typeBehandling.toString(),
                        "opprettetTid" to it.opprettetTid.toString(),
                    )
                })
        )
    }
}
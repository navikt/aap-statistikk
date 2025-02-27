package no.nav.aap.statistikk.behandling

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.avsluttetbehandling.RettighetsType
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

data class BQYtelseBehandling(
    val saksnummer: String,
    val referanse: UUID,
    val brukerFnr: String,
    val behandlingsType: TypeBehandling,
    val datoAvsluttet: LocalDateTime,
    val kodeverk: String?,
    val diagnosekode: String?,
    val bidiagnoser: List<String>?,
    val rettighetsPerioder: List<RettighetstypePeriode>,
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
            val saksnummer = Field.newBuilder("saksnummer", StandardSQLTypeName.STRING)
                .setDescription("Saksnummer. Ikke-null.")
                .build()
            val referanse = Field.newBuilder("referanse", StandardSQLTypeName.STRING)
                .setDescription("Behandlingsreferanse. Unik innenfor sak. Ikke-null.")
                .build()
            val behandlingsreferanse = Field.newBuilder("behandlingsreferanse", StandardSQLTypeName.STRING)
                .setDescription("Behandlingsreferanse. Unik innenfor sak. Ikke-null.")
                .build()
            val brukerFnr = Field.newBuilder("brukerFnr", StandardSQLTypeName.STRING)
                .setDescription("Fødselsnummer. Ikke-null").build()
            val behandlingsType = Field.newBuilder("behandlingsType", StandardSQLTypeName.STRING)
                .setDescription(
                    "Behandlingstype. Mulige verdier ${
                        TypeBehandling.entries.map { it.name }.joinToString()
                    }"
                ).build()
            val datoAvsluttet = Field.newBuilder("datoAvsluttet", StandardSQLTypeName.DATETIME)
                .setDescription("På hvilken dato ble saksbehandlingen avsluttet i Kelvin. Ikke-null.")
                .build()
            val kodeverk = Field.newBuilder("kodeverk", StandardSQLTypeName.STRING)
                .setDescription("Kodeverk brukt for diagnose i 11-5-vurderingen. Kan være null om søknaden ble avslått før sykdomsbildet ble vurdert.")
                .setMode(Field.Mode.NULLABLE).build()
            val diagnosekode = Field.newBuilder("diagnosekode", StandardSQLTypeName.STRING)
                .setDescription("Samme beskrivelse som for 'kodeverk'.")
                .setMode(Field.Mode.NULLABLE).build()
            val bidiagnoser = Field.newBuilder(
                "bidiagnoser",
                StandardSQLTypeName.STRUCT,
                Field.of("kode", StandardSQLTypeName.STRING)
            )
                .setDescription("Samme beskrivelse som for 'kodeverk'.")
                .setMode(Field.Mode.REPEATED)
                .build();
            val rettighetstypePeriode = Field.newBuilder(
                "rettighetstypePerioder",
                StandardSQLTypeName.STRUCT,
                Field.newBuilder("fraDato", StandardSQLTypeName.DATE)
                    .setDescription("Fra-og-med-dato for rettighetstypen. Ikke-null").build(),
                Field.newBuilder("tilDato", StandardSQLTypeName.DATE)
                    .setDescription("Til-og-med-dato for rettighetstypen. Ikke-null").build(),
                Field.newBuilder("rettighetstype", StandardSQLTypeName.STRING)
                    .setDescription("Rettighetstypen. Ikke-null.").build()
            ).setMode(Field.Mode.REPEATED)
                .setDescription("Periodisering av rettighetstype. F.eks kan AAP fås som sykepengeerstatning i en periode, og ordniær i en annen.")
                .build()

            val radEndret = Field.newBuilder("radEndret", StandardSQLTypeName.DATETIME)
                .setDescription("Tidspunkt for siste endring på denne raden.").build()

            return Schema.of(
                saksnummer,
                referanse,
                behandlingsreferanse,
                behandlingsType,
                brukerFnr,
                datoAvsluttet,
                kodeverk,
                diagnosekode,
                bidiagnoser,
                rettighetstypePeriode,
                radEndret
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BQYtelseBehandling {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val referanse = fieldValueList.get("behandlingsreferanse").stringValue
        val brukerFnr = fieldValueList.get("brukerFnr").stringValue
        val behandlingsType = fieldValueList.get("behandlingsType").stringValue
        val datoAvsluttet = LocalDateTime.parse(fieldValueList.get("datoAvsluttet").stringValue)
        val kodeverk = fieldValueList.get("kodeverk").stringValue
        val diagnosekode = fieldValueList.get("diagnosekode").stringValue
        val bidiagnoser =
            fieldValueList.get("bidiagnoser").repeatedValue.map { it.recordValue[0].stringValue }
        val radEndret = LocalDateTime.parse(fieldValueList.get("radEndret").stringValue)

        val rettighetstypePerioder = fieldValueList.get("rettighetstypePerioder").repeatedValue
            .map {
                RettighetstypePeriode(
                    fraDato = LocalDate.parse(it.recordValue[0].stringValue),
                    tilDato = LocalDate.parse(it.recordValue[1].stringValue),
                    rettighetstype = RettighetsType.valueOf(it.recordValue[2].stringValue)
                )
            }

        return BQYtelseBehandling(
            saksnummer = saksnummer,
            referanse = UUID.fromString(referanse),
            brukerFnr,
            behandlingsType = behandlingsType.let { TypeBehandling.valueOf(it) },
            datoAvsluttet = datoAvsluttet,
            kodeverk = kodeverk,
            diagnosekode = diagnosekode,
            bidiagnoser = bidiagnoser,
            rettighetsPerioder = rettighetstypePerioder,
            radEndret = radEndret
        )
    }

    override fun toRow(value: BQYtelseBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "saksnummer" to value.saksnummer,
                "referanse" to value.referanse.toString(),
                "behandlingsreferanse" to value.referanse.toString(),
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
                "rettighetstypePerioder" to value.rettighetsPerioder.map { periode ->
                    mapOf(
                        "fraDato" to periode.fraDato.toString(),
                        "tilDato" to periode.tilDato.toString(),
                        "rettighetstype" to periode.rettighetstype.toString()
                    )
                },
                "radEndret" to value.radEndret.truncatedTo(ChronoUnit.MILLIS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )
    }
}
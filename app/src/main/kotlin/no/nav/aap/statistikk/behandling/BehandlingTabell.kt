package no.nav.aap.statistikk.behandling

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.avsluttetbehandling.RettighetsType
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.bigquery.BQTable
import no.nav.aap.statistikk.bigquery.hentEllerNull
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.utbetaling.helved.toBase64
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * @param utbetalingId Base64-enkodet versjon av [referanse].
 */
data class BQYtelseBehandling(
    val saksnummer: Saksnummer,
    val referanse: UUID,
    val utbetalingId: String?,
    val brukerFnr: String,
    val behandlingsType: TypeBehandling,
    val datoOpprettet: LocalDateTime,
    val datoAvsluttet: LocalDateTime,
    val kodeverk: String?,
    val diagnosekode: String?,
    val bidiagnoser: List<String>?,
    val rettighetsPerioder: List<RettighetstypePeriode>,
    val vurderingsbehov: List<String>,
    val resultat: ResultatKode? = null,
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
            val behandlingsreferanse =
                Field.newBuilder("behandlingsreferanse", StandardSQLTypeName.STRING)
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
            val datoOpprettet = Field.newBuilder("datoOpprettet", StandardSQLTypeName.DATETIME)
                .setDescription("På hvilken dato ble behandling opprettet i Kelvin. Ikke-null.")
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

            val utbetalingId = Field.newBuilder("utbetalingId", StandardSQLTypeName.STRING)
                .setDescription("Base64-encodet verdi av 'behandlingsreferanse'. Det er denne som sendes til UR.")
                .build()

            val resultat = Field.newBuilder("resultat", StandardSQLTypeName.STRING)
                .setDescription("Resultat av behandlingen. Ved innvilget førstegangsbehandling er denne INNVILGET. Kan være null.")
                .setMode(Field.Mode.NULLABLE).build()

            val vurderingsbehov = Field.newBuilder("vurderingsbehov", StandardSQLTypeName.STRING)
                .setDescription("Behandlingsbehov som er vurdert i denne behandlingen.")
                .setMode(Field.Mode.REPEATED)
                .build()

            return Schema.of(
                saksnummer,
                behandlingsreferanse,
                behandlingsType,
                brukerFnr,
                datoOpprettet,
                datoAvsluttet,
                kodeverk,
                diagnosekode,
                bidiagnoser,
                rettighetstypePeriode,
                radEndret,
                utbetalingId,
                resultat,
                vurderingsbehov
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BQYtelseBehandling {
        val saksnummer = fieldValueList.get("saksnummer").stringValue.let(::Saksnummer)
        val referanse = fieldValueList.get("behandlingsreferanse").stringValue
        val brukerFnr = fieldValueList.get("brukerFnr").stringValue
        val behandlingsType = fieldValueList.get("behandlingsType").stringValue
        val datoOpprettet = LocalDateTime.parse(fieldValueList.get("datoOpprettet").stringValue)
        val datoAvsluttet = LocalDateTime.parse(fieldValueList.get("datoAvsluttet").stringValue)
        val kodeverk = fieldValueList.get("kodeverk").stringValue
        val diagnosekode = fieldValueList.get("diagnosekode").stringValue
        val bidiagnoser =
            fieldValueList.get("bidiagnoser").repeatedValue.map { it.recordValue[0].stringValue }
        val vurderingsbehov =
            fieldValueList.get("vurderingsbehov").repeatedValue.map { it.value.toString() }
        val radEndret = LocalDateTime.parse(fieldValueList.get("radEndret").stringValue)
        val utbetalingId = fieldValueList.get("utbetalingId").stringValue

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
            brukerFnr = brukerFnr,
            behandlingsType = behandlingsType.let { TypeBehandling.valueOf(it) },
            datoOpprettet = datoOpprettet,
            datoAvsluttet = datoAvsluttet,
            kodeverk = kodeverk,
            diagnosekode = diagnosekode,
            bidiagnoser = bidiagnoser,
            rettighetsPerioder = rettighetstypePerioder,
            radEndret = radEndret,
            utbetalingId = utbetalingId,
            resultat = fieldValueList.hentEllerNull("resultat")?.let { ResultatKode.valueOf(it) },
            vurderingsbehov = vurderingsbehov
        )
    }

    override fun toRow(value: BQYtelseBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "saksnummer" to value.saksnummer.value,
                "behandlingsreferanse" to value.referanse.toString(),
                "brukerFnr" to value.brukerFnr,
                "behandlingsType" to value.behandlingsType.toString(),
                "datoOpprettet" to value.datoOpprettet.truncatedTo(ChronoUnit.MILLIS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
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
                "resultat" to value.resultat?.toString(),
                "radEndret" to value.radEndret.truncatedTo(ChronoUnit.MILLIS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "utbetalingId" to value.referanse.toBase64(),
                "vurderingsbehov" to value.vurderingsbehov.joinToString(",").let(::listOf)
            )
        )
    }
}
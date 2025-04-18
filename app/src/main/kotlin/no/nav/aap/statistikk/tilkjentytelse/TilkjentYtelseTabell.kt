package no.nav.aap.statistikk.tilkjentytelse

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.aap.statistikk.bigquery.BQTable
import no.nav.aap.statistikk.sak.Saksnummer
import java.time.LocalDate

data class BQTilkjentYtelse(
    val saksnummer: Saksnummer,
    val behandlingsreferanse: String,
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val dagsats: Double,
    val gradering: Double,
    val antallBarn: Int,
    val barnetillegg: Double,
    val barnetilleggSats: Double,
    val redusertDagsats: Double,
)

class TilkjentYtelseTabell : BQTable<BQTilkjentYtelse> {
    companion object {
        const val TABLE_NAME = "tilkjentYtelse"
    }

    private enum class FeltNavn(val feltNavn: String) {
        SAKSNUMMER("saksnummer"),
        BEHANDLINGSREFERANSE("behandlingsreferanse"),
        FRA_DATO(
            "fraDato"
        ),
        TIL_DATO("tilDato"), DAGSATS("dagsats"), GRADERING("gradering"),
        ANTALL_BARN("antallBarn"),
        BARNETILLEGG("barnetillegg"), BARNETILLEGG_SATS("barnetilleggSats"),
        REDUSERT_DAGSATS("redusertDagsats"),
    }

    override val tableName: String = TABLE_NAME
    override val version: Int = 1

    override val schema: Schema = Schema.of(
        Field.of(FeltNavn.SAKSNUMMER.feltNavn, StandardSQLTypeName.STRING),
        Field.of(FeltNavn.BEHANDLINGSREFERANSE.feltNavn, StandardSQLTypeName.STRING),
        Field.of(FeltNavn.FRA_DATO.feltNavn, StandardSQLTypeName.DATE),
        Field.of(FeltNavn.TIL_DATO.feltNavn, StandardSQLTypeName.DATE),
        Field.of(FeltNavn.DAGSATS.feltNavn, StandardSQLTypeName.FLOAT64),
        Field.of(FeltNavn.GRADERING.feltNavn, StandardSQLTypeName.FLOAT64),
        Field.of(FeltNavn.ANTALL_BARN.feltNavn, StandardSQLTypeName.INT64),
        Field.of(FeltNavn.BARNETILLEGG.feltNavn, StandardSQLTypeName.FLOAT64),
        Field.of(FeltNavn.BARNETILLEGG_SATS.feltNavn, StandardSQLTypeName.FLOAT64),
        Field.of(FeltNavn.REDUSERT_DAGSATS.feltNavn, StandardSQLTypeName.FLOAT64),
    )


    override fun parseRow(fieldValueList: FieldValueList): BQTilkjentYtelse {
        val saksnummer =
            fieldValueList.get(FeltNavn.SAKSNUMMER.feltNavn).stringValue.let(::Saksnummer)
        val behandlingsReferanse =
            fieldValueList.get(FeltNavn.BEHANDLINGSREFERANSE.feltNavn).stringValue
        val fraDato = LocalDate.parse(fieldValueList.get(FeltNavn.FRA_DATO.feltNavn).stringValue)
        val tilDato = LocalDate.parse(fieldValueList.get(FeltNavn.TIL_DATO.feltNavn).stringValue)
        val dagsats = fieldValueList.get(FeltNavn.DAGSATS.feltNavn).doubleValue
        val gradering = fieldValueList.get(FeltNavn.GRADERING.feltNavn).doubleValue
        val antallBarn = fieldValueList.get(FeltNavn.ANTALL_BARN.feltNavn).longValue
        val barnetillegg = fieldValueList.get(FeltNavn.BARNETILLEGG.feltNavn).doubleValue
        val barnetilleggSats = fieldValueList.get(FeltNavn.BARNETILLEGG_SATS.feltNavn).doubleValue
        val redusertDagsats = fieldValueList.get(FeltNavn.REDUSERT_DAGSATS.feltNavn).doubleValue

        return BQTilkjentYtelse(
            saksnummer = saksnummer,
            behandlingsreferanse = behandlingsReferanse,
            fraDato = fraDato,
            tilDato = tilDato,
            dagsats = dagsats,
            gradering = gradering,
            antallBarn = antallBarn.toInt(),
            barnetillegg = barnetillegg,
            barnetilleggSats = barnetilleggSats,
            redusertDagsats = redusertDagsats,
        )
    }

    override fun toRow(value: BQTilkjentYtelse): RowToInsert {
        return RowToInsert.of(
            mapOf(
                FeltNavn.SAKSNUMMER.feltNavn to value.saksnummer.value,
                FeltNavn.BEHANDLINGSREFERANSE.feltNavn to value.behandlingsreferanse,
                FeltNavn.FRA_DATO.feltNavn to value.fraDato.toString(),
                FeltNavn.TIL_DATO.feltNavn to value.tilDato.toString(),
                FeltNavn.DAGSATS.feltNavn to value.dagsats,
                FeltNavn.GRADERING.feltNavn to value.gradering,
                FeltNavn.ANTALL_BARN.feltNavn to value.antallBarn,
                FeltNavn.BARNETILLEGG.feltNavn to value.barnetillegg,
                FeltNavn.BARNETILLEGG_SATS.feltNavn to value.barnetilleggSats,
                FeltNavn.REDUSERT_DAGSATS.feltNavn to value.redusertDagsats,
            )
        )
    }
}
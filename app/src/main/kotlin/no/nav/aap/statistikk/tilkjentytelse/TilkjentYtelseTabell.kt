package no.nav.aap.statistikk.tilkjentytelse

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.LocalDate
import java.util.*

class TilkjentYtelseTabell : BQTable<TilkjentYtelse> {
    private enum class FeltNavn(val feltNavn: String) {
        SAKSNUMMER("saksnummer"), BEHANDLINGSREFERANSE("behandlingsreferanse"), PERIODER("perioder"), FRA_DATO(
            "fraDato"
        ),
        TIL_DATO("tilDato"), DAGSATS("dagsats"), GRADERING("gradering")
    }

    override val tableName: String
        get() = "tilkjentYtelse"

    override val schema: Schema
        get() {
            val saksnummerField = Field.of(FeltNavn.SAKSNUMMER.feltNavn, StandardSQLTypeName.STRING)
            val behandlingsReferanse =
                Field.of(FeltNavn.BEHANDLINGSREFERANSE.feltNavn, StandardSQLTypeName.STRING)
            val perioderField = Field.newBuilder(
                FeltNavn.PERIODER.feltNavn,
                StandardSQLTypeName.STRUCT,
                Field.of(FeltNavn.FRA_DATO.feltNavn, StandardSQLTypeName.DATE),
                Field.of(FeltNavn.TIL_DATO.feltNavn, StandardSQLTypeName.DATE),
                Field.of(FeltNavn.DAGSATS.feltNavn, StandardSQLTypeName.FLOAT64),
                Field.of(FeltNavn.GRADERING.feltNavn, StandardSQLTypeName.FLOAT64)
            ).setMode(Field.Mode.REPEATED).build()
            return Schema.of(saksnummerField, behandlingsReferanse, perioderField)
        }

    override fun parseRow(fieldValueList: FieldValueList): TilkjentYtelse {
        val saksnummer = fieldValueList.get(FeltNavn.SAKSNUMMER.feltNavn).stringValue
        val behandlingsReferanse =
            fieldValueList.get(FeltNavn.BEHANDLINGSREFERANSE.feltNavn).stringValue

        val tilkjentYtelsePerioder =
            fieldValueList.get(FeltNavn.PERIODER.feltNavn).repeatedValue.map {
                TilkjentYtelsePeriode(
                    fraDato = LocalDate.parse(it.recordValue[0].stringValue),
                    tilDato = LocalDate.parse(it.recordValue[1].stringValue),
                    dagsats = it.recordValue[2].doubleValue,
                    gradering = it.recordValue[3].doubleValue,
                )
            }

        return TilkjentYtelse(
            saksnummer = saksnummer,
            behandlingsReferanse = UUID.fromString(behandlingsReferanse),
            perioder = tilkjentYtelsePerioder
        )
    }

    override fun toRow(value: TilkjentYtelse): RowToInsert {
        return RowToInsert.of(
            mapOf(FeltNavn.SAKSNUMMER.feltNavn to value.saksnummer,
                FeltNavn.BEHANDLINGSREFERANSE.feltNavn to value.behandlingsReferanse.toString(),
                FeltNavn.PERIODER.feltNavn to value.perioder.map {
                    mapOf(
                        FeltNavn.FRA_DATO.feltNavn to it.fraDato.toString(),
                        FeltNavn.TIL_DATO.feltNavn to it.tilDato.toString(),
                        FeltNavn.GRADERING.feltNavn to it.gradering,
                        FeltNavn.DAGSATS.feltNavn to it.dagsats,
                    )
                })
        )
    }
}
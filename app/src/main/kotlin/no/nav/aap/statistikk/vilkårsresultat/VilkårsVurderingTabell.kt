package no.nav.aap.statistikk.vilkårsresultat

import com.google.cloud.bigquery.*
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.LocalDate
import java.util.*

data class BQVilkårsResultatPeriode(
    val saksnummer: String,
    val behandlingsReferanse: UUID,
    val behandlingsType: String,
    val vilkårtype: Vilkårtype,
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val utfall: String,
    val manuellVurdering: Boolean,
)

class VilkårsVurderingTabell : BQTable<BQVilkårsResultatPeriode> {
    companion object {
        const val TABLE_NAME = "vilkarsResultat"
    }

    private enum class FeltNavn(val feltNavn: String) {
        SAKSNUMMER("saksnummer"),
        BEHANDLINGSREFERANSE("behandlingsreferanse"),
        BEHANDLINGSTYPE("behandlingsType"),
        VILKÅR_TYPE("type"),
        FRA_DATO("fraDato"),
        TIL_DATO("tilDato"),
        UTFALL("utfall"),
        MANUELL_VURDERING("manuell_vurdering"),
    }

    override val tableName: String = TABLE_NAME
    override val version: Int = 1
    override val schema: Schema
        get() {
            val saksnummerField = Field.of(FeltNavn.SAKSNUMMER.feltNavn, StandardSQLTypeName.STRING)
            val behandlingsReferanse =
                Field.of(FeltNavn.BEHANDLINGSREFERANSE.feltNavn, StandardSQLTypeName.STRING)
            val behandlingsType =
                Field.of(FeltNavn.BEHANDLINGSTYPE.feltNavn, StandardSQLTypeName.STRING)
            val vilkårType = Field.of(FeltNavn.VILKÅR_TYPE.feltNavn, StandardSQLTypeName.STRING)
            val fraDato = Field.of(FeltNavn.FRA_DATO.feltNavn, StandardSQLTypeName.DATE)
            val tilDato = Field.of(FeltNavn.TIL_DATO.feltNavn, StandardSQLTypeName.DATE)
            val utfall = Field.of(FeltNavn.UTFALL.feltNavn, StandardSQLTypeName.STRING)
            val manuellVurdering =
                Field.of(FeltNavn.MANUELL_VURDERING.feltNavn, StandardSQLTypeName.BOOL)
            return Schema.of(
                saksnummerField,
                behandlingsReferanse,
                behandlingsType,
                vilkårType,
                fraDato,
                tilDato,
                utfall,
                manuellVurdering
            )
        }


    override fun parseRow(fieldValueList: FieldValueList): BQVilkårsResultatPeriode {
        val saksnummer = hentVerdi(fieldValueList, FeltNavn.SAKSNUMMER)
        val behandlingsType = hentVerdi(fieldValueList, FeltNavn.BEHANDLINGSTYPE)
        val behandlingsReferanse = hentVerdi(fieldValueList, FeltNavn.BEHANDLINGSREFERANSE)
        val vilkårType = hentVerdi(fieldValueList, FeltNavn.VILKÅR_TYPE)
        val fraDato = hentVerdi(fieldValueList, FeltNavn.FRA_DATO)
        val tilDato = hentVerdi(fieldValueList, FeltNavn.TIL_DATO)
        val utfall = hentVerdi(fieldValueList, FeltNavn.UTFALL)
        val manuellVurdering = fieldValueList.get(FeltNavn.MANUELL_VURDERING.feltNavn).booleanValue

        return BQVilkårsResultatPeriode(
            saksnummer = saksnummer,
            behandlingsType = behandlingsType,
            behandlingsReferanse = UUID.fromString(behandlingsReferanse),
            vilkårtype = Vilkårtype.valueOf(vilkårType),
            fraDato = LocalDate.parse(fraDato),
            tilDato = LocalDate.parse(tilDato),
            utfall = utfall,
            manuellVurdering = manuellVurdering
        )
    }

    private fun hentVerdi(fieldValueList: FieldValueList, felt: FeltNavn): String =
        fieldValueList.get(felt.feltNavn).stringValue

    override fun toRow(value: BQVilkårsResultatPeriode): RowToInsert {
        // TODO: bruke ID?
        return RowToInsert.of(
            mapOf(
                FeltNavn.SAKSNUMMER.feltNavn to value.saksnummer,
                FeltNavn.BEHANDLINGSTYPE.feltNavn to value.behandlingsType,
                FeltNavn.BEHANDLINGSREFERANSE.feltNavn to value.behandlingsReferanse.toString(),
                FeltNavn.VILKÅR_TYPE.feltNavn to value.vilkårtype.toString(),
                FeltNavn.FRA_DATO.feltNavn to value.fraDato.toString(),
                FeltNavn.TIL_DATO.feltNavn to value.tilDato.toString(),
                FeltNavn.UTFALL.feltNavn to value.utfall,
                FeltNavn.MANUELL_VURDERING.feltNavn to value.manuellVurdering
            )
        )
    }
}
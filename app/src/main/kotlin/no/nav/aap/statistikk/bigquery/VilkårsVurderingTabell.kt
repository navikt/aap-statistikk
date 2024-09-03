package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.*
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import no.nav.aap.statistikk.api_kontrakt.Vilkårtype
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.time.LocalDate
import java.util.*

class VilkårsVurderingTabell : BQTable<Vilkårsresultat> {
    private enum class FeltNavn(val feltNavn: String) {
        SAKSNUMMER("saksnummer"),
        BEHANDLINGSREFERANSE("behandlingsreferanse"),
        BEHANDLINGSTYPE("behandlingsType"),
        VILKÅR("vilkar"),
        VILKÅR_TYPE("type"),
        PERIODER("perioder"),
        FRA_DATO("fraDato"),
        TIL_DATO("tilDato"),
        UTFALL("utfall"),
        MANUELL_VURDERING("manuell_vurdering"),
    }

    override val tableName: String = "vilkarsResultat"
    override val schema: Schema
        get() {
            val saksnummerField = Field.of(FeltNavn.SAKSNUMMER.feltNavn, StandardSQLTypeName.STRING)
            val behandlingsReferanse = Field.of(FeltNavn.BEHANDLINGSREFERANSE.feltNavn, StandardSQLTypeName.STRING)
            val behandlingsType = Field.of(FeltNavn.BEHANDLINGSTYPE.feltNavn, StandardSQLTypeName.STRING)
            val vilkårField =
                Field.newBuilder(
                    FeltNavn.VILKÅR.feltNavn,
                    StandardSQLTypeName.STRUCT,
                    Field.of(FeltNavn.VILKÅR_TYPE.feltNavn, StandardSQLTypeName.STRING),
                    Field.newBuilder(
                        FeltNavn.PERIODER.feltNavn,
                        StandardSQLTypeName.STRUCT,
                        Field.of(FeltNavn.FRA_DATO.feltNavn, StandardSQLTypeName.DATE),
                        Field.of(FeltNavn.TIL_DATO.feltNavn, StandardSQLTypeName.DATE),
                        Field.of(FeltNavn.UTFALL.feltNavn, StandardSQLTypeName.STRING),
                        Field.of(FeltNavn.MANUELL_VURDERING.feltNavn, StandardSQLTypeName.BOOL),
                    ).setMode(Field.Mode.REPEATED).build()
                )
                    .setMode(Field.Mode.REPEATED)
                    .build()
            return Schema.of(saksnummerField, behandlingsReferanse, behandlingsType, vilkårField)
        }


    override fun parseRow(fieldValueList: FieldValueList): Vilkårsresultat {
        val saksnummer = hentVerdi(fieldValueList, FeltNavn.SAKSNUMMER)
        val behandlingsType = hentVerdi(fieldValueList, FeltNavn.BEHANDLINGSTYPE)
        val behandlingsReferanse = hentVerdi(fieldValueList, FeltNavn.BEHANDLINGSREFERANSE)

        // TODO https://github.com/googleapis/java-bigquery/issues/3389
        val vilkår =
            fieldValueList.get(FeltNavn.VILKÅR.feltNavn).repeatedValue.map {
                Vilkår(
                    vilkårType = Vilkårtype.valueOf(it.recordValue[0].stringValue),
                    perioder = it.recordValue[1].repeatedValue.map(::vilkårsPeriodeFraFieldValue)
                )
            }

        return Vilkårsresultat(
            saksnummer = saksnummer,
            behandlingsType = behandlingsType,
            behandlingsReferanse = UUID.fromString(behandlingsReferanse),
            vilkår = vilkår
        )
    }

    private fun hentVerdi(fieldValueList: FieldValueList, felt: FeltNavn): String =
        fieldValueList.get(felt.feltNavn).stringValue

    private fun vilkårsPeriodeFraFieldValue(periodeRecord: FieldValue) = VilkårsPeriode(
        fraDato = LocalDate.parse(periodeRecord.recordValue[0].stringValue),
        tilDato = LocalDate.parse(periodeRecord.recordValue[1].stringValue),
        utfall = periodeRecord.recordValue[2].stringValue,
        manuellVurdering = periodeRecord.recordValue[3].booleanValue,
    )

    override fun toRow(value: Vilkårsresultat): RowToInsert {
        // TODO: bruke ID?
        return RowToInsert.of(
            mapOf(
                FeltNavn.SAKSNUMMER.feltNavn to value.saksnummer,
                FeltNavn.BEHANDLINGSTYPE.feltNavn to value.behandlingsType,
                FeltNavn.BEHANDLINGSREFERANSE.feltNavn to value.behandlingsReferanse.toString(),
                FeltNavn.VILKÅR.feltNavn to value.vilkår.map {
                    mapOf(
                        FeltNavn.VILKÅR_TYPE.feltNavn to it.vilkårType.toString(),
                        FeltNavn.PERIODER.feltNavn to it.perioder.map { periode ->
                            mapOf(
                                FeltNavn.FRA_DATO.feltNavn to periode.fraDato.toString(),
                                FeltNavn.TIL_DATO.feltNavn to periode.tilDato.toString(),
                                FeltNavn.UTFALL.feltNavn to periode.utfall,
                                FeltNavn.MANUELL_VURDERING.feltNavn to periode.manuellVurdering
                            )
                        }
                    )
                })
        )
    }
}
package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.aap.statistikk.testutils.BigQuery
import no.nav.aap.statistikk.testutils.schemaRegistry
import no.nav.aap.statistikk.vilkårsresultat.BQVilkårsResultatPeriode
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkårtype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class BigQueryClientTest {
    @Test
    fun `tabell blir opprettet ved init av klient`(@BigQuery bigQueryConfig: BigQueryConfig) {

        val client =
            BigQueryClient(bigQueryConfig, mapOf("vilkårsvurdering" to VilkårsVurderingTabell()))

        assertThat(client.create(VilkårsVurderingTabell())).isFalse
    }

    @Disabled("BigQuery-emulator støtter ikke skjema-endringer :(")
    @Test
    fun `legge til nye felter til tabell`(@BigQuery bigQueryConfig: BigQueryConfig) {
        data class OneField(val myField: String)

        val originalTabell = object : BQTable<OneField> {
            override val tableName: String = "mytable"
            override val version: Int = 0
            override val schema: Schema
                get() {
                    val myField =
                        Field.of("myField", StandardSQLTypeName.STRING)
                    return Schema.of(myField)
                }

            override fun parseRow(fieldValueList: FieldValueList): OneField {
                return OneField(fieldValueList.get("myField").stringValue)
            }

            override fun toRow(value: OneField): RowToInsert {
                return RowToInsert.of(mapOf("myField" to value.myField))
            }
        }
        val client =
            BigQueryClient(bigQueryConfig, mapOf("mytable" to originalTabell))

        client.insert(originalTabell, OneField("somevalue"))

        val oppdatertTabell = object : BQTable<OneField> {
            override val tableName: String = "mytable"
            override val version: Int = 0
            override val schema: Schema
                get() {
                    val myField =
                        Field.of("myField", StandardSQLTypeName.STRING)
                    return Schema.of(myField)
                }

            override fun parseRow(fieldValueList: FieldValueList): OneField {
                TODO("Not yet implemented")
            }

            override fun toRow(value: OneField): RowToInsert {
                TODO("Not yet implemented")
            }
        }

        val nyClient =
            BigQueryClient(bigQueryConfig, mapOf("mytable" to oppdatertTabell))

    }

    @Test
    fun `sette inn rad og hente ut igjen`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)

        val vilkårsVurderingTabell = VilkårsVurderingTabell()

        val behandlingsReferanse = UUID.randomUUID()
        val vilkårsResult = BQVilkårsResultatPeriode(
            "123", behandlingsReferanse, "behandling",
            fraDato = LocalDate.now().minusWeeks(2),
            tilDato = LocalDate.now().minusDays(1),
            utfall = "utfall",
            manuellVurdering = true,
            vilkårtype = Vilkårtype.GRUNNLAGET
        )

        client.insert(vilkårsVurderingTabell, vilkårsResult)
        val uthentetResultat = client.read(vilkårsVurderingTabell)

        assertThat(uthentetResultat.size).isEqualTo(1)
        assertThat(uthentetResultat.first().saksnummer).isEqualTo("123")
        assertThat(uthentetResultat.first().behandlingsReferanse).isEqualTo(behandlingsReferanse)
        assertThat(uthentetResultat.first().behandlingsType).isEqualTo("behandling")
        assertThat(uthentetResultat.first().vilkårtype).isEqualTo(Vilkårtype.GRUNNLAGET)
    }
}
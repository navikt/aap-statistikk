package no.nav.aap.statistikk.beregningsgrunnlag.repository

import no.nav.aap.statistikk.api_kontrakt.UføreType
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.testutils.BigQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class BeregningsGrunnlagBigQueryTest {

    @Test
    fun `lagre beregningsgrunnlag 11-19`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)

        val tabell = BeregningsGrunnlagTabell()

        val grunnlag =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2, 2021 to 146.4)
            )

        client.insert(
            tabell,
            Beregningsgrunnlag(value = grunnlag, behandlingsReferanse = UUID.randomUUID())
        )

        val uthentetResultat = client.read(tabell)

        assertThat(uthentetResultat.size).isEqualTo(1)

        val første = uthentetResultat.first() as MedBehandlingsreferanse<*>

        første.value as IBeregningsGrunnlag.Grunnlag_11_19

        assertThat(første.value.inntekter).isEqualTo(mapOf(2020 to 123.2, 2021 to 146.4))
        assertThat(første.value.grunnlag).isEqualTo(12.2)
        assertThat(første.value.er6GBegrenset).isEqualTo(true)
        assertThat(første.value.erGjennomsnitt).isEqualTo(false)
    }

    @Test
    fun `lagre uføregrunnlag`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)
        val tabell = BeregningsGrunnlagTabell()

        val grunnlag_11_19 =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2)
            )

        val grunnlag = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 123.456,
            type = UføreType.STANDARD,
            grunnlag11_19 = grunnlag_11_19,
            uføregrad = 80,
            uføreInntekterFraForegåendeÅr = mapOf(2020 to 123.1, 2021 to 145.5),
            uføreInntektIKroner = BigDecimal(123456789.00),
            uføreYtterligereNedsattArbeidsevneÅr = 1963,
        )

        val behandlingsReferanse = UUID.randomUUID()
        client.insert(
            tabell,
            Beregningsgrunnlag(value = grunnlag, behandlingsReferanse = behandlingsReferanse)
        )

        val uthentetResultat = client.read(tabell).first() as MedBehandlingsreferanse<*>

        assertThat(uthentetResultat).isEqualTo(
            Beregningsgrunnlag(
                value = grunnlag,
                behandlingsReferanse = behandlingsReferanse
            )
        )
    }

    @Test
    fun `lagre grunnlag yrkesskade`(@BigQuery bigQueryConfig: BigQueryConfig) {
        println(bigQueryConfig)
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)
        val tabell = BeregningsGrunnlagTabell()

        val grunnlag_11_19 =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2)
            )

        val uføre = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 123.456,
            type = UføreType.STANDARD,
            grunnlag11_19 = grunnlag_11_19,
            uføregrad = 80,
            uføreInntekterFraForegåendeÅr = mapOf(2020 to 123.2, 2021 to 145.5),
            uføreInntektIKroner = BigDecimal(123456789.00),
            uføreYtterligereNedsattArbeidsevneÅr = 1963,
        )

        val grunnlag = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 1337.123,
            beregningsgrunnlag = uføre,
            terskelverdiForYrkesskade = 50,
            andelSomSkyldesYrkesskade = BigDecimal(20),
            andelYrkesskade = 40,
            benyttetAndelForYrkesskade = 20,
            andelSomIkkeSkyldesYrkesskade = BigDecimal(10),
            antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(50000),
            yrkesskadeTidspunkt = 1992,
            grunnlagForBeregningAvYrkesskadeandel = BigDecimal(1234),
            yrkesskadeinntektIG = BigDecimal(60000),
            grunnlagEtterYrkesskadeFordel = BigDecimal(6123),
        )


        val behandlingsReferanse = UUID.randomUUID()
        client.insert(
            tabell,
            Beregningsgrunnlag(value = grunnlag, behandlingsReferanse = behandlingsReferanse)
        )

        val uthentetResultat = client.read(tabell).first() as MedBehandlingsreferanse<*>

        assertThat(uthentetResultat).isEqualTo(
            MedBehandlingsreferanse(
                value = grunnlag,
                behandlingsReferanse = behandlingsReferanse
            )
        )
    }
}